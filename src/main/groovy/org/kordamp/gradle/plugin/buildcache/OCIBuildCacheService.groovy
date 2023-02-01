/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2023 Andres Almiray.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.gradle.plugin.buildcache

import com.oracle.bmc.model.BmcException
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.model.ObjectLifecycleRule
import com.oracle.bmc.objectstorage.model.PutObjectLifecyclePolicyDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest
import com.oracle.bmc.objectstorage.requests.GetObjectLifecyclePolicyRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.HeadBucketRequest
import com.oracle.bmc.objectstorage.requests.PutObjectLifecyclePolicyRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import groovy.transform.CompileStatic
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
@CompileStatic
class OCIBuildCacheService implements BuildCacheService {
    private static final Logger logger = LoggerFactory.getLogger(OCIBuildCacheService)
    private static final String BUILD_CACHE_CONTENT_TYPE = 'application/vnd.gradle.build-cache-artifact'

    private final ObjectStorageClient client
    private final OCIBuildCache buildCache
    private String namespaceName

    OCIBuildCacheService(ObjectStorageClient client, OCIBuildCache buildCache) {
        this.client = client
        this.buildCache = buildCache
    }

    @Override
    boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        resolveNamespace()
        String objectName = resolveObjectName(key)

        try {
            GetObjectResponse object = client.getObject(GetObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(buildCache.bucket)
                .objectName(objectName)
                .build())

            logger.info("Found cache item '{}' in {}:{} bucket", objectName, namespaceName, buildCache.bucket)
            InputStream is = null
            try {
                is = object.inputStream
                reader.readFrom(is)
                return true
            } catch (IOException e) {
                throw new BuildCacheException('Error while reading cache object from OCI ObjectStorage bucket', e)
            } finally {
                is?.close()
            }
        } catch (BmcException e) {
            logger.info("Did not find cache item '{}' in {}:{} bucket", objectName, namespaceName, buildCache.bucket)
        }

        return false
    }

    @Override
    void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        resolveNamespace()
        ensureBucketExists()
        String objectName = resolveObjectName(key)

        ByteArrayOutputStream os = null
        InputStream is = null
        try {
            os = new ByteArrayOutputStream()
            writer.writeTo(os)
            is = new ByteArrayInputStream(os.toByteArray())

            PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(buildCache.bucket)
                .objectName(objectName)
                .contentLength(writer.size)
                .contentType(BUILD_CACHE_CONTENT_TYPE)
                .putObjectBody(is)
            client.putObject(builder.build())
        } catch (Exception e) {
            throw new BuildCacheException("Error while storing cache object in bucket", e)
        } finally {
            os?.close()
            is?.close()
        }
    }

    @Override
    void close() throws IOException {
        client.close()
    }

    private String resolveObjectName(BuildCacheKey key) {
        key.hashCode
    }

    private void resolveNamespace() {
        if (!namespaceName) {
            try {
                namespaceName = client.getNamespace(GetNamespaceRequest.builder()
                    .compartmentId(buildCache.compartmentId)
                    .build())
                    .value
            } catch (Exception e) {
                throw new BuildCacheException('Error while resolving namespace', e)
            }
        }
    }

    private void ensureBucketExists() {
        // 1. Check if it exists
        try {
            client.headBucket(HeadBucketRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(buildCache.bucket)
                .build())

            logger.info("Bucket '${buildCache.bucket}' was found.")
            ensureLifecyclePolicyExists()
            return
        } catch (BmcException e) {
            // exception most likely means the bucket does not exist, continue
        }

        // 2. Create bucket
        logger.info('Provisioning Bucket. This may take a while.')
        try {
            client.createBucket(CreateBucketRequest.builder()
                .namespaceName(namespaceName)
                .createBucketDetails(CreateBucketDetails.builder()
                    .compartmentId(buildCache.compartmentId)
                    .name(buildCache.bucket)
                    .build())
                .build())
        } catch (BmcException e) {
            throw new BuildCacheException("Error while provisioning ${namespaceName}:${buildCache.bucket} bucket", e)
        }

        // 3. Create lifecycle policy
        createObjectLifecyclePolicy([createObjectLifecycleRule()])
    }

    private void ensureLifecyclePolicyExists() {
        List<ObjectLifecycleRule> rules = retrieveObjectLifecycleRules()
        if (rules.empty) {
            createObjectLifecyclePolicy([createObjectLifecycleRule()])
        }
    }

    private List<ObjectLifecycleRule> retrieveObjectLifecycleRules() {
        try {
            return client.getObjectLifecyclePolicy(GetObjectLifecyclePolicyRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(buildCache.bucket)
                .build())
                .objectLifecyclePolicy
                .items ?: []
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                return []
            }
            throw new BuildCacheException("Error while retrieving object lifecycle policy on ${namespaceName}:${buildCache.bucket} bucket", e)
        }
    }

    private ObjectLifecycleRule createObjectLifecycleRule() {
        ObjectLifecycleRule.builder()
            .name('delete-objects-after-' + buildCache.policy.amount + '-' + buildCache.policy.unit.toLowerCase() + '-rule')
            .action('DELETE')
            .timeAmount(buildCache.policy.amount)
            .timeUnit(ObjectLifecycleRule.TimeUnit.valueOf(buildCache.policy.unit.toLowerCase().capitalize()))
            .isEnabled(true)
            .build()
    }

    private void createObjectLifecyclePolicy(List<ObjectLifecycleRule> rules) {
        try {
            client.putObjectLifecyclePolicy(PutObjectLifecyclePolicyRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(buildCache.bucket)
                .putObjectLifecyclePolicyDetails(PutObjectLifecyclePolicyDetails.builder()
                    .items(rules)
                    .build())
                .build())
        } catch (Exception e) {
            throw new BuildCacheException("Error while setting object lifecycle policy on ${namespaceName}:${buildCache.bucket} bucket", e)
        }
    }
}

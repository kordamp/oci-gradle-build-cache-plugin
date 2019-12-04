/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019 Andres Almiray.
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

import com.google.common.base.Supplier
import com.oracle.bmc.ConfigFileReader
import com.oracle.bmc.Region
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.ObjectLifecycleRule
import groovy.transform.CompileStatic
import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.kordamp.gradle.plugin.buildcache.OCIBuildCachePlugin.isBlank

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
@CompileStatic
class OCIBuildCacheServiceFactory implements BuildCacheServiceFactory<OCIBuildCache> {
    private static final String CONFIG_LOCATION = '~/.oci/config'
    private static final Logger logger = LoggerFactory.getLogger(OCIBuildCacheServiceFactory)

    @Override
    BuildCacheService createBuildCacheService(OCIBuildCache buildCache, Describer describer) {
        logger.debug('Creating OCI build cache service')

        if (isBlank(buildCache.bucket)) {
            buildCache.bucket = 'build-cache'
        }

        describer
            .type('OCI ObjectStorage')
            .config('Compartment', buildCache.compartmentId)
            .config('Bucket', buildCache.bucket)

        AuthenticationDetailsProvider authenticationProvider = validateConfig(buildCache)
        System.setProperty('sun.net.http.allowRestrictedHeaders', 'true')
        ObjectStorageClient client = new ObjectStorageClient(authenticationProvider)

        return new OCIBuildCacheService(client, buildCache)
    }

    private AuthenticationDetailsProvider validateConfig(OCIBuildCache buildCache) {
        List<String> errors = []
        if (isBlank(buildCache.compartmentId)) {
            errors << "Missing value for 'buildCache.compartmentId'"
        }

        if (buildCache.policy.amount < 1L) {
            errors << "Invalid value for 'buildCache.policy.amount'. Value must be greater than 1"
        }
        if (isBlank(buildCache.policy.unit)) {
            errors << "Missing value for 'buildCache.policy.unit'"
        }
        try {
            ObjectLifecycleRule.TimeUnit.valueOf(buildCache.policy.unit.toLowerCase().capitalize())
        } catch (Exception e) {
            e.printStackTrace()
            errors << "Invalid value for 'buildCache.policy.unit'. Value must be 'Days' or 'Zears'"
        }

        if (errors.size() > 0) {
            throw new IllegalStateException(errors.join('\n'))
        }

        if (buildCache.config.empty) {
            ConfigFileReader.ConfigFile configFile = ConfigFileReader.parse(CONFIG_LOCATION, buildCache.profile ?: 'DEFAULT')
            ConfigFileAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile)
            buildCache.config.region = provider.region.regionId
            return provider
        }

        if (isBlank(buildCache.config.userId)) {
            errors << "Missing value for 'buildCache.config.userId'"
        }
        if (isBlank(buildCache.config.tenantId)) {
            errors << "Missing value for 'buildCache.config.tenantId'"
        }
        if (isBlank(buildCache.config.fingerprint)) {
            errors << "Missing value for 'buildCache.config.fingerprint'"
        }
        if (isBlank(buildCache.config.region)) {
            errors << "Missing value for 'buildCache.config.region'"
        }
        if (null == buildCache.config.keyfile) {
            errors << "Missing value for 'buildCache.config.keyfile'"
        }

        if (errors.size() > 0) {
            throw new IllegalStateException(errors.join('\n'))
        }

        AuthenticationDetailsProvider authenticationDetailsProvider = SimpleAuthenticationDetailsProvider.builder()
            .userId(buildCache.config.userId)
            .tenantId(buildCache.config.tenantId)
            .fingerprint(buildCache.config.fingerprint)
            .region(Region.fromRegionId(buildCache.config.region))
            .privateKeySupplier(new Supplier<InputStream>() {
                @Override
                InputStream get() {
                    new FileInputStream(buildCache.config.keyfile)
                }
            })
            .passPhrase(buildCache.config.passphrase ?: '')
            .build()

        authenticationDetailsProvider
    }
}

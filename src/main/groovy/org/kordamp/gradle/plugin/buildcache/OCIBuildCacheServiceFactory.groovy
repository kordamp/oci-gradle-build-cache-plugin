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

import com.oracle.bmc.ConfigFileReader
import com.oracle.bmc.Region
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import com.oracle.bmc.http.client.jersey.JerseyHttpProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.ObjectLifecycleRule
import com.oracle.bmc.util.internal.FileUtils
import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Supplier

import static org.kordamp.gradle.plugin.buildcache.OCIBuildCachePlugin.isBlank

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
class OCIBuildCacheServiceFactory implements BuildCacheServiceFactory<OCIBuildCache> {
    private static final String CONFIG_LOCATION = '~/.oci/config'
    private static final Logger logger = LoggerFactory.getLogger(OCIBuildCacheServiceFactory)

    @Override
    BuildCacheService createBuildCacheService(OCIBuildCache buildCache, Describer describer) {
        logger.info('Creating OCI build cache service')

        if (isBlank(buildCache.compartmentId)) {
            logger.warn("Missing value for 'buildCache.compartmentId'")
            logger.warn('OCI build cache service is not enabled')
            return new NoopBuildCacheService()
        }

        if (isBlank(buildCache.bucket)) {
            buildCache.bucket = 'build-cache'
        }

        describer
            .type('OCI ObjectStorage')
            .config('Compartment', buildCache.compartmentId)
            .config('Bucket', buildCache.bucket)

        Optional<AuthenticationDetailsProvider> authenticationProvider = validateConfig(buildCache)

        if (authenticationProvider.present) {
            System.setProperty('sun.net.http.allowRestrictedHeaders', 'true')
            ObjectStorageClient client = ObjectStorageClient.builder()
                .httpProvider(new JerseyHttpProvider())
                .build(authenticationProvider.get())

            return new OCIBuildCacheService(client, buildCache)
        }

        logger.warn('OCI build cache service is not enabled')
        return new NoopBuildCacheService()
    }

    private Optional<AuthenticationDetailsProvider> validateConfig(OCIBuildCache buildCache) {
        List<String> errors = []

        if (buildCache.policy.amount < 1L) {
            errors << "Invalid value for 'buildCache.policy.amount'. Value must be greater than 1"
        }
        if (isBlank(buildCache.policy.unit)) {
            errors << "Missing value for 'buildCache.policy.unit'"
        } else {
            try {
                ObjectLifecycleRule.TimeUnit.valueOf(buildCache.policy.unit.toLowerCase().capitalize())
            } catch (Exception e) {
                errors << "Invalid value for 'buildCache.policy.unit'. Value must be 'Days' or 'Years'"
            }
        }

        if (errors.size() > 0 && buildCache.failOnError) {
            throw new IllegalStateException(errors.join('\n'))
        }

        if (buildCache.config.empty) {
            String configFileLocation = FileUtils.expandUserHome(buildCache.configFile ?: CONFIG_LOCATION)
            if (!Files.exists(Paths.get(configFileLocation))) {
                // no config available. disable cache
                return Optional.empty()
            }

            ConfigFileReader.ConfigFile configFile = ConfigFileReader.parse(configFileLocation, buildCache.profile ?: 'DEFAULT')
            ConfigFileAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile)
            buildCache.config.region = provider.region.regionId
            return Optional.of(provider)
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

        if (errors.size() > 0 && buildCache.failOnError) {
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

        Optional.of(authenticationDetailsProvider)
    }
}

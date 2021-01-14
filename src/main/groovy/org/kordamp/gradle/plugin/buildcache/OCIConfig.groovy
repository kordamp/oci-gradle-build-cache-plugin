/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2021 Andres Almiray.
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

import groovy.transform.CompileStatic

import static org.kordamp.gradle.plugin.buildcache.OCIBuildCachePlugin.isBlank

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
@CompileStatic
class OCIConfig {
    String userId
    String tenantId
    String fingerprint
    String region
    File keyfile
    String passphrase

    boolean isEmpty() {
        isBlank(userId) &&
            isBlank(tenantId) &&
            isBlank(fingerprint) &&
            isBlank(region) &&
            keyfile == null
    }
}

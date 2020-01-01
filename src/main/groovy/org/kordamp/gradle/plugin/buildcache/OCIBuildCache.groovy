/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2020 Andres Almiray.
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
import org.gradle.api.Action
import org.gradle.caching.configuration.AbstractBuildCache

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
@CompileStatic
class OCIBuildCache extends AbstractBuildCache {
    String configFile = '~/.oci/config'
    String profile
    String compartmentId
    String bucket
    OCIConfig config = new OCIConfig()
    OCIPolicy policy = new OCIPolicy()

    void config(Action<? extends OCIConfig> action) {
        action.execute(config)
    }

    void policy(Action<? extends OCIPolicy> action) {
        action.execute(policy)
    }
}

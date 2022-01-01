/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2022 Andres Almiray.
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
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
@CompileStatic
class OCIBuildCachePlugin implements Plugin<Settings> {
    void apply(Settings settings) {
        Banner.display(settings)

        settings.buildCache
            .registerBuildCacheService(OCIBuildCache, OCIBuildCacheServiceFactory)
    }

    static boolean isBlank(String str) {
        if (str == null || str.length() == 0) {
            return true
        }
        for (char c : str.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                return false
            }
        }

        return true
    }
}

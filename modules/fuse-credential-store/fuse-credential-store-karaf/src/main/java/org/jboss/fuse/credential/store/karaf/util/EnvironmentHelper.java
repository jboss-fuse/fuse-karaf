/**
 *  Copyright 2005-2018 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.jboss.fuse.credential.store.karaf.util;

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.getenv;

/**
 * Utility class with methods to access environment variables.
 */
final class EnvironmentHelper {

    private EnvironmentHelper() {
        // utility class
    }

    /**
     * Returns a map with all the environment variable values with the specified prefix. The values of the returned map
     * are with the prefix removed and the case preserved, the values are equal to the corresponding values in the
     * environment.
     *
     * @param prefix
     *            the prefix to filter by
     * @return map of environment variables matching the requested prefix
     */
    static Map<String, String> attributesFromEnvironment(final String prefix) {
        final Map<String, String> attributes = new HashMap<>();

        final int attributeKeyStart = prefix.length();
        getenv().forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                attributes.put(k.substring(attributeKeyStart), v);
            }
        });

        return attributes;
    }
}

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
import java.util.List;
import java.util.Map;

import org.apache.karaf.shell.support.CommandException;

/**
 * Utility class for parsing options given on the Karaf shell command line.
 */
public final class OptionsHelper {

    private OptionsHelper() {
        // utility class
    }

    /**
     * Parses all options given as a list of option-value pairs, in {@code option=value} syntax to a {@link Map}.
     *
     * @param options
     *            list of option-value pairs
     * @return map of parsed option-values
     * @throws CommandException
     *             if the specified option is not in the proper syntax
     */
    public static Map<String, String> attributesFromOptions(final List<String> options) throws CommandException {
        final Map<String, String> ret = new HashMap<>();

        for (final String option : options) {
            final int split = option.indexOf('=');

            if ((split < 1) || (split == (option.length() - 1)) || (option.indexOf('=', split + 1) > 0)) {
                throw new CommandException("Specified parameter `" + option + "`, is not using the syntax key=value");
            }

            final String key = option.substring(0, split).trim();

            final String value = option.substring(split + 1).trim();

            if (key.isEmpty() || value.isEmpty()) {
                throw new CommandException("Specified parameter `" + option + "`, is not using the syntax key=value");
            }

            ret.put(key, value);
        }

        return ret;
    }
}

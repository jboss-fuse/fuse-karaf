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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.apache.karaf.shell.support.CommandException;
import org.assertj.core.util.VisibleForTesting;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@RunWith(Enclosed.class)
public class OptionsHelperTest {

    @RunWith(Parameterized.class)
    public static class InvalidParameterTests {

        @Parameter
        @VisibleForTesting
        public String parameter;

        @Parameters(name = "{0}")
        public static Iterable<?> invalidParameters() {
            return Arrays.asList("", "a=", "=a", "a= ", " =", "= ", " = ", "==", "= = =");
        }

        @Test(expected = CommandException.class)
        public void shouldConvertNoParametersToEmptyAttributeMap() throws CommandException {
            OptionsHelper.attributesFromOptions(Collections.singletonList(parameter));
        }

    }

    public static class ParameterTests {

        @Test
        public void shouldConvertSimpleParametersToAttributeMap() throws CommandException {
            final Map<String, String> attributes = OptionsHelper
                    .attributesFromOptions(Arrays.asList("a=b", "c =d", "e= f", " g = h"));

            assertThat(attributes).containsOnly(entry("a", "b"), entry("c", "d"), entry("e", "f"), entry("g", "h"));
        }

    }

}

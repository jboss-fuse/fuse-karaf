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
package org.jboss.fuse.credential.store.karaf.command;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.parsing.CommandLineImpl;
import org.jboss.fuse.credential.store.karaf.util.ProtectionType;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CredentialStoreProtectionCompletionSupportTest {

    private static final Session NOT_NEEDED = null;

    CredentialStoreProtectionCompletionSupport completition = new CredentialStoreProtectionCompletionSupport();

    @Test
    public void shouldAutocompleteForKnownProtectionTypes() {
        final CommandLineImpl commandLine = new CommandLineImpl(
                new String[] {"credential-store:create", "-p", "masked", "-k"}, 4, 0, 37,
                "credential-store:create -p masked -k ");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(37);

        assertThat(candidates).containsOnly(
                Arrays.stream(ProtectionType.masked.getSupportedOptions()).map(o -> o + "=").toArray(String[]::new));
    }

    @Test
    public void shouldAutocompletePartialValuesForOptions() {
        final CommandLineImpl commandLine = new CommandLineImpl(
                new String[] {"credential-store:create", "-p", "masked", "-k", "provider=S"}, 4, 10, 47,
                "credential-store:create -p masked -k provider=S");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(37);

        assertThat(candidates).containsOnly(Arrays.stream(Security.getProviders()).map(Provider::getName)
                .filter(p -> p.startsWith("S")).map(p -> "provider=" + p + " ").toArray(String[]::new));
    }

    @Test
    public void shouldAutocompletePartialyForKnownProtectionTypes() {
        final CommandLineImpl commandLine = new CommandLineImpl(
                new String[] {"credential-store:create", "-p", "masked", "-k", "pro"}, 4, 3, 40,
                "credential-store:create -p masked -k pro");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(37);

        assertThat(candidates).containsOnly("provider=");
    }

    @Test
    public void shouldAutocompleteValuesForOptions() {
        final CommandLineImpl commandLine = new CommandLineImpl(
                new String[] {"credential-store:create", "-p", "masked", "-k", "provider="}, 4, 9, 46,
                "credential-store:create -p masked -k provider=");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(37);

        assertThat(candidates).containsOnly(
                Arrays.stream(Security.getProviders()).map(p -> "provider=" + p.getName() + " ").toArray(String[]::new));
    }

    @Test
    public void shouldGatherUnusedOptions() {
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions()).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k")).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a")).containsOnly("a");
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a", "-k")).containsOnly("a");
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a", "-k")).containsOnly("a");
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a", "-k", "b")).containsOnly("a", "b");
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a=x")).containsOnly("a");
        assertThat(CredentialStoreProtectionCompletionSupport.usedOptions("-k", "a=x", "-k", "b=y")).containsOnly("a",
                "b");
    }

    @Test
    public void shouldNotAutocompleteIfTheProtectionTypeArgumentIsUnknown() {
        final CommandLineImpl commandLine = new CommandLineImpl(new String[] {"-p", "rubber_ducky"}, 0, 0, 0,
                "-p rubber_ducky");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(-1);

        assertThat(candidates).isEmpty();
    }

    @Test
    public void shouldNotAutocompleteIfThereIsNoProtectionTypeArgument() {
        final CommandLineImpl commandLine = new CommandLineImpl(new String[] {""}, 0, 0, 0, "");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(-1);

        assertThat(candidates).isEmpty();
    }

    @Test
    public void shouldNotAutocompleteIfThereIsNoProtectionTypeArgumentValue() {
        final CommandLineImpl commandLine = new CommandLineImpl(new String[] {"-p"}, 0, 0, 0, "-p");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(-1);

        assertThat(candidates).isEmpty();
    }

    @Test
    public void shouldOfferAutocompletionsOnlyForNonSpecifiedOptions() {
        final CommandLineImpl commandLine = new CommandLineImpl(
                new String[] {"credential-store:create", "-p", "masked", "-k", "provider", "-k"}, 6, 0, 49,
                "credential-store:create -p masked -k provider -k ");

        final List<String> candidates = new ArrayList<>();

        assertThat(completition.complete(NOT_NEEDED, commandLine, candidates)).isEqualTo(49);

        assertThat(candidates).doesNotContain("provider=");
    }

    @Test
    public void shouldReturnOptionOf() {
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf(null)).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf("")).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf("=value")).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf(" = value")).isEmpty();
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf("option=value")).isEqualTo("option");
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf("option=")).isEqualTo("option");
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf("option =")).isEqualTo("option");
        assertThat(CredentialStoreProtectionCompletionSupport.optionOf(" option = value")).isEqualTo("option");
    }
}

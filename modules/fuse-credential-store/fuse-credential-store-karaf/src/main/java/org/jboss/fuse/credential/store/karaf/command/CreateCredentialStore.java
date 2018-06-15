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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.CommandException;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jboss.fuse.credential.store.karaf.Defaults;
import org.jboss.fuse.credential.store.karaf.util.CredentialStoreHelper;
import org.jboss.fuse.credential.store.karaf.util.OptionsHelper;
import org.jboss.fuse.credential.store.karaf.util.ProtectionType;
import org.jboss.fuse.credential.store.karaf.util.ProviderHelper;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;

import static org.jboss.fuse.credential.store.karaf.util.OptionsHelper.attributesFromOptions;

/**
 * An Apache Karaf shell command to create a Credential store.
 */
@Command(scope = "credential-store", name = "create", description = "Create credential store")
@Service
public final class CreateCredentialStore implements Action {

    @Option(name = "-k", aliases = {"--protection-attributes"},
            description = "Credential store protection attributes, used to configure the credential protection",
            multiValued = true)
    @Completion(caseSensitive = true, value = CredentialStoreProtectionCompletionSupport.class)
    List<String> credentialAttributes = Collections.emptyList();

    @Option(name = "-p", aliases = {"--protection-type"}, description = "Credential store protection type",
            multiValued = false, valueToShowInHelp = Option.DEFAULT_STRING)
    ProtectionType credentialType = Defaults.CREDENTIAL_TYPE;

    @Option(name = "--provider",
            description = "Credential store provider, eight fully qualified class name of the provider or the provider name if the provider is registered with security runtime",
            multiValued = false, valueToShowInHelp = Option.DEFAULT_STRING)
    @Completion(caseSensitive = true, value = ProviderCompletionSupport.class)
    String provider = Defaults.PROVIDER;

    @Option(name = "-t", aliases = {"--store-type"}, description = "Credential store implementation type (algorithm)",
            multiValued = false, valueToShowInHelp = Option.DEFAULT_STRING)
    @Completion(caseSensitive = true, value = CredentialStoreAlgorithmCompletionSupport.class)
    String storeAlgorithm = Defaults.CREDENTIAL_STORE_ALGORITHM;

    @Option(name = "-a", aliases = {"--store-attributes"},
            description = "Credential store attributes, used to configure the credential store", multiValued = true)
    List<String> storeAttributes = Collections.emptyList();

    /**
     * Delegates to {@link ProtectionType} to create parameters needed to configure {@link CredentialSource} thats in
     * turn for the protection of the {@link CredentialStore}. Used by the command line to present to the user
     * configuration needed to setup the Credential Store.
     *
     * @param type
     *            type of protection
     * @param parameters
     *            parameters given by the user
     * @return parameters needed to configure the CredentialSource
     * @throws CommandException
     * @throws GeneralSecurityException
     * @throws IOException
     */
    static Map<String, String> createCredentialSourceConfiguration(final ProtectionType type,
            final List<String> parameters) throws CommandException, GeneralSecurityException, IOException {
        final Map<String, String> attributes = OptionsHelper.attributesFromOptions(parameters);

        return type.createConfiguration(attributes);
    }

    /**
     * Performs the {@link CredentialStore} creation, by the way of instantiation, initialization and in the end
     * flushing the Credential store implementation. The given attributes are combined with the default attributes so
     * that the user doesn't need to specify a lot of parameters needed for the initialization of the Credential source.
     *
     * @see CredentialStoreHelper#defaultCredentialStoreAttributesFor(String)
     *
     * @param algorithm
     *            of the Credential store, must be one supported by the provider
     * @param givenAttributes
     *            configuration parameters that will be combined with default to initialize the Credential store
     * @param credentialSource
     *            the protection of the Credential store
     * @param provider
     *            provider whose implementation will be used for the Credential store
     * @throws GeneralSecurityException
     */
    static void createCredentialStore(final String algorithm, final Map<String, String> givenAttributes,
            final CredentialSource credentialSource, final Provider provider) throws GeneralSecurityException {
        final CredentialStore credentialStore = CredentialStore.getInstance(algorithm, provider);

        final CredentialStore.ProtectionParameter protectionParameter = new CredentialStore.CredentialSourceProtectionParameter(
                credentialSource);

        final Map<String, String> attributes = CredentialStoreHelper.defaultCredentialStoreAttributesFor(algorithm);
        attributes.putAll(givenAttributes);

        // Since Elytron 1.1.0, it is required to add the "create=true" attribute to enable automatic
        // creation of the credential store
        attributes.put("create", "true");
        credentialStore.initialize(attributes, protectionParameter);

        credentialStore.flush();
    }

    /**
     * Performs the creation of Credential store according to the given command line options.
     */
    @Override
    public Object execute() throws Exception {
        final Map<String, String> attributes = attributesFromOptions(storeAttributes);

        final Provider providerToUse = ProviderHelper.provider(provider);

        final Map<String, String> credentialSourceConfiguration = createCredentialSourceConfiguration(credentialType,
                credentialAttributes);

        final CredentialSource credential = credentialType.createCredentialSource(credentialSourceConfiguration);

        createCredentialStore(storeAlgorithm, attributes, credential, providerToUse);

        final ShellTable table = new ShellTable();
        table.column(new Col("Variable"));
        table.column(new Col("Value"));

        final StringBuilder buffy = new StringBuilder();

        if (credentialType != Defaults.CREDENTIAL_TYPE) {
            appendConfigurationTo(Collections.singletonMap("CREDENTIAL_STORE_PROTECTION_TYPE", credentialType.name()),
                    table, buffy);
        }

        appendConfigurationTo(credentialSourceConfiguration, table, buffy);
        appendConfigurationTo(attributes.entrySet().stream()
                .collect(Collectors.toMap(e -> "CREDENTIAL_STORE_ATTR_" + e.getKey(), Entry::getValue)), table, buffy);

        System.out.println("In order to use this credential store set the following environment variables");
        table.print(System.out);
        System.out.println("Or simply use this:");
        System.out.print(buffy.toString());

        return null;
    }

    /**
     * Adds the given configuration key-values to the displayed {@link ShellTable} and to the given
     * {@link StringBuilder}. The table will contain the raw values, and the string will contain formated commands for
     * setting the environment variables.
     *
     * @param configuration
     *            key-value environment variables to append
     * @param table
     *            table to append to
     * @param buffy
     *            string to append to
     */
    private void appendConfigurationTo(final Map<String, String> configuration, final ShellTable table,
            final StringBuilder buffy) {
        for (final Entry<String, String> entry : configuration.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            table.addRow().addContent(key, value);
            buffy.append("export ").append(key).append('=').append(value).append(System.lineSeparator());
        }
    }

}

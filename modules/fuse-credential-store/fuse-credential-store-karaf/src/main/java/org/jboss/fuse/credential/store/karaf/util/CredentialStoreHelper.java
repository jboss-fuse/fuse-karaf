/**
 *  Copyright 2005-2017 Red Hat, Inc.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.getenv;

import org.jboss.fuse.credential.store.karaf.Defaults;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStore.ProtectionParameter;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;

import static org.jboss.fuse.credential.store.karaf.util.EnvironmentHelper.attributesFromEnvironment;

/**
 * Utility class with methods relating to {@link CredentialStore} usage.
 */
public final class CredentialStoreHelper {

    private static final String CREDENTIAL_STORE_ALGORITHM_ENV = "CREDENTIAL_STORE_ALGORITHM";

    private static final String CREDENTIAL_STORE_ATTR_ENV_PREFIX = "CREDENTIAL_STORE_ATTR_";

    private static final String CREDENTIAL_STORE_PROVIDER_ENV = "CREDENTIAL_STORE_PROVIDER";

    /**
     * Regular expression that matches store reference syntax
     */
    private static final Pattern STORE_REFERENCE_REGEX = Pattern.compile("CS:(.+)");

    private CredentialStoreHelper() {
        // utility class
    }

    /**
     * Determines if any values are in the format of store reference.
     *
     * @param values
     *            property values
     * @return true if any value is in the format of store reference
     */
    public static boolean containsStoreReferences(final Collection<String> values) {
        return values.stream().anyMatch(STORE_REFERENCE_REGEX.asPredicate());
    }

    public static boolean couldBeCredentialStoreAlias(final String value) {
        return STORE_REFERENCE_REGEX.matcher(value).matches();
    }

    /**
     * Creates the credential store specified by the configuration in environment variables.
     *
     * @return the credential store
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public static CredentialStore credentialStoreFromEnvironment() throws GeneralSecurityException, IOException {
        final String credentialStoreAlgorithm = Optional.ofNullable(getenv(CREDENTIAL_STORE_ALGORITHM_ENV))
                .orElse(Defaults.CREDENTIAL_STORE_ALGORITHM);

        final Provider provider = ProviderHelper.provider(
                Optional.ofNullable(getenv(CREDENTIAL_STORE_PROVIDER_ENV)).orElse(ProviderHelper.WILDFLY_PROVIDER));

        final CredentialStore credentialStore = CredentialStore.getInstance(credentialStoreAlgorithm, provider);

        final Map<String, String> attributes = defaultCredentialStoreAttributesFor(credentialStoreAlgorithm);
        attributes.putAll(attributesFromEnvironment(CREDENTIAL_STORE_ATTR_ENV_PREFIX));

        final ProtectionParameter protectionParameter = createProtectionParameterFromEnvironment();

        credentialStore.initialize(attributes, protectionParameter);

        return credentialStore;
    }

    public static Map<String, String> defaultCredentialStoreAttributesFor(final String credentialStoreAlgorithm) {
        final Map<String, String> defaults = new HashMap<>();

        if (KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE.equals(credentialStoreAlgorithm)) {
            defaults.put("keyStoreType", "PKCS12");
        }

        return defaults;
    }

    public static String referenceForAlias(final String alias) {
        return "CS:" + alias;
    }

    public static String toCredentialStoreAlias(final String value) {
        final Matcher matcher = STORE_REFERENCE_REGEX.matcher(value);

        matcher.matches();

        return matcher.group(1);
    }

    static ProtectionParameter createProtectionParameterFromEnvironment() throws GeneralSecurityException, IOException {
        final String credentialTypeParam = Optional.ofNullable(getenv("CREDENTIAL_STORE_PROTECTION_TYPE"))
                .orElse(Defaults.CREDENTIAL_TYPE.name());
        final ProtectionType credentialType = ProtectionType.valueOf(credentialTypeParam);

        final CredentialSource credentialSource = credentialType.createCredentialSource(getenv());

        return new CredentialStore.CredentialSourceProtectionParameter(credentialSource);
    }

}

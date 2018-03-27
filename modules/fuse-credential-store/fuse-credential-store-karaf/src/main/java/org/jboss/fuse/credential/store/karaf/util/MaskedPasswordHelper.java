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

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jboss.fuse.credential.store.karaf.util.ProtectionType.CredentialSourceHandler;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordSpec;

import static java.lang.Integer.parseInt;

/**
 * {@link CredentialSourceHandler} implementation for masked password Credential store protection.
 */
final class MaskedPasswordHelper implements CredentialSourceHandler {

    private static final String DEFAULT_ALGORITHM = MaskedPassword.ALGORITHM_MASKED_HMAC_SHA512_AES_256;

    private static final String[] OPTIONS = {"provider", "algorithm", "password", "salt", "iterations"};

    private final String[] supportedMaskedAlgorithms;

    public MaskedPasswordHelper() {
        final String passwordFactoryType = PasswordFactory.class.getSimpleName();

        supportedMaskedAlgorithms = Arrays.stream(Security.getProviders()).flatMap(p -> p.getServices().stream())
                .filter(s -> passwordFactoryType.equals(s.getType())).map(s -> s.getAlgorithm())
                .filter(a -> a.startsWith("masked-")).toArray(String[]::new);
    }

    @Override
    public Map<String, String> createConfiguration(final Map<String, String> attributes)
            throws GeneralSecurityException, IOException {
        final Provider provider = ProviderHelper
                .provider(option(attributes, "provider", ProviderHelper.WILDFLY_PROVIDER));

        final String algorithm = option(attributes, "algorithm", DEFAULT_ALGORITHM);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);

        final String password = option(attributes, "password", null);

        final String salt = option(attributes, "salt", "");
        final String iterations = option(attributes, "iterations", "");

        final AlgorithmParameterSpec algorithmParameterSpec;
        if (salt.isEmpty() && iterations.isEmpty()) {
            algorithmParameterSpec = null;
        } else if (salt.isEmpty()) {
            algorithmParameterSpec = new IteratedPasswordAlgorithmSpec(parseInt(iterations));
        } else {
            final byte[] saltBytes = Base64.getDecoder().decode(salt);
            algorithmParameterSpec = new IteratedSaltedPasswordAlgorithmSpec(parseInt(iterations), saltBytes);
        }

        final EncryptablePasswordSpec keySpec = new EncryptablePasswordSpec(password.toCharArray(),
                algorithmParameterSpec);

        final MaskedPassword maskedPassword = passwordFactory.generatePassword(keySpec).castAs(MaskedPassword.class);

        final MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec = maskedPassword.getParameterSpec();

        final Map<String, String> configuration = new HashMap<>();
        final Encoder encoder = Base64.getEncoder();

        if (!DEFAULT_ALGORITHM.equals(algorithm)) {
            configuration.put(CREDENTIAL_STORE_PROTECTION_ALGORITHM, algorithm);
        }

        configuration.put(CREDENTIAL_STORE_PROTECTION, encoder.encodeToString(maskedPassword.getMaskedPasswordBytes()));

        final AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance(algorithm, provider);
        algorithmParameters.init(maskedPasswordAlgorithmSpec);
        final byte[] encoded = algorithmParameters.getEncoded();

        configuration.put(CREDENTIAL_STORE_PROTECTION_PARAMS, encoder.encodeToString(encoded));

        return configuration;
    }

    @Override
    public CredentialSource createCredentialSource(final Map<String, String> configuration)
            throws GeneralSecurityException, IOException {
        final String algorithmParamsBase64 = option(configuration, CREDENTIAL_STORE_PROTECTION_PARAMS, "");

        final Decoder decoder = Base64.getDecoder();

        final byte[] encodedAlgorithmParams = decoder.decode(algorithmParamsBase64);

        final String algorithm = option(configuration, CREDENTIAL_STORE_PROTECTION_ALGORITHM, DEFAULT_ALGORITHM);

        final Provider provider = ProviderHelper
                .provider(option(configuration, CREDENTIAL_STORE_PROTECTION_PROVIDER, ProviderHelper.WILDFLY_PROVIDER));

        final AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance(algorithm, provider);

        algorithmParameters.init(encodedAlgorithmParams);

        final MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec = algorithmParameters
                .getParameterSpec(MaskedPasswordAlgorithmSpec.class);

        final char[] initialKeyMaterial = maskedPasswordAlgorithmSpec.getInitialKeyMaterial();
        final int iterationCount = maskedPasswordAlgorithmSpec.getIterationCount();
        final byte[] salt = maskedPasswordAlgorithmSpec.getSalt();

        final String maskedPasswordBase64 = option(configuration, CREDENTIAL_STORE_PROTECTION, "");
        final byte[] maskedPasswordBytes = decoder.decode(maskedPasswordBase64);

        final MaskedPasswordSpec maskedPasswordSpec = new MaskedPasswordSpec(initialKeyMaterial, iterationCount, salt,
                maskedPasswordBytes);

        final PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);

        final Password maskedPassword = passwordFactory.generatePassword(maskedPasswordSpec);

        final PasswordFactory clearPasswordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR,
                provider);
        final ClearPasswordSpec clearPasswordSpec = passwordFactory.getKeySpec(maskedPassword, ClearPasswordSpec.class);

        final Password password = clearPasswordFactory.generatePassword(clearPasswordSpec);

        final PasswordCredential passwordCredential = new PasswordCredential(password);

        return IdentityCredentials.NONE.withCredential(passwordCredential);
    }

    @Override
    public String[] getOptionValuesFor(final String option) {
        switch (option) {
            case "provider":
                return Arrays.stream(Security.getProviders()).map(p -> p.getName()).toArray(String[]::new);
            case "algorithm":
                return supportedMaskedAlgorithms;
            default:
        }

        return new String[0];
    }

    @Override
    public String[] getSupportedOptions() {
        return OPTIONS.clone();
    }

    private String option(final Map<String, String> attributes, final String key, final String defaultValue) {
        final String value = attributes.get(key);

        if ((value == null) && (defaultValue == null)) {
            throw new IllegalArgumentException("Parameter `" + key + "` is required");
        }

        return Optional.ofNullable(value).orElse(defaultValue).trim();
    }
}

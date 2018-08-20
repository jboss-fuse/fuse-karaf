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

import java.io.File;
import java.security.AlgorithmParameters;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
//CHECKSTYLE:OFF
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordSpec;
import sun.security.jca.Providers;

import static java.lang.Integer.parseInt;
import static org.junit.Assert.assertTrue;
//CHECKSTYLE:ON

public class CredentialStoreHelperTest {

    public static final Logger LOG = LoggerFactory.getLogger(CredentialStoreHelperTest.class);

    @Test
    public void accessCredentialStore() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new WildFlyElytronProvider());

        // KeyStoreCredentialStore is default algorithm when using
        // org.jboss.fuse.credential.store.karaf.util.CredentialStoreHelper.credentialStoreFromEnvironment()
        // it's a credential store which is backed by a key store
        CredentialStore cs1 = CredentialStore.getInstance("KeyStoreCredentialStore");
        // Credential store implementation which uses the legacy "vault" format
        CredentialStore cs2 = CredentialStore.getInstance("VaultCredentialStore");
        // map-backed credential store implementation
        CredentialStore cs3 = CredentialStore.getInstance("MapCredentialStore");

        LOG.info("Credential Store 1: {}, aliases: {}", cs1, cs1.getAliases());
        LOG.info("Credential Store 2: {}, aliases: {}", cs2, /*cs2.getAliases()*/ null);
        LOG.info("Credential Store 3: {}, aliases: {}", cs3, cs3.getAliases());

        // KeyStoreCredentialStore uses 3 parameters/attributes
        //  - location
        //  - modifiable
        //  - keyStoreType

        //CHECKSTYLE:OFF

        // from $JAVA_HOME/jre/lib/security/java.security, keystore.type
        LOG.info("Default KeyStore type: {}", KeyStore.getDefaultType());

        Set<String> providers = new TreeSet<>();
        Set<String> serviceTypes = new TreeSet<>();
//        Set<String> serviceClasses = new TreeSet<>();
        for (Provider p : Providers.getProviderList().providers()) {
            providers.add(p.getName());
            for (Provider.Service s : p.getServices()) {
                serviceTypes.add(s.getType());
//                serviceClasses.add(s.getClassName());
            }
        }
        LOG.info("Providers:");
        for (String p : providers) {
            LOG.info(" - {}", p);
        }
        LOG.info("Service types:");
        for (String st : serviceTypes) {
            LOG.info(" - {}", st);
        }
//        LOG.info("Service classes:");
//        for (String sc : serviceClasses) {
//            LOG.info(" - {}", sc);
//        }

        LOG.info("KeyStore providers / algorithms:");
        for (Provider p : Providers.getProviderList().providers()) {
            for (Provider.Service s : p.getServices()) {
                if ("KeyStore".equals(s.getType())) {
                    LOG.info(" - {} / {}", s.getProvider().getName(), s.getAlgorithm());
                }
            }
        }

        LOG.info("PasswordFactory providers / algorithms:");
        for (Provider p : Providers.getProviderList().providers()) {
            for (Provider.Service s : p.getServices()) {
                if ("PasswordFactory".equals(s.getType())) {
                    LOG.info(" - {} / {}", s.getProvider().getName(), s.getAlgorithm());
                }
            }
        }

        LOG.info("SecretKeyFactory providers / algorithms:");
        for (Provider p : Providers.getProviderList().providers()) {
            for (Provider.Service s : p.getServices()) {
                if ("SecretKeyFactory".equals(s.getType())) {
                    LOG.info(" - {} / {}", s.getProvider().getName(), s.getAlgorithm());
                }
            }
        }

        LOG.info("Cipher providers / algorithms:");
        for (Provider p : Providers.getProviderList().providers()) {
            for (Provider.Service s : p.getServices()) {
                if ("Cipher".equals(s.getType())) {
                    LOG.info(" - {} / {}", s.getProvider().getName(), s.getAlgorithm());
                }
            }
        }

        LOG.info("AlgorithmParameters providers / algorithms:");
        for (Provider p : Providers.getProviderList().providers()) {
            for (Provider.Service s : p.getServices()) {
                if ("AlgorithmParameters".equals(s.getType())) {
                    LOG.info(" - {} / {}", s.getProvider().getName(), s.getAlgorithm());
                }
            }
        }

        //CHECKSTYLE:ON

        Password pwd1 = PasswordFactory.getInstance("clear")
                .generatePassword(new ClearPasswordSpec("secret1".toCharArray()));
        Password pwd2 = PasswordFactory.getInstance("clear")
                .generatePassword(new ClearPasswordSpec("secret2".toCharArray()));

        CredentialSource cs = IdentityCredentials.NONE.withCredential(new PasswordCredential(pwd1));
        CredentialStore.ProtectionParameter pp = new CredentialStore.CredentialSourceProtectionParameter(cs);
        Map<String, String> attrs = new HashMap<>();
        attrs.put("keyStoreType", "PKCS12");
        attrs.put("location", String.format("target/credentials-%12d.store", new Date().getTime()));
        attrs.put("create", "true");
        cs1.initialize(attrs, pp);
        cs1.store("alias1", new PasswordCredential(pwd2));
        cs1.flush();

        LOG.info("Credential Store 1: {}, aliases: {}", cs1, cs1.getAliases());
        PasswordCredential pwd = cs1.retrieve("alias1", PasswordCredential.class);
        LOG.info("Retrieved password: {}", new String(((ClearPassword)pwd.getPassword()).getPassword()));
    }

    @Test
    public void asBefore() throws Exception {
        Provider provider = new WildFlyElytronProvider();

        Map<String, String> attributes1 = new HashMap<>();
        attributes1.put("create", "true");
        attributes1.put("location", "target/credential.store");
        attributes1.put("keyStoreType", "PKCS12");

        Map<String, String> attributes2 = new HashMap<>();
        attributes2.put("password", "password");
        attributes2.put("algorithm", "masked-SHA1-DES-EDE");
//        attributes2.put("salt", "abcdefgh"); // not required
//        attributes2.put("iterations", "1000"); // not required

        String algorithm = attributes2.get("algorithm");
        PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);

        String password = attributes2.get("password");
        String salt = attributes2.get("salt");
        String iterations = attributes2.get("iterations");

        AlgorithmParameterSpec algorithmParameterSpec;
        // salt && iterations:
        if (salt == null && iterations == null) {
            algorithmParameterSpec = null;
        } else if (salt == null || salt.isEmpty()) {
            algorithmParameterSpec = new IteratedPasswordAlgorithmSpec(parseInt(iterations));
        } else {
            final byte[] saltBytes = Base64.getDecoder().decode(salt);
            algorithmParameterSpec = new IteratedSaltedPasswordAlgorithmSpec(parseInt(iterations), saltBytes);
        }
        final EncryptablePasswordSpec keySpec = new EncryptablePasswordSpec(password.toCharArray(), algorithmParameterSpec);

        MaskedPassword maskedPassword = passwordFactory.generatePassword(keySpec).castAs(MaskedPassword.class);
        MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec = maskedPassword.getParameterSpec();

        AlgorithmParameters params = AlgorithmParameters.getInstance(algorithm, provider);
        // initialize parameters with decoded spec
        params.init(maskedPasswordAlgorithmSpec);
        byte[] paramsBytes = params.getEncoded();

        Base64.Encoder encoder = Base64.getEncoder();

        Map<String, String> configuration = new HashMap<>();
        configuration.put("CREDENTIAL_STORE_PROTECTION_ALGORITHM", algorithm);
        configuration.put("CREDENTIAL_STORE_PROTECTION_PARAMS", encoder.encodeToString(paramsBytes));
        configuration.put("CREDENTIAL_STORE_PROTECTION", encoder.encodeToString(maskedPassword.getMaskedPasswordBytes()));

        // we have credential store key, now let's create the credential store itself
        params = AlgorithmParameters.getInstance(algorithm, provider);
        // initialize parameters with encodd spec
        params.init(paramsBytes);

        maskedPasswordAlgorithmSpec = params.getParameterSpec(MaskedPasswordAlgorithmSpec.class);
        // specification of all that's needed to create MaskedPassword
        MaskedPasswordSpec spec = new MaskedPasswordSpec(maskedPasswordAlgorithmSpec.getInitialKeyMaterial(),
                maskedPasswordAlgorithmSpec.getIterationCount(),
                maskedPasswordAlgorithmSpec.getSalt(),
                Base64.getDecoder().decode(configuration.get("CREDENTIAL_STORE_PROTECTION")));

        maskedPassword = passwordFactory.generatePassword(spec).castAs(MaskedPassword.class);

        // masked -> clear text
        PasswordFactory ctPasswordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR, provider);
        ClearPasswordSpec clearPasswordSpec = passwordFactory.getKeySpec(maskedPassword, ClearPasswordSpec.class);
        Password ctPassword = ctPasswordFactory.generatePassword(clearPasswordSpec);

        // org.wildfly.security.credential.source.CredentialSource is needed to initialize CredentialStore
        CredentialSource source = IdentityCredentials.NONE.withCredential(new PasswordCredential(ctPassword));

        CredentialStore cs = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE,
                provider);
        CredentialStore.CredentialSourceProtectionParameter csProtection
                = new CredentialStore.CredentialSourceProtectionParameter(source);
        cs.initialize(attributes1, csProtection);
        cs.flush();

        assertTrue(new File(attributes1.get("location")).isFile());
    }

}

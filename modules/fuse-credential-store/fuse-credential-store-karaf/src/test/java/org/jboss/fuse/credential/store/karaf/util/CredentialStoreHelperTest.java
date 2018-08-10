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

import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
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
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
//CHECKSTYLE:OFF
import sun.security.jca.Providers;
//CHECKSTYLE:ON

public class CredentialStoreHelperTest {

    public static final Logger LOG = LoggerFactory.getLogger(CredentialStoreHelperTest.class);

    @Test
    public void shouldCreateProtectionParameter() {

    }

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

}

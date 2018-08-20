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
package org.jboss.fuse.credential.store.karaf;

import java.io.File;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    final Activator activator = new Activator();

    CredentialStore credentialStore;

    @After
    public void deregisterElytronProvider() {
        Security.removeProvider(new WildFlyElytronProvider().getName());
    }

    @Before
    public void initializeCredentialStore() throws Exception {
        activator.start(null);

        final WildFlyElytronProvider elytron = new WildFlyElytronProvider();
        Security.addProvider(elytron);

        final PasswordFactory passwordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR, elytron);
        final Password password = passwordFactory.generatePassword(
                new ClearPasswordSpec("it was the best of times it was the worst of times".toCharArray()));

        final Credential credential = new PasswordCredential(password);

        final CredentialSource credentialSource = IdentityCredentials.NONE.withCredential(credential);

        credentialStore = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE, elytron);

        final String storePath = new File(tmp.getRoot(), "credential.store").getAbsolutePath();
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("location", storePath);
        parameters.put("keyStoreType", "JCEKS");
        parameters.put("create", "true");

        credentialStore.initialize(parameters,
                new CredentialStore.CredentialSourceProtectionParameter(credentialSource));

        final Password secret = passwordFactory
                .generatePassword(new ClearPasswordSpec("this is a password".toCharArray()));
        final Credential value = new PasswordCredential(secret);
        credentialStore.store("alias", value);

        credentialStore.flush();
    }

    @Test
    public void shouldNotReplaceSystemPropertiesNotInCredentialStoreFormat() {
        assertThat(activator.replaced(credentialStore, "key", "value")).isFalse();
    }

    @Test
    public void shouldReplaceSystemPropertiesInCredentialStoreFormat() {
        assertThat(activator.replaced(credentialStore, "key", "CS:alias")).isTrue();

        assertThat(System.getProperty("key")).isEqualTo("this is a password");
    }

    @After
    public void stopBundle() throws Exception {
        activator.stop(null);
    }
}

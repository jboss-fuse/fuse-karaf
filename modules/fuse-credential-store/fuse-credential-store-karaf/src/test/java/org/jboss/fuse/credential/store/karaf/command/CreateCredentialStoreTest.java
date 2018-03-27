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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.jboss.fuse.credential.store.karaf.util.ProtectionType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateCredentialStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void shouldCreateCredentialSource() throws Exception {
        final Map<String, String> configuration = CreateCredentialStore.createCredentialSourceConfiguration(
                ProtectionType.masked,
                Collections.singletonList("password=The quick brown fox jumped over the lazy dog"));

        assertThat(configuration).containsKeys("CREDENTIAL_STORE_PROTECTION_PARAMS", "CREDENTIAL_STORE_PROTECTION");
    }

    @Test
    public void shouldCreateCredentialStoreFromCommand() throws Exception {
        final File storeFile = new File(tmp.getRoot(), "credential.store");

        final CreateCredentialStore command = new CreateCredentialStore();

        command.storeAttributes = Arrays.asList("location=" + storeFile.getAbsolutePath(), "keyStoreType=PKCS12");
        command.credentialAttributes = Arrays.asList("algorithm=masked-MD5-DES",
                "password=The quick brown fox jumped over the lazy dog");

        final PrintStream original = System.out;
        try {
            final ByteArrayOutputStream capture = new ByteArrayOutputStream();

            System.setOut(new PrintStream(capture));

            command.execute();

            final String output = new String(capture.toByteArray());

            assertThat(output).contains("In order to use this credential store set the following environment variables")
                    .contains("export CREDENTIAL_STORE_PROTECTION_PARAMS=")
                    .contains("export CREDENTIAL_STORE_PROTECTION=").contains("export CREDENTIAL_STORE_ATTR_location=")
                    .contains("export CREDENTIAL_STORE_ATTR_keyStoreType=");
        } finally {
            System.setOut(original);
        }

    }

    @Test
    public void shouldCreateInitializeAndPersistCredentialStore() throws IOException, GeneralSecurityException {
        final File storeFile = new File(tmp.getRoot(), "credential.store");

        final Map<String, String> attributes = Collections.singletonMap("location", storeFile.getAbsolutePath());

        final Provider provider = new WildFlyElytronProvider();

        final CredentialSource credentialSource = IdentityCredentials.NONE.withCredential(
                new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "test".toCharArray())));

        CreateCredentialStore.createCredentialStore(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE, attributes,
                credentialSource, provider);

        assertThat(storeFile).exists().isFile();
    }
}

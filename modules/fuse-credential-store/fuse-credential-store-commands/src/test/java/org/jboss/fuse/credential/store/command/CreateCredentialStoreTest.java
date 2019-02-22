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
package org.jboss.fuse.credential.store.command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import org.apache.karaf.shell.api.console.Session;
import org.jboss.fuse.credential.store.impl.Activator;
import org.jboss.fuse.credential.store.impl.CredentialStoreServiceImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreateCredentialStoreTest {

    public static final Logger LOG = LoggerFactory.getLogger(CreateCredentialStoreTest.class);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private CredentialStoreServiceImpl credentialStoreService;

    @Before
    public void init() throws Exception {
        System.setProperty("karaf.etc", "target");

        BundleContext context = mock(BundleContext.class);
        when(context.getBundles()).thenReturn(new Bundle[0]);

        new Activator().start(context);

        credentialStoreService = new CredentialStoreServiceImpl(context, new WildFlyElytronProvider());
    }

    @Test
    public void shouldCreateCredentialStoreFromCommand() throws Exception {
        final File storeFile = new File(tmp.getRoot(), "credential.store");

        Session session = mock(Session.class);
        when(session.readLine(anyString(), anyChar())).thenReturn("The quick brown fox jumped over the lazy dog");

        final CreateCredentialStore command = new CreateCredentialStore();

        command.credentialStoreService = credentialStoreService;
        command.iterationCount = 1000;
        command.location = storeFile.getAbsolutePath();
        command.algorithm = "masked-MD5-DES";
        command.session = session;
        command.force = true;

        final PrintStream original = System.out;
        try {
            final ByteArrayOutputStream capture = new ByteArrayOutputStream();

            System.setOut(new PrintStream(capture));

            command.execute();

            final String output = new String(capture.toByteArray());

            assertThat(output).contains("Credential store configuration was not persisted")
                    .contains("CREDENTIAL_STORE_PROTECTION_PARAMS=")
                    .contains("CREDENTIAL_STORE_PROTECTION=")
                    .contains("CREDENTIAL_STORE_LOCATION=");
        } finally {
            System.setOut(original);
        }
    }

    @Test
    public void shouldCreateInitializeAndPersistCredentialStore() throws Exception {
        final File storeFile = new File(tmp.getRoot(), "credential.store");

        Session session = mock(Session.class);
        when(session.readLine(anyString(), anyChar())).thenReturn("test");

        final CreateCredentialStore command = new CreateCredentialStore();

        command.credentialStoreService = credentialStoreService;
        command.iterationCount = 1000;
        command.location = storeFile.getAbsolutePath();
        command.algorithm = "masked-MD5-DES";
        command.session = session;
        command.persistConfiguration = true;
        command.force = true;

        final PrintStream original = System.out;
        try {
            final ByteArrayOutputStream capture = new ByteArrayOutputStream();

            System.setOut(new PrintStream(capture));

            command.execute();
        } finally {
            System.setOut(original);
        }

        assertThat(storeFile).exists().isFile();

        StoreInCredentialStore store = new StoreInCredentialStore();
        store.credentialStoreService = credentialStoreService;
        store.alias = "my.password";
        store.secret = "sec4et";
        store.execute();

        String credential = credentialStoreService.retrievePassword("my.password");
        assertNotNull(credential);
    }

    @Test
    public void createCredentialStoreAsInEAP() throws Exception {
        Security.addProvider(new WildFlyElytronProvider());
        new File("target/my-cstore-1.p12").delete();

        // org.wildfly.extension.elytron: `modules/system/layers/base/org/wildfly/extension/elytron/main/wildfly-elytron-integration-3.0.16.Final-redhat-1.jar
        // org.wildfly.security.elytron-private: `modules/system/layers/base/org/wildfly/security/elytron-private/main/wildfly-elytron-1.1.10.Final-redhat-1.jar

        // command:
        // /subsystem=elytron/credential-store=my-cstore-1:add(location="/data/tmp/my-cstore-1.jceks", credential-reference={clear-text=STORE_PASSWORD}, create=true)

        CredentialStore cs = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE);
        assertNotNull(cs);
        Field spi = cs.getClass().getDeclaredField("spi");
        spi.setAccessible(true);
        LOG.info("Store: {}, type: {}", spi.get(cs).getClass().getName(), cs.getType());

        assertFalse(cs.isInitialized());

        // EAP initializes CS using these parameters:
        // credentialStoreAttributes = {java.util.HashMap@10655}  size = 4
        // 0 = {java.util.HashMap$Node@10705} "keyStoreType" -> "JCEKS"
        // 1 = {java.util.HashMap$Node@10707} "create" -> "true"
        // 2 = {java.util.HashMap$Node@10726} "location" -> "/data/tmp/my-cstore-7.jceks"
        // 3 = {java.util.HashMap$Node@10706} "modifiable" -> "true"

        // valid attributes: org.wildfly.security.credential.store.impl.KeyStoreCredentialStore.validAttribtues
        // validAttribtues = {java.util.Arrays$ArrayList@11133}  size = 8
        // 0 = "create" = false by default
        // 1 = "cryptoAlg" = "AES/CBC/NoPadding" by default
        // 2 = "external" = false by default
        // 3 = "externalPath"
        // 4 = "keyAlias" = "cs_key" by default
        // 5 = "keyStoreType" = "JCEKS" by default
        // 6 = "location"
        // 7 = "modifiable" = true by default

        // credential-store:create uses keyStoreType = PKCS12
        Map<String, String> parameters = new HashMap<>();
        parameters.put("create", "true");
//        parameters.put("keyStoreType", "JCEKS");
        parameters.put("keyStoreType", "PKCS12");
//        parameters.put("location", "target/my-cstore-1.jceks");
        parameters.put("location", "target/my-cstore-1.p12");

        // password...
        ClearPassword clearPassword = ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "STORE_PASSWORD".toCharArray());
        Credential credential = new PasswordCredential(clearPassword);
        CredentialSource source = IdentityCredentials.NONE.withCredential(credential);
        CredentialStore.ProtectionParameter protectionParameter = new CredentialStore.CredentialSourceProtectionParameter(source);

        cs.initialize(parameters, protectionParameter);
        assertTrue(cs.isInitialized());
//        assertTrue(new File("target/my-cstore-1.jceks").isFile());
        assertTrue(new File("target/my-cstore-1.p12").isFile());

        cs.store("my-alias", new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "passw0rd".toCharArray())));
        cs.flush();
    }

    @Test
    public void jceksAsInEAP() throws Exception {
        KeyStore jceks = KeyStore.getInstance("JCEKS");
        assertNotNull(jceks);
        jceks.load(null, null);
        jceks.store(new FileOutputStream("target/my-cstore-2.jceks"), "STORE_PASSWORD".toCharArray());
    }

    @Test
    public void pkcs12AsInFuse() throws Exception {
        KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
        assertNotNull(pkcs12);
        pkcs12.load(null, null);
        pkcs12.store(new FileOutputStream("target/my-cstore-2.p12"), "STORE_PASSWORD".toCharArray());
    }

}

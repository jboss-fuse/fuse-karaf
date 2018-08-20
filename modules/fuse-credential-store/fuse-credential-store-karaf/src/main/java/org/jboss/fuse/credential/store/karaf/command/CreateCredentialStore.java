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

import java.io.File;
import java.security.Provider;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.credential.store.karaf.Activator;
import org.jboss.fuse.credential.store.karaf.command.completers.CredentialStoreAlgorithmCompletionSupport;
import org.jboss.fuse.credential.store.karaf.util.CredentialStoreConfiguration;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;

/**
 * An Apache Karaf shell command to create a Credential store.
 */
@Command(scope = "credential-store", name = "create", description = "Create PKCS#12 credential store")
@Service
public final class CreateCredentialStore extends AbstractCredentialStoreCommand {

    private static final int DEFAULT_IC = 200000;
    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    // see http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/diff/8763e65ce267/src/share/classes/com/sun/crypto/provider/KeyProtector.java
    // for the change to default values of iteration count for PBE algorithms
    @Option(name = "-ic", aliases = { "--iteration-count" }, description = "Iteration count parameter used for masked password generation")
    int iterationCount = DEFAULT_IC;

    @Option(name = "-a", aliases = { "--algorithm" }, description = "PBE algorithm used for masked password generation")
    @Completion(caseSensitive = true, value = CredentialStoreAlgorithmCompletionSupport.class)
    String algorithm = CredentialStoreConfiguration.DEFAULT_ALGORITHM;

    @Option(name = "-l", aliases = { "--location" }, description = "Location of credential store, by default, ${karaf.etc}/credential.store.p12 is used")
    String location;

    @Option(name = "-p", aliases = { "--persist" }, description = "Persists configuration of newly created credential store in ${karaf.etc}/system.properties")
    boolean persist;

    @Option(name = "-f", aliases = { "--force" }, description = "Force replacement of existing Credential Store configuration")
    boolean force;

    /**
     * Performs the creation of Credential store according to the given command line options.
     */
    @Override
    public Object execute() throws Exception {
        if (Activator.credentialStore != null && !force) {
            System.err.println("Credential store is already configured. To replace the configuration, please use \"--force\" option.");
            return null;
        }

        if (algorithm == null || "".equals(algorithm.trim())) {
            System.err.println("Please specify algorithm for credential store password");
            return null;
        }

        if (!CredentialStoreAlgorithmCompletionSupport.isSupported(algorithm)) {
            System.err.println("Algorithm " + algorithm + " is not supported");
            return null;
        }

        File credentialStoreFile = null;
        if (location == null) {
            credentialStoreFile = new File(System.getProperty("karaf.etc"), CredentialStoreConfiguration.DEFAULT_LOCATION);
        } else {
            credentialStoreFile = new File(location);
        }
        if (credentialStoreFile.isDirectory()) {
            File f = new File(credentialStoreFile, CredentialStoreConfiguration.DEFAULT_LOCATION);
            if (!force && f.isFile()) {
                System.err.println("File " + f.getCanonicalPath() + " already exist.");
                return null;
            }
            credentialStoreFile = f;
        } else if (!force && credentialStoreFile.isFile()) {
            System.err.println("File " + credentialStoreFile.getCanonicalPath() + " already exist.");
            return null;
        }

        if (iterationCount < 1000) {
            System.err.println("Please specify higher value of \"iteration count\" parameter. The default value is " + DEFAULT_IC + ".");
            return null;
        }

        final Provider providerToUse = Activator.getElytronProvider();

        // master password - will be encrypted using masked algorithm (iv, ic, salt) and together with algorithm specification
        // presented to user - or written to etc/system.properties
        // this password will be used both to encrypt credential store and to encrypt the passwords in store
        String password1 = session.readLine("Credential store password: ", '*');
        String password2 = session.readLine("Credential store password (repeat): ", '*');
        if (password1 == null || password2 == null || "".equals(password1.trim()) || "".equals(password2)) {
            System.err.println("Please specify password to protect credential store.");
            return null;
        }
        if (!password1.equals(password2)) {
            System.err.println("Passwords do not match.");
            return null;
        }

        byte[] seed = Activator.RANDOM.generateSeed(8);
        byte[] iv = Activator.RANDOM.generateSeed(40);
        char[] ivc = new char[iv.length];
        int idx = 0;
        for (byte b : iv) {
            ivc[idx] = CHARS[((int)iv[idx] & 0xff) % CHARS.length];
            idx++;
        }
        // ic, iv, salt
        MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec
                = new MaskedPasswordAlgorithmSpec(ivc, iterationCount, seed);
        for (int i = 0; i < ivc.length; i++) {
            iv[i] = 0;
        }

        PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, Activator.getElytronProvider());
        // ic, iv, salt, password.
        // Elytron's PasswordFactory can use it to generate masked passwords
        EncryptablePasswordSpec encryptablePasswordSpec = new EncryptablePasswordSpec(password1.toCharArray(),
                maskedPasswordAlgorithmSpec);

        // encrypted master password
        MaskedPassword maskedPassword = passwordFactory.generatePassword(encryptablePasswordSpec)
                .castAs(MaskedPassword.class);

        // now, persist everything that's required to recover the password and generate/initialize
        // credential store
        CredentialStoreConfiguration config = new CredentialStoreConfiguration();
        config.setLocation(credentialStoreFile);
        config.setProtectionAlgorithmName(algorithm);
        config.setProtectionParameters(maskedPassword.getParameterSpec());
        config.setKey(maskedPassword.getMaskedPasswordBytes());

        if (persist) {
            config.persist();
        }

        if (credentialStoreFile.isFile()) {
            if (!credentialStoreFile.delete()) {
                System.err.println("Problem when deleting existing credential store file: " + credentialStoreFile.getCanonicalPath());
                return null;
            }
        }

        // even if configuration isn't persisted, the credential store file is written
        CredentialStore cs = config.loadCredentialStore();

        System.out.println("\nCredential store was written to " + credentialStoreFile.getCanonicalPath());

        if (persist) {
            // the only place outside the Activator itself where we set system credential store
            Activator.setCredentialStore(cs);

            System.out.println("\nCredential store configuration was persisted in ${karaf.etc}/system.properties and" +
                    " is effective.");
        } else {
            System.out.println("\nCredential store configuration was not persisted and is not effective. Please use one of the following" +
                    " configuration options and restart Fuse.");

            System.out.println("Option #1: Configure these system properties (e.g., in etc/system.properties):");
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM, config.getProtectionAlgorithmName());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS, config.getEncodedProtectionParameters());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.PROPERTY_CREDENTIAL_STORE_PROTECTION, config.getEncodedKey());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.PROPERTY_CREDENTIAL_STORE_LOCATION, config.getLocation().getCanonicalPath());

            System.out.println("Option #2: Configure these environmental variables (e.g., in bin/setenv):");
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.ENV_CREDENTIAL_STORE_PROTECTION_ALGORITHM, config.getProtectionAlgorithmName());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.ENV_CREDENTIAL_STORE_PROTECTION_PARAMS, config.getEncodedProtectionParameters());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.ENV_CREDENTIAL_STORE_PROTECTION, config.getEncodedKey());
            System.out.printf(" - %s=%s%n", CredentialStoreConfiguration.ENV_CREDENTIAL_STORE_LOCATION, config.getLocation().getCanonicalPath());
        }

        return null;
    }

}

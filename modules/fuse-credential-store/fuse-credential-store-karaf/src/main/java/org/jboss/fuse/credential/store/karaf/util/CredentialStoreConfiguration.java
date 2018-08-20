/*
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
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.HashMap;
import java.util.Map;

import org.apache.felix.utils.properties.Properties;
import org.jboss.fuse.credential.store.karaf.Activator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordSpec;

/**
 * <p>Set of configuration parameters for {@link org.wildfly.security.credential.store.CredentialStore} specified
 * either via environment or system properties.</p>
 * <p>String attributes are usually base64 decoded.</p>
 */
public class CredentialStoreConfiguration {

    public static Logger LOG = LoggerFactory.getLogger(CredentialStoreConfiguration.class);

    public static final String ENV_CREDENTIAL_STORE_PROTECTION_ALGORITHM = "CREDENTIAL_STORE_PROTECTION_ALGORITHM";
    public static final String ENV_CREDENTIAL_STORE_PROTECTION_PARAMS = "CREDENTIAL_STORE_PROTECTION_PARAMS";
    public static final String ENV_CREDENTIAL_STORE_PROTECTION = "CREDENTIAL_STORE_PROTECTION";
    public static final String ENV_CREDENTIAL_STORE_LOCATION = "CREDENTIAL_STORE_LOCATION";

    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM = "credential.store.protection.algorithm";
    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS = "credential.store.protection.params";
    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION = "credential.store.protection";
    public static final String PROPERTY_CREDENTIAL_STORE_LOCATION = "credential.store.location";

    public static final String DEFAULT_ALGORITHM = MaskedPassword.ALGORITHM_MASKED_SHA1_DES_EDE;
    public static final String DEFAULT_LOCATION = "credential.store.p12";

    // location of credential store
    private File location;
    // encrypted password used to protect the store and its entries
    private byte[] key;
    // value of env/system property with base64 encoded key
    private String encodedKey;
    // algorithm that's used to protect the password to credential store
    private String protectionAlgorithmName;
    // algorithm properties that can be used to recover the PBE password: ic, iv, salt
    private AlgorithmParameterSpec protectionParameterSpec;
    // value of env/system property with base64 encoded AlgorithmParameterSpec
    private String encodedProtectionParameterSpec;
    private byte[] protectionParameters;

    // a guard for loadCredentialStore()
    private boolean discoveryPerformed;

    /**
     * Write the configuration to {@code etc/system.properties} and actually set runtime (transient) system properties
     */
    public void persist() throws IOException {
        if (location == null || protectionAlgorithmName == null || protectionParameterSpec == null
                || key == null) {
            throw new IllegalArgumentException("Configuration is not complete");
        }

        // change AlgorithmParameterSpec into encoded form
        System.setProperty(PROPERTY_CREDENTIAL_STORE_LOCATION, location.getCanonicalPath());
        System.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM, protectionAlgorithmName);
        System.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS, getEncodedProtectionParameters());
        System.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION, getEncodedKey());

        Properties props = new Properties(new File(System.getProperty("karaf.etc"), "system.properties"));
        props.setProperty(PROPERTY_CREDENTIAL_STORE_LOCATION, location.getCanonicalPath());
        props.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM, protectionAlgorithmName);
        props.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS, getEncodedProtectionParameters());
        props.setProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION, getEncodedKey());
        props.save();

        // after configuration is persisted, it's like it was discovered, so we can get actual credential store
        // because configuration is complete
        discoveryPerformed = true;
    }

    /**
     * Attempts to discover {@link CredentialStore} configuration in system properties or in environmental
     * variables (in that order). After discovering, we can call {@link #loadCredentialStore()} which will
     * actually perform validation of the configuration.
     * @return
     */
    public boolean discover() {
        String protection = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION);
        String params = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS);
        String algorithm = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM, DEFAULT_ALGORITHM);
        String location = System.getProperty(PROPERTY_CREDENTIAL_STORE_LOCATION,
                new File(System.getProperty("karaf.etc"), DEFAULT_LOCATION).getAbsolutePath());

        if (protection == null || "".equals(protection.trim())) {
            protection = System.getenv(ENV_CREDENTIAL_STORE_PROTECTION);
        }
        if (params == null || "".equals(params.trim())) {
            params = System.getenv(ENV_CREDENTIAL_STORE_PROTECTION_PARAMS);
        }
        if (algorithm == null || "".equals(algorithm.trim())) {
            algorithm = System.getenv(ENV_CREDENTIAL_STORE_PROTECTION_ALGORITHM);
            if (algorithm == null || "".equals(algorithm.trim())) {
                algorithm = DEFAULT_ALGORITHM;
            }
        }
        if (location == null || "".equals(location.trim())) {
            location = System.getenv(ENV_CREDENTIAL_STORE_LOCATION);
            if (location == null || "".equals(location.trim())) {
                location = new File(System.getProperty("karaf.etc"), DEFAULT_LOCATION).getAbsolutePath();
            }
        }

        boolean discovered = false;
        if (protection != null && !"".equals(protection.trim()) && params != null && !"".equals(params.trim())) {
            setProtectionAlgorithmName(algorithm);
            setEncodedKey(protection);
            setLocation(new File(location));
            try {
                setEncodedProtectionParameterSpec(params);
                discovered = true;
            } catch (GeneralSecurityException | IOException e) {
                LOG.error("Can't decode protection parameters: " + e.getMessage(), e);
                discovered = false;
            }
        }

        discoveryPerformed = true;

        return discovered;
    }

    /**
     * Loads {@link CredentialStore} after successful discovery of configuration.
     * @return
     */
    public CredentialStore loadCredentialStore() throws IOException, GeneralSecurityException {
        // when starting Fuse, discover() has to be called, but credential-store:create command may also
        // prepare configuration so it is ready to generate CredentialStore
        if (!discoveryPerformed && key == null) {
            throw new IllegalStateException("Configuration was not loaded");
        }

        // org.wildfly.security.credential.store.CredentialStore
        CredentialStore cs = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE,
                Activator.getElytronProvider());
        Map<String, String> parameters = new HashMap<>();
        parameters.put("create", key == null ? "false" : "true");
        parameters.put("keyStoreType", "PKCS12");
        parameters.put("location", location.getCanonicalPath());

        // side effect (I know, I know, ...) of this call is setting protectionParameters to byte array
        getEncodedProtectionParameters();

        AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance(protectionAlgorithmName,
                Activator.getElytronProvider());
        algorithmParameters.init(protectionParameters);
        MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec
                = algorithmParameters.getParameterSpec(MaskedPasswordAlgorithmSpec.class);

        // specification of all that's needed to create MaskedPassword
        MaskedPasswordSpec spec = new MaskedPasswordSpec(maskedPasswordAlgorithmSpec.getInitialKeyMaterial(),
                maskedPasswordAlgorithmSpec.getIterationCount(),
                maskedPasswordAlgorithmSpec.getSalt(),
                key);

        PasswordFactory passwordFactory = PasswordFactory.getInstance(protectionAlgorithmName,
                Activator.getElytronProvider());
        MaskedPassword maskedPassword = passwordFactory.generatePassword(spec).castAs(MaskedPassword.class);

        // masked -> clear text
        PasswordFactory ctPasswordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR,
                Activator.getElytronProvider());
        ClearPasswordSpec clearPasswordSpec = passwordFactory.getKeySpec(maskedPassword, ClearPasswordSpec.class);
        Password ctPassword = ctPasswordFactory.generatePassword(clearPasswordSpec);

        // org.wildfly.security.credential.source.CredentialSource is needed to initialize CredentialStore
        CredentialSource source = IdentityCredentials.NONE.withCredential(new PasswordCredential(ctPassword));

        CredentialStore.CredentialSourceProtectionParameter csProtection
                = new CredentialStore.CredentialSourceProtectionParameter(source);
        // will create or load credential store
        cs.initialize(parameters, csProtection);
        cs.flush();

        return cs;
    }

    public File getLocation() {
        return location;
    }

    public void setLocation(File location) {
        this.location = location;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public String getProtectionAlgorithmName() {
        return protectionAlgorithmName;
    }

    public void setProtectionAlgorithmName(String protectionAlgorithmName) {
        this.protectionAlgorithmName = protectionAlgorithmName;
    }

    public AlgorithmParameterSpec getProtectionParameters() {
        return protectionParameterSpec;
    }

    public void setProtectionParameters(AlgorithmParameterSpec protectionParameterSpec) throws GeneralSecurityException, IOException {
        this.protectionParameterSpec = protectionParameterSpec;

        // immediately convert the spec to encoded form
        AlgorithmParameters params = AlgorithmParameters.getInstance(protectionAlgorithmName,
                Activator.getElytronProvider());
        // initialize parameters with decoded spec
        params.init(protectionParameterSpec);
        protectionParameters = params.getEncoded();
    }

    public String getEncodedProtectionParameterSpec() {
        return encodedProtectionParameterSpec;
    }

    public void setEncodedProtectionParameterSpec(String encodedProtectionParameterSpec) throws GeneralSecurityException, IOException {
        this.encodedProtectionParameterSpec = encodedProtectionParameterSpec;

        // immediately convert encoded for into spec
        AlgorithmParameters params = AlgorithmParameters.getInstance(protectionAlgorithmName,
                Activator.getElytronProvider());
        // initialize parameters with encoded spec
        protectionParameters = Activator.DECODER.decode(encodedProtectionParameterSpec);
        params.init(protectionParameters);
        protectionParameterSpec = params.getParameterSpec(MaskedPasswordAlgorithmSpec.class);
    }

    /**
     * Returns base64 encoded {@link java.security.AlgorithmParameters} for protection algorithm
     * @return
     */
    public String getEncodedProtectionParameters() {
        return Activator.ENCODER.encodeToString(protectionParameters);
    }

    /**
     * Returns base64 encoded key
     * @return
     */
    public String getEncodedKey() {
        return Activator.ENCODER.encodeToString(key);
    }

    public void setEncodedKey(String encodedKey) {
        this.encodedKey = encodedKey;

        // decode the key too
        key = Activator.DECODER.decode(encodedKey);
    }

    public boolean isDiscoveryPerformed() {
        return discoveryPerformed;
    }

}

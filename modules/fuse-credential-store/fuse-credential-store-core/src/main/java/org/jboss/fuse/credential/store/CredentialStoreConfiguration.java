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
package org.jboss.fuse.credential.store;

import java.io.File;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

import org.apache.felix.utils.properties.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;

/**
 * <p>Set of configuration parameters for {@link org.wildfly.security.credential.store.CredentialStore} specified
 * either via environment or system properties. This is kind of DTO object that allows to generate / handle credential
 * store, but doesn't generate it by itself.</p>
 * <p>String attributes are usually base64 decoded.</p>
 */
public class CredentialStoreConfiguration {

    public static final Logger LOG = LoggerFactory.getLogger(CredentialStoreConfiguration.class);

    public static final String ENV_CREDENTIAL_STORE_PROTECTION_ALGORITHM = "CREDENTIAL_STORE_PROTECTION_ALGORITHM";
    public static final String ENV_CREDENTIAL_STORE_PROTECTION_PARAMS = "CREDENTIAL_STORE_PROTECTION_PARAMS";
    public static final String ENV_CREDENTIAL_STORE_PROTECTION = "CREDENTIAL_STORE_PROTECTION";
    public static final String ENV_CREDENTIAL_STORE_LOCATION = "CREDENTIAL_STORE_LOCATION";

    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM = "credential.store.protection.algorithm";
    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS = "credential.store.protection.params";
    public static final String PROPERTY_CREDENTIAL_STORE_PROTECTION = "credential.store.protection";
    public static final String PROPERTY_CREDENTIAL_STORE_LOCATION = "credential.store.location";

    public static final String DEFAULT_ALGORITHM = org.wildfly.security.password.interfaces.MaskedPassword.ALGORITHM_MASKED_SHA1_DES_EDE;
    public static final String DEFAULT_LOCATION = "credential.store.p12";

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final CredentialStoreService credentialStoreService;

    // location of credential store
    private File location;
    // encrypted password used to protect the store and its entries
    private byte[] key;
    // algorithm that's used to protect the password to credential store
    private String protectionAlgorithmName;
    // algorithm properties that can be used to recover the PBE password: ic, iv, salt
    private AlgorithmParameterSpec protectionParameterSpec;
    // value of env/system property with base64 encoded AlgorithmParameterSpec
    private byte[] protectionParameters;

    private boolean discoveryPerformed;

    public CredentialStoreConfiguration(CredentialStoreService service) {
        this.credentialStoreService = service;
    }

    /**
     * Write the configuration to {@code etc/system.properties} and actually set runtime (transient) system properties
     */
    public void persistConfiguration() throws IOException {
        if (location == null || protectionAlgorithmName == null || protectionParameterSpec == null || key == null) {
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
     * Attempts to discover {@code CredentialStore} configuration in system properties or in environmental
     * variables (in that order). After discovering, we can validate the configuration and load credential store
     * @return
     */
    public boolean discover() {
        String defaultLocation = new File(System.getProperty("karaf.etc"), DEFAULT_LOCATION).getAbsolutePath();

        String protection = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION);
        String params = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_PARAMS);
        String algorithm = System.getProperty(PROPERTY_CREDENTIAL_STORE_PROTECTION_ALGORITHM, DEFAULT_ALGORITHM);
        String location = System.getProperty(PROPERTY_CREDENTIAL_STORE_LOCATION, defaultLocation);

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
                location = defaultLocation;
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
                credentialStoreService.getElytronProvider());
        // initialize parameters with decoded spec
        params.init(protectionParameterSpec);
        protectionParameters = params.getEncoded();
    }

    public void setEncodedProtectionParameterSpec(String encodedProtectionParameterSpec) throws GeneralSecurityException, IOException {
        // immediately convert encoded form into spec
        AlgorithmParameters params = AlgorithmParameters.getInstance(protectionAlgorithmName,
                credentialStoreService.getElytronProvider());
        // initialize parameters with encoded spec
        protectionParameters = DECODER.decode(encodedProtectionParameterSpec);
        params.init(protectionParameters);
        protectionParameterSpec = params.getParameterSpec(MaskedPasswordAlgorithmSpec.class);
    }

    /**
     * Returns base64 encoded {@link java.security.AlgorithmParameters} for protection algorithm
     * @return
     */
    public String getEncodedProtectionParameters() {
        return ENCODER.encodeToString(protectionParameters);
    }

    /**
     * Returns base64 encoded key
     * @return
     */
    public String getEncodedKey() {
        return ENCODER.encodeToString(key);
    }

    public void setEncodedKey(String encodedKey) {
        // decode the key immediately
        key = DECODER.decode(encodedKey);
    }

    public boolean isDiscoveryPerformed() {
        return discoveryPerformed;
    }

    /**
     * Using raw data, configures related, elytron-specific {@link AlgorithmParameterSpec} and secret data.
     * @param algorithm
     * @param password
     * @param ivc
     * @param iterationCount
     * @param salt
     */
    public void configureMaskedPasswordDetails(String algorithm, char[] password, char[] ivc, int iterationCount, byte[] salt) throws Exception {
        CredentialStoreService.MaskedPasswordData maskedPassword = credentialStoreService.generateMaskedPassword(algorithm, password, ivc, iterationCount, salt);
        setProtectionParameters(maskedPassword.getSpec());
        setKey(maskedPassword.getSecret());
    }

}

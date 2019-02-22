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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Set;

/**
 * Service to access Elytron based credential store.
 */
public interface CredentialStoreService {

    /**
     * Ensures that configuration is read an applied and credential store is actually available.
     * Validation is performed for karaf commands, so it operates on stdout/stderr.
     * @return
     */
    boolean validate();

    /**
     * Checks if credential store is already configured
     * @return
     */
    boolean available();

    /**
     * <p>When generating new {@code CredentialStore} we can set it effective using this method.
     * When {@code credential-store:create} command is called, it can replace existing store. This method is also
     * called when starting the bundle, after discovering the configuration.</p>
     * <p>Some additional processing will happen, as we have to register some OSGi hooks and wrappers.</p>
     * @param config
     */
    void useCredentialStoreFromConfig(CredentialStoreConfiguration config) throws IOException, GeneralSecurityException;

    /**
     * Returns aliases available in {@code CredentialStore}
     * @return
     */
    Set<String> aliases() throws GeneralSecurityException;

    /**
     * Retrieves password for alias from {@code CredentialStore}
     * @param alias
     * @return password for alias or {@code null} if not found
     */
    String retrievePassword(String alias) throws GeneralSecurityException;

    /**
     * Checks whether alias exists in {@code CredentialStore}
     * @param alias
     * @return
     */
    boolean aliasExists(String alias) throws GeneralSecurityException;

    /**
     * Adds new alias + secret to {@code CredentialStore}
     * @param alias
     * @param secret
     */
    void addAlias(String alias, String secret) throws GeneralSecurityException;

    /**
     * Removes existing alias from {@code CredentialStore}
     * @param alias
     */
    void removeAlias(String alias) throws GeneralSecurityException;

    /**
     * Returns instance of {@link Provider} for Elytron provider - we should never use it declaratively, as it's
     * private packaged.
     * @return
     */
    Provider getElytronProvider();

    /**
     * Generates {@link MaskedPasswordData} to protect credential store
     * @param algorithm
     * @param password
     * @param ivc
     * @param iterationCount
     * @param salt
     * @return
     */
    MaskedPasswordData generateMaskedPassword(String algorithm, char[] password, char[] ivc, int iterationCount, byte[] salt)
            throws Exception;

    /**
     * Called to clean up all service registrations and close all the trackers
     * @param restoreOriginalPm whether to register original {@link org.apache.felix.cm.PersistenceManager}
     */
    void cleanup(boolean restoreOriginalPm);

    /**
     * Wrapper for secret data and spec.
     */
    interface MaskedPasswordData {

        byte[] getSecret();

        AlgorithmParameterSpec getSpec();
    }

}

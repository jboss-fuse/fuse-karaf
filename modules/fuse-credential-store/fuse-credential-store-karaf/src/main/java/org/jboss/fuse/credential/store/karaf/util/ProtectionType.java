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
import java.security.GeneralSecurityException;
import java.util.Map;

import org.wildfly.security.credential.source.CredentialSource;

/**
 * All supported protection types that are used to protect the Credential store.
 */
public enum ProtectionType {

    masked(new MaskedPasswordHelper());

    /**
     * Contract a specific supported protectiont type must implement.
     */
    interface CredentialSourceHandler {
        String CREDENTIAL_STORE_PROTECTION = "CREDENTIAL_STORE_PROTECTION";

        String CREDENTIAL_STORE_PROTECTION_ALGORITHM = "CREDENTIAL_STORE_PROTECTION_ALGORITHM";

        String CREDENTIAL_STORE_PROTECTION_PARAMS = "CREDENTIAL_STORE_PROTECTION_PARAMS";

        String CREDENTIAL_STORE_PROTECTION_PROVIDER = "CREDENTIAL_STORE_PROTECTION_PROVIDER";

        Map<String, String> createConfiguration(Map<String, String> attributes)
                throws GeneralSecurityException, IOException;

        CredentialSource createCredentialSource(Map<String, String> configuration)
                throws GeneralSecurityException, IOException;

        String[] getOptionValuesFor(String option);

        String[] getSupportedOptions();
    }

    private final CredentialSourceHandler handler;

    ProtectionType(final CredentialSourceHandler handler) {
        this.handler = handler;

    }

    public Map<String, String> createConfiguration(final Map<String, String> attributes)
            throws GeneralSecurityException, IOException {
        return handler.createConfiguration(attributes);
    }

    public CredentialSource createCredentialSource(final Map<String, String> configuration)
            throws GeneralSecurityException, IOException {
        return handler.createCredentialSource(configuration);
    }

    public String[] getOptionValuesFor(final String option) {
        return handler.getOptionValuesFor(option);
    }

    public String[] getSupportedOptions() {
        return handler.getSupportedOptions();
    }

}

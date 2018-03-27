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
import java.util.HashMap;
import java.util.Map;

import org.jboss.fuse.credential.store.karaf.util.ProtectionType.CredentialSourceHandler;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtectionTypeTest {

    @Test
    public void shouldCreateMaskedPasswordCredentialSourceFromConfiguration()
            throws IOException, GeneralSecurityException {

        final Map<String, String> configuration = new HashMap<>();

        configuration.put("CREDENTIAL_STORE_PROTECTION_ALGORITHM", MaskedPassword.ALGORITHM_MASKED_MD5_DES);
        configuration.put("CREDENTIAL_STORE_PROTECTION_PARAMS",
                "MDkEKXNvbWVhcmJpdHJhcnljcmF6eXN0cmluZ3RoYXRkb2Vzbm90bWF0dGVyAgID6AQIHmrp8uDnGLE=");
        configuration.put("CREDENTIAL_STORE_PROTECTION", "mC/60tWnla4bmFn2e5Z8U3CZnjsG9Pvc");

        final CredentialSource credentialSource = ProtectionType.masked.createCredentialSource(configuration);

        assertThat(credentialSource).isNotNull();

        final PasswordCredential credential = credentialSource.getCredential(PasswordCredential.class);

        final Password password = credential.getPassword();

        final PasswordFactory clearPasswordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR,
                new WildFlyElytronProvider());

        final ClearPasswordSpec clearPasswordSpec = clearPasswordFactory.getKeySpec(password, ClearPasswordSpec.class);

        assertThat(new String(clearPasswordSpec.getEncodedPassword())).isEqualTo("my deep dark secret");
    }

    @Test
    public void shouldCreateMaskedPasswordCredentialSourceWithCustomConfiguration()
            throws GeneralSecurityException, IOException {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("password", "my deep dark secret");
        attributes.put("algorithm", MaskedPassword.ALGORITHM_MASKED_MD5_DES);

        final Map<String, String> configuration = ProtectionType.masked.createConfiguration(attributes);

        assertThat(configuration).containsOnlyKeys(CredentialSourceHandler.CREDENTIAL_STORE_PROTECTION_ALGORITHM,
                CredentialSourceHandler.CREDENTIAL_STORE_PROTECTION,
                CredentialSourceHandler.CREDENTIAL_STORE_PROTECTION_PARAMS);
    }

    @Test
    public void shouldCreateMaskedPasswordCredentialSourceWithDefaultConfiguration()
            throws GeneralSecurityException, IOException {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("password", "my deep dark secret");

        final Map<String, String> configuration = ProtectionType.masked.createConfiguration(attributes);

        assertThat(configuration).containsOnlyKeys(CredentialSourceHandler.CREDENTIAL_STORE_PROTECTION,
                CredentialSourceHandler.CREDENTIAL_STORE_PROTECTION_PARAMS);
    }
}

/**
 *  Copyright 2005-2017 Red Hat, Inc.
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

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.credential.store.karaf.util.CredentialStoreHelper;
import org.jboss.fuse.credential.store.karaf.util.ProviderHelper;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;

/**
 * Places a secret value in the Credential store under the specified alias configured by the environment variables.
 */
@Command(scope = "credential-store", name = "store", description = "Store secret in the credential store")
@Service
public class StoreInCredentialStore implements Action {

    @Option(name = "-a", aliases = {"--alias"}, description = "Alias for the secret", required = true,
            multiValued = false)
    private String alias;

    @Option(name = "-s", aliases = {"--secret"}, description = "Secret value", required = true, multiValued = false)
    private String secret;

    @Override
    public Object execute() throws Exception {
        final CredentialStore credentialStore = CredentialStoreHelper.credentialStoreFromEnvironment();
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear",
                ProviderHelper.provider(ProviderHelper.WILDFLY_PROVIDER));
        final Password password = passwordFactory.generatePassword(new ClearPasswordSpec(secret.toCharArray()));

        credentialStore.store(alias, new PasswordCredential(password));

        credentialStore.flush();

        System.out.println("Value stored in the credential store to reference it use: "
            + CredentialStoreHelper.referenceForAlias(alias));

        return null;
    }

}

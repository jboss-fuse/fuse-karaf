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

import java.util.Locale;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.credential.store.CredentialStoreHelper;

/**
 * Places a secret value in the Credential store under the specified alias configured by the environment variables.
 */
@Command(scope = "credential-store", name = "store", description = "Store secret in the credential store")
@Service
public class StoreInCredentialStore extends AbstractCredentialStoreCommand {

    @Argument(index = 0, required = true, description = "Alias for credential Store entry (case insensitive)")
    String alias;

    @Argument(index = 1, required = false, description = "Secret value to put into Credential Store. If not specified, secret value will be read from standard input.")
    String secret;

    @Override
    public Object execute() throws Exception {
        if (!credentialStoreService.validate()) {
            return null;
        }

        alias = alias.toLowerCase(Locale.ROOT);

        if (credentialStoreService.aliasExists(alias)) {
            System.out.println("Entry with alias \"" + alias + "\" already exists in credential store.");
            return null;
        }

        if (secret == null || "".equals(secret)) {
            String secret1 = session.readLine("Secret value to store: ", '*');
            String secret2 = session.readLine("Secret value to store (repeat): ", '*');
            if (secret1 == null || secret2 == null || "".equals(secret1.trim()) || "".equals(secret2)) {
                System.err.println("Please specify secret value to store - either as argument or from standard input.");
                return null;
            }
            if (!secret1.equals(secret2)) {
                System.err.println("Secret values do not match.");
                return null;
            }
            secret = secret1;
        }

        credentialStoreService.addAlias(alias, secret);

        System.out.println("Value stored in the credential store. To reference it use: "
            + CredentialStoreHelper.referenceForAlias(alias));

        return null;
    }

}

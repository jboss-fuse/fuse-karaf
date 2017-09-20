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
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.store.CredentialStore;

/**
 * Removes a value from the Credential store configured by the environment variables.
 */
@Command(scope = "credential-store", name = "remove", description = "Remove secret from the credential store")
@Service
public class RemoveFromCredentialStore implements Action {

    @Option(name = "-a", aliases = {"--alias"}, description = "Alias of the secret to remove", required = true,
            multiValued = false)
    private String alias;

    @Override
    public Object execute() throws Exception {
        final CredentialStore credentialStore = CredentialStoreHelper.credentialStoreFromEnvironment();

        credentialStore.remove(alias, Credential.class);

        credentialStore.flush();

        return null;
    }

}

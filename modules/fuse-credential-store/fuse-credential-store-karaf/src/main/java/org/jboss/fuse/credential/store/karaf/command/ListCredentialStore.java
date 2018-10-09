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

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.Row;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jboss.fuse.credential.store.karaf.Activator;
import org.jboss.fuse.credential.store.karaf.util.CredentialStoreHelper;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Lists the content of the Credential store configured by the environment variables.
 */
@Command(scope = "credential-store", name = "list", description = "List the content of the credential store")
@Service
public class ListCredentialStore extends AbstractCredentialStoreCommand {

    @Option(name = "-x", aliases = { "--show-secrets" }, description = "Additionally shows actual decrypted secret values")
    boolean secrets;

    @Override
    public Object execute() throws Exception {
        if (!validate()) {
            return null;
        }

        final ShellTable table = new ShellTable();
        table.column(new Col("Alias"));
        table.column(new Col("Reference"));
        if (secrets) {
            table.column(new Col("Secret value"));
        }

        final CredentialStore credentialStore = Activator.credentialStore;

        for (final String alias : credentialStore.getAliases()) {
            Row row = table.addRow();
            row.addContent(alias, CredentialStoreHelper.referenceForAlias(alias));
            if (secrets) {
                PasswordCredential credential = credentialStore.retrieve(alias, PasswordCredential.class);
                row.addContent(credential == null ? "<null>" : new String(credential.getPassword().castAs(ClearPassword.class).getPassword()));
            }
        }

        table.print(System.out);

        return null;
    }

}

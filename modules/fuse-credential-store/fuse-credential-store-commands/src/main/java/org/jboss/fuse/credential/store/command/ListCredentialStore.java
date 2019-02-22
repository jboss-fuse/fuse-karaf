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

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.Row;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jboss.fuse.credential.store.CredentialStoreHelper;

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
        if (!credentialStoreService.validate()) {
            return null;
        }

        final ShellTable table = new ShellTable();
        table.column(new Col("Alias"));
        table.column(new Col("Reference"));
        if (secrets) {
            table.column(new Col("Secret value"));
        }

        for (final String alias : credentialStoreService.aliases()) {
            Row row = table.addRow();
            row.addContent(alias, CredentialStoreHelper.referenceForAlias(alias));
            if (secrets) {
                String password = credentialStoreService.retrievePassword(alias);
                row.addContent(password == null ? "<null>" : password);
            }
        }

        table.print(System.out);

        return null;
    }


}

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

import java.security.Provider.Service;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.wildfly.security.credential.store.CredentialStore;

/**
 * A {@link Completer} that auto completes Credential store algorithms. Looks for all implementations of
 * {@link CredentialStore} in the providers registered at {@link Security} and auto completes with the
 * {@link Service#getAlgorithm()}.
 */
@org.apache.karaf.shell.api.action.lifecycle.Service
public class CredentialStoreAlgorithmCompletionSupport implements Completer {

    private final String credentialStoreType = CredentialStore.class.getSimpleName();

    @Override
    public int complete(final Session session, final CommandLine commandLine, final List<String> candidates) {
        final String[] algorithms = Arrays.stream(Security.getProviders()).flatMap(p -> p.getServices().stream())
                .filter(s -> credentialStoreType.equals(s.getType())).map(Service::getAlgorithm).toArray(String[]::new);

        return new StringsCompleter(algorithms).complete(session, commandLine, candidates);
    }

}

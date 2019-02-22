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
package org.jboss.fuse.jasypt.commands.completers;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.jasypt.registry.AlgorithmRegistry;

@Service
public class JasyptDigestAlgorithmsCompletionSupport implements Completer {

    private static Set<String> supportedAlgorithms = new TreeSet<>();

    public JasyptDigestAlgorithmsCompletionSupport() {
        final Set digestAlgos = AlgorithmRegistry.getAllDigestAlgorithms();

        for (Object algo : digestAlgos) {
            supportedAlgorithms.add((String) algo);
        }
    }

    public static boolean isSupported(String algorithm) {
        return supportedAlgorithms.contains(algorithm);
    }

    @Override
    public int complete(final Session session, final CommandLine commandLine, final List<String> candidates) {
        return new StringsCompleter(supportedAlgorithms).complete(session, commandLine, candidates);
    }

}

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
package org.jboss.fuse.jasypt.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jasypt.commons.CommonUtils;
import org.jasypt.digest.StandardByteDigester;
import org.jasypt.intf.service.JasyptStatelessService;
import org.jboss.fuse.jasypt.commands.completers.JasyptDigestAlgorithmsCompletionSupport;

/**
 * Lists algorithms available for jasypt encryption
 */
@Command(scope = "jasypt", name = "digest", description = "Digests data using different algorithms and configuration")
@Service
public class Digest implements Action {

    @Reference
    protected Session session;

    @Option(name = "-a", aliases = { "--algorithm" }, description = "Digest algorithm to use.")
    @Completion(caseSensitive = true, value = JasyptDigestAlgorithmsCompletionSupport.class)
    String algorithm = StandardByteDigester.DEFAULT_ALGORITHM;

    @Option(name = "-s", aliases = { "--salt-size" }, description = "Size of salt to be applied.")
    int saltSize = StandardByteDigester.DEFAULT_SALT_SIZE_BYTES;

    @Option(name = "-i", aliases = { "--iterations" }, description = "Number of hash iterations to be applied.")
    int iterations = StandardByteDigester.DEFAULT_ITERATIONS;

    @Option(name = "-h", aliases = { "--hex" }, description = "Use HEX output format. By default, Base64 is used.")
    boolean hex;

    @Argument(description = "Input data to digest. If not specified, the value will be read from standard input.", required = false)
    String input;

    @Override
    public Object execute() throws Exception {

        if (input == null || "".equals(input)) {
            String input1 = session.readLine("Input data to digest: ", '*');
            String input2 = session.readLine("Input data to digest (repeat): ", '*');
            if (input1 == null || input2 == null || "".equals(input1.trim()) || "".equals(input2)) {
                System.err.println("Please specify input data to digest - either as argument or from standard input.");
                return null;
            }
            if (!input1.equals(input2)) {
                System.err.println("Input values do not match.");
                return null;
            }
            input = input1;
        }

        JasyptStatelessService service = new JasyptStatelessService();
        String digest = service.digest(
                input,
                algorithm, null, null,
                Integer.toString(iterations), null, null,
                Integer.toString(saltSize), null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                hex ? CommonUtils.STRING_OUTPUT_TYPE_HEXADECIMAL : CommonUtils.STRING_OUTPUT_TYPE_BASE64, null, null,
                null, null, null,
                null, null, null
        );

        System.out.println("Algorithm used: " + algorithm);
        System.out.println("Digest value: " + digest);

        return null;
    }

}

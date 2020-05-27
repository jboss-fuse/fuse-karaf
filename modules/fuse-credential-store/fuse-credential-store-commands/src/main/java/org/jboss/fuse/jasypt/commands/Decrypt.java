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
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;
import org.jasypt.intf.service.JasyptStatelessService;
import org.jasypt.iv.RandomIvGenerator;
import org.jboss.fuse.jasypt.commands.completers.JasyptPbeAlgorithmsCompletionSupport;

import static org.jboss.fuse.jasypt.commands.Helpers.isIVNeeded;

/**
 * Lists algorithms available for jasypt encryption
 */
@Command(scope = "jasypt", name = "decrypt", description = "Decrypts previously encrypted data")
@Service
public class Decrypt implements Action {

    @Reference
    protected Session session;

    @Option(name = "-a", aliases = { "--algorithm" }, description = "PBE algorithm to use.")
    @Completion(caseSensitive = true, value = JasyptPbeAlgorithmsCompletionSupport.class)
    String algorithm = StandardPBEByteEncryptor.DEFAULT_ALGORITHM;

    @Option(name = "-i", aliases = { "--iterations" }, description = "Number of key obtention iterations to use.")
    int iterations = StandardPBEByteEncryptor.DEFAULT_KEY_OBTENTION_ITERATIONS;

    @Option(name = "-h", aliases = { "--hex" }, description = "Use HEX output format. By default, Base64 is used.")
    boolean hex;

    @Option(name = "-w", aliases = { "--password-property" }, description = "Specify password as environmental variable or system property (checked in this order)")
    String passwordProperty;

    @Option(name = "-W", aliases = { "--password" }, description = "Specify password to derive PBE key (will be visible in history)."
            + " If neither `-w` nor `-W` options are specified, password will be read from standard input.")
    String password;

    @Option(name = "-I", aliases = { "--use-iv-generator" }, description = "Use RandomIvGenerator for decryption. Default is false except for the following algorithms: "
            + "PBEWITHHMACSHA384ANDAES_128, PBEWITHHMACSHA224ANDAES_256, PBEWITHHMACSHA512ANDAES_256, PBEWITHHMACSHA256ANDAES_128, "
            + "PBEWITHHMACSHA256ANDAES_256, PBEWITHHMACSHA1ANDAES_128, PBEWITHHMACSHA384ANDAES_256, PBEWITHHMACSHA1ANDAES_256, PBEWITHHMACSHA224ANDAES_128, PBEWITHHMACSHA512ANDAES_128 ")
    boolean useIVGenerator = false;

    @Argument(index = 0, description = "Input data to decrypt. If no data is specified, it'll be read from standard input.", required = false)
    String input;

    @Override
    public Object execute() throws Exception {
        this.useIVGenerator = this.useIVGenerator || isIVNeeded(algorithm);
        String password = Helpers.getPassword(passwordProperty, this.password, session, false);
        if (password == null) {
            return null;
        }

        if (input == null || "".equals(input)) {
            String input1 = session.readLine("Data to decrypt: ", '*');
            if (input1 == null || "".equals(input1.trim())) {
                System.err.println("Please specify data to decrypt.");
                return null;
            }
            input = input1;
        }

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            JasyptStatelessService service = new JasyptStatelessService();

            String clear = service.decrypt(
                    input,
                    password, null, null,
                    algorithm, null, null,
                    Integer.toString(iterations), null, null,
                    null, null, null,
                    null, null, null,
                    null, null, null,
                    hex ? CommonUtils.STRING_OUTPUT_TYPE_HEXADECIMAL : CommonUtils.STRING_OUTPUT_TYPE_BASE64, null, null,
                    useIVGenerator ? RandomIvGenerator.class.getName() : null,
                    null, null);

            System.out.println("Algorithm used: " + algorithm);
            System.out.println("Decrypted data: " + clear);
        } finally {
            if (tccl != null) {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }

        return null;
    }

}

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
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.intf.service.JasyptStatelessService;
import org.jboss.fuse.jasypt.commands.completers.JasyptDigestAlgorithmsCompletionSupport;
import org.jboss.fuse.jasypt.commands.completers.JasyptPbeAlgorithmsCompletionSupport;

/**
 * Lists algorithms available for jasypt encryption
 */
@Command(scope = "jasypt", name = "encrypt", description = "Encrypts data using different algorithms and configuration")
@Service
public class Encrypt implements Action {

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

    @Option(name = "-W", aliases = { "--password" }, description = "Specify password to derive PBE key (will be visible in history). If neither `-w` nor `-W` options are specified, password will be read from standard input.")
    String password;

    @Argument(index = 0, description = "Input data to encrypt. If no data is specified, it'll be read from standard input.", required = false)
    String input;

    @Override
    public Object execute() throws Exception {
        String password = Helpers.getPassword(passwordProperty, this.password, session, false);
        if (password == null) {
            return null;
        }

        if (input == null || "".equals(input)) {
            String input1 = session.readLine("Data to encrypt: ", '*');
            String input2 = session.readLine("Data to encrypt (repeat): ", '*');
            if (input1 == null || input2 == null || "".equals(input1.trim()) || "".equals(input2)) {
                System.err.println("Please specify data to encrypt.");
                return null;
            }
            if (!input1.equals(input2)) {
                System.err.println("Values do not match.");
                return null;
            }
            input = input1;
        }

        JasyptStatelessService service = new JasyptStatelessService();
        String secret = service.encrypt(
                input,
                password, null, null,
                algorithm, null, null,
                Integer.toString(iterations), null, null,
                null, null, null,
                null, null, null,
                null, null, null,
                hex ? CommonUtils.STRING_OUTPUT_TYPE_HEXADECIMAL : CommonUtils.STRING_OUTPUT_TYPE_BASE64, null, null);

        System.out.println("Algorithm used: " + algorithm);
        System.out.println("Encrypted data: " + secret);

        return null;
    }

}

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

    @Argument(index = 0, description = "Password to derive PBE key.", required = true)
    String password;

    @Argument(index = 1, description = "Input data to encrypt.", required = true)
    String input;

    @Override
    public Object execute() throws Exception {

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

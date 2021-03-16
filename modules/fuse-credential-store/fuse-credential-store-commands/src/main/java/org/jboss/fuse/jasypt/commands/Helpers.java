/*
 *  Copyright 2005-2019 Red Hat, Inc.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.karaf.shell.api.console.Session;

public final class Helpers {

     static final Set<String> ALGORITHMS_THAT_REQUIRE_IV = new HashSet<>(
            Arrays.asList(
                    "PBEWITHHMACSHA1ANDAES_128",
                    "PBEWITHHMACSHA1ANDAES_256",
                    "PBEWITHHMACSHA224ANDAES_128",
                    "PBEWITHHMACSHA224ANDAES_256",
                    "PBEWITHHMACSHA256ANDAES_128",
                    "PBEWITHHMACSHA256ANDAES_256",
                    "PBEWITHHMACSHA384ANDAES_128",
                    "PBEWITHHMACSHA384ANDAES_256",
                    "PBEWITHHMACSHA512ANDAES_128",
                    "PBEWITHHMACSHA512ANDAES_256",
                    "MASKED-HMAC-SHA1-AES-128",
                    "MASKED-HMAC-SHA224-AES-128",
                    "MASKED-HMAC-SHA256-AES-128",
                    "MASKED-HMAC-SHA384-AES-128",
                    "MASKED-HMAC-SHA512-AES-128",
                    "MASKED-HMAC-SHA1-AES-256",
                    "MASKED-HMAC-SHA224-AES-256",
                    "MASKED-HMAC-SHA256-AES-256",
                    "MASKED-HMAC-SHA384-AES-256",
                    "MASKED-HMAC-SHA512-AES-256"
            )
    );

    private Helpers() { }

    /**
     * Helper for commands to get secret value from different sources.
     *
     * @param passwordProperty
     * @param passwordValue
     * @param session
     * @param repeat
     * @return
     * @throws IOException
     */
    public static String getPassword(String passwordProperty, String passwordValue, Session session, boolean repeat) throws IOException {
        String password = passwordValue;
        if (passwordProperty != null && !"".equals(passwordProperty) && passwordValue != null && !"".equals(passwordValue)) {
            System.err.println("Password is specified both as argument as property. Please choose one option.");
            return null;
        }

        if (passwordProperty != null) {
            if (System.getenv(passwordProperty) != null) {
                password = System.getenv(passwordProperty);
            } else if (System.getProperty(passwordProperty) != null) {
                password = System.getProperty(passwordProperty);
            }
            if (password == null) {
                System.err.println("There's no environmental variable or system property \"" + passwordProperty + "\"");
                return null;
            }
        } else if (passwordValue == null || "".equals(passwordValue)) {
            String password1 = session.readLine("Master password: ", '*');
            String password2 = null;
            if (repeat) {
                password2 = session.readLine("Master password (repeat): ", '*');
            } else {
                password2 = password1;
            }
            if (password1 == null || password2 == null || "".equals(password1.trim()) || "".equals(password2)) {
                System.err.println("Please specify master password.");
                return null;
            }
            if (!password1.equals(password2)) {
                System.err.println("Passwords do not match.");
                return null;
            }
            password = password1;
        }
        return password;
    }

    public static boolean isIVNeeded(String algorithm) {
        return ALGORITHMS_THAT_REQUIRE_IV.contains(algorithm.toUpperCase());
    }
}

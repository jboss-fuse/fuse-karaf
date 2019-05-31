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

import org.apache.karaf.shell.api.console.Session;

public class Helpers {

    /**
     * Helper for commands to get secret value from different sources.
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

}

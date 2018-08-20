/*
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
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.console.Session;
import org.jboss.fuse.credential.store.karaf.Activator;

public abstract class AbstractCredentialStoreCommand implements Action {

    @Reference
    protected Session session;

    /**
     * Subclasses may ensure the configuration is valid
     * @return
     */
    protected boolean validate() {
        if (Activator.credentialStore == null) {
            if (Activator.config != null) {
                if (!Activator.config.isDiscoveryPerformed()) {
                    System.out.println("Configuration was not loaded and Credential Store is not available");
                    return false;
                }
                if (!Activator.config.discover()) {
                    System.out.println("Configuration was not found and Credential Store is not available.");
                    return false;
                }
                System.out.println("Credential Store was not loaded properly. Please consult logs for more details.");
                return false;
            }
        }

        return true;
    }

}

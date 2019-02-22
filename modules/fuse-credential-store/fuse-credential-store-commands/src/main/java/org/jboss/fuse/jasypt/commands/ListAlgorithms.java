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

import java.util.Set;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.jasypt.registry.AlgorithmRegistry;

/**
 * Lists algorithms available for jasypt encryption
 */
@Command(scope = "jasypt", name = "list-algorithms", description = "Lists algorithms available for Jasypt encryption")
@Service
public class ListAlgorithms implements Action {

    @Reference
    protected Session session;

    @Override
    public Object execute() throws Exception {

        final Set digestAlgos = AlgorithmRegistry.getAllDigestAlgorithms();
        final Set pbeAlgos = AlgorithmRegistry.getAllPBEAlgorithms();

        System.out.println("DIGEST ALGORITHMS:");
        for (Object algo : digestAlgos) {
            System.out.println(" - " + algo);
        }

        System.out.println("\nPBE ALGORITHMS:");
        for (Object algo : pbeAlgos) {
            System.out.println(" - " + algo);
        }
        System.out.println();

        return null;
    }

}

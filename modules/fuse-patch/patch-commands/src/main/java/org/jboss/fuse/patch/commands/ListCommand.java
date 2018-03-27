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
package org.jboss.fuse.patch.commands;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.patch.PatchService;

@Service
@Command(scope = "patch", name = "list", description = "Display known patches")
public class ListCommand extends PatchCommandSupport {

    @Option(name = "--bundles", description = "Display the list of bundles for each patch")
    boolean bundles;

    @Option(name = "--short", description = "Display less information")
    boolean less;

    @Override
    protected void doExecute(PatchService service) throws Exception {
        display(service.getPatches(), bundles, less);
    }

}

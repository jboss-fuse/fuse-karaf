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

import java.util.stream.Collectors;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.commands.completers.UninstallPatchCompleter;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchDetailsRequest;
import org.jboss.fuse.patch.management.PatchException;
import org.jboss.fuse.patch.management.PatchManagement;
import org.osgi.framework.BundleContext;

@Service
@Command(scope = "patch", name = "delete", description = "Deletes added, but not installed patch")
public class DeleteCommand extends PatchCommandSupport {

    @Reference
    BundleContext bundleContext;

    @Argument(name = "PATCH", description = "name of the patch to delete", required = true, multiValued = false)
    @Completion(UninstallPatchCompleter.class)
    String patchId;

    @Reference
    private PatchManagement patchManagement;

    @Override
    public Object execute() throws Exception {
        // patch:delete doesn't call org.jboss.fuse.patch.PatchService.autoDeployPatches()
        doExecute(service);
        return null;
    }

    @Override
    protected void doExecute(PatchService service) throws Exception {
        Patch patch = patchManagement.loadPatch(new PatchDetailsRequest(patchId));

        if (patch == null) {
            throw new PatchException("Patch '" + patchId + "' not found");
        }

        if (patch.getResult() != null && patch.getResult().getKarafBases().size() > 0) {
            throw new PatchException("Patch '" + patchId + "' can't be deleted, as it's installed in these containers: "
                    + patch.getResult().getKarafBases().stream()
                    .map(kb -> kb.contains("|") ? kb.split("\\s*\\|\\s*")[0] : kb)
                    .collect(Collectors.joining(", ")));
        }

        patchManagement.delete(patch);
        service.undeploy(patch);
        System.out.println("Patch '" + patchId + "' was successfully deleted");
    }

}

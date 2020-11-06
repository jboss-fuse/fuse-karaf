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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.commands.completers.PatchCompleter;
import org.jboss.fuse.patch.management.CVE;
import org.jboss.fuse.patch.management.ManagedPatch;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchDetailsRequest;
import org.jboss.fuse.patch.management.PatchException;
import org.jboss.fuse.patch.management.PatchManagement;

@Service
@Command(scope = "patch", name = "show", description = "Display information about added/installed patch")
public class ShowCommand extends PatchCommandSupport {

    @Argument(name = "PATCH", description = "name of the patch to display", required = true, multiValued = false)
    @Completion(PatchCompleter.class)
    String patchId;

    @Option(name = "--bundles", description = "Display the list of bundles for the patch")
    boolean bundles;

    @Option(name = "--files", description = "Display list of files added/modified/removed in a patch (without the files in ${karaf.home}/system)")
    boolean files;

    @Option(name = "--diff", description = "Display unified diff of files modified in a patch (without the files in ${karaf.home}/system)")
    boolean diff;

    @Reference
    private PatchManagement patchManagement;

    @Override
    protected void doExecute(PatchService service) throws Exception {
        Patch patch = patchManagement.loadPatch(new PatchDetailsRequest(patchId, bundles, files, diff));

        if (patch == null) {
            throw new PatchException("Patch '" + patchId + "' not found");
        }
        System.out.printf("Patch ID: %s%n", patch.getPatchData().getId());
        if (patch.getManagedPatch() != null) {
            System.out.printf("Patch Commit ID: %s%n", patch.getManagedPatch().getCommitId());
        }
        System.out.printf("#### %d CVE fix%s%s%n", patch.getPatchData().getCves().size(),
                patch.getPatchData().getCves().size() == 1 ? "" : "es",
                patch.getPatchData().getCves().size() == 0 ? "" : ":");
        for (CVE cve : patch.getPatchData().getCves()) {
            System.out.printf(" - %s: %s%n", cve.getId(), cve.getDescription());
            if (cve.getBzLink() != null && !"".equals(cve.getBzLink().trim())) {
                System.out.printf("   Bugzilla link: %s%n", cve.getBzLink());
            }
            if (cve.getCveLink() != null && !"".equals(cve.getCveLink().trim())) {
                System.out.printf("   CVE link: %s%n", cve.getCveLink());
            }
        }
        if (bundles && patch.getPatchData().getBundles() != null) {
            System.out.printf("#### %d Bundles%s%n", patch.getPatchData().getBundles().size(), patch.getPatchData().getBundles().size() == 0 ? "" : ":");
            iterate(patch.getPatchData().getBundles());
        }
        if (files) {
            ManagedPatch details = patch.getManagedPatch();
            System.out.printf("#### %d Files added%s%n", details.getFilesAdded().size(), details.getFilesAdded().size() == 0 ? "" : ":");
            iterate(details.getFilesAdded());
            System.out.printf("#### %d Files modified%s%n", details.getFilesModified().size(), details.getFilesModified().size() == 0 ? "" : ":");
            iterate(details.getFilesModified());
            System.out.printf("#### %d Files removed%s%n", details.getFilesRemoved().size(), details.getFilesRemoved().size() == 0 ? "" : ":");
            iterate(details.getFilesRemoved());
        }
        if (diff) {
            System.out.println("#### Patch changes:\n" + patch.getManagedPatch().getUnifiedDiff());
        }
    }

    /**
     * List file names
     * @param fileNames
     */
    private void iterate(java.util.List<String> fileNames) {
        for (String name : fileNames) {
            System.out.printf(" - %s%n", name);
        }
    }

}

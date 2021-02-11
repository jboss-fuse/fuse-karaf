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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.management.BundleUpdate;
import org.jboss.fuse.patch.management.CVE;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchException;
import org.jboss.fuse.patch.management.PatchResult;

import static org.jboss.fuse.patch.management.Utils.stripSymbolicName;

public abstract class PatchCommandSupport implements Action {

    @Reference
    protected PatchService service;

    @Override
    public Object execute() throws Exception {
        service.autoDeployPatches();
        doExecute(service);
        return null;
    }

    protected abstract void doExecute(PatchService service) throws Exception;

    protected void display(PatchResult result) {
        int l1 = "[name]".length();
        int l2 = "[old]".length();
        int l3 = "[new]".length();
        for (BundleUpdate update : result.getBundleUpdates()) {
            if (update.getSymbolicName() != null && stripSymbolicName(update.getSymbolicName()).length() > l1) {
                l1 = stripSymbolicName(update.getSymbolicName()).length();
            }
            if (update.getPreviousVersion().length() > l2) {
                l2 = update.getPreviousVersion().length();
            }
            if (update.getNewVersion().length() > l3) {
                l3 = update.getNewVersion().length();
            }
        }
        System.out.printf("%-" + l1 + "s   %-" + l2 + "s   %-" + l3 + "s%n", "[name]", "[old]", "[new]");
        java.util.List<BundleUpdate> updates = new ArrayList<>(result.getBundleUpdates());
        updates.sort(Comparator.comparing(BundleUpdate::getSymbolicName));
        for (BundleUpdate update : updates) {
            System.out.printf("%-" + l1 + "s | %-" + l2 + "s | %-" + l3 + "s%n",
                    update.getSymbolicName() == null ? "" : stripSymbolicName(update.getSymbolicName()),
                    update.getPreviousVersion(),
                    update.getNewVersion());
        }
    }

    /**
     * Displays a list of {@link Patch patches} in short format. Each {@link Patch#getManagedPatch()} is already
     * tracked.
     * @param patches
     * @param listBundles
     */
    protected void display(Iterable<Patch> patches, boolean listBundles) {
        display(patches, listBundles, false);
    }

    /**
     * Displays a list of {@link Patch patches} in short format. Each {@link Patch#getManagedPatch()} is already
     * tracked.
     * @param patches
     * @param listBundles
     * @param lessInformation
     */
    protected void display(Iterable<Patch> patches, boolean listBundles, boolean lessInformation) {
        int l1 = "[name]".length();
        int l2 = "[installed]".length();
        int l3 = "[rollup]".length();
        int l4 = "[description]".length();
        int l5 = "[CVEs]".length();

        List<Patch> sorted = new ArrayList<>();
        patches.forEach(sorted::add);
        sorted.sort(Comparator.comparing(p -> p.getPatchData().getId()));

        for (Patch patch : sorted) {
            if (patch.getPatchData().getId().length() > l1) {
                l1 = patch.getPatchData().getId().length();
            }
            if (patch.getResult() != null) {
                java.util.List<String> versions = patch.getResult().getVersions();
                if (versions.size() > 0) {
                    // patch installed in fabric
                    for (String v : versions) {
                        if (("Version " + v).length() > l2) {
                            l2 = ("Version " + v).length();
                        }
                    }
                }
                java.util.List<String> karafBases = patch.getResult().getKarafBases();
                if (karafBases.size() > 0) {
                    // patch installed in standalone mode (root, admin:create)
                    for (String kbt : karafBases) {
                        String[] kb = kbt.split("\\s*\\|\\s*");
//                        if (kb[0].length() > l2) {
//                            l2 = kb[0].length();
//                        }
                        // ENTESB-15764 - let's only display "true" if the patch was installed
                        if ("true".length() > l2) {
                            l2 = "true".length();
                        }
                    }
                }
            }
            String desc = patch.getPatchData().getDescription() != null && !"".equals(patch.getPatchData().getDescription().trim())
                    ? patch.getPatchData().getDescription()
                    : patch.getPatchData().getId();
            if (desc.length() > l4) {
                l4 = desc.length();
            }
            for (CVE cve : patch.getPatchData().getCves()) {
                if (cve.getId().length() > l5) {
                    l5 = cve.getId().length();
                }
            }
        }

        if (!lessInformation) {
            System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l3 + "s %-" + l4 + "s %-" + l5 + "s%n", "[name]", "[installed]", "[rollup]", "[description]", "[CVEs]");
        } else {
            System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l4 + "s %-" + l5 + "s%n", "[name]", "[installed]", "[description]", "[CVEs]");
        }
        for (Patch patch : sorted) {
            String desc = patch.getPatchData().getDescription() != null && !"".equals(patch.getPatchData().getDescription().trim())
                    ? patch.getPatchData().getDescription() : patch.getPatchData().getId();
            String installed = Boolean.toString(patch.isInstalled());
            String rollup = Boolean.toString(patch.getPatchData().isRollupPatch());
            String cve = "-";
            if (patch.getResult() != null) {
                if (patch.getResult().getKarafBases().size() > 0) {
                    String kbt = patch.getResult().getKarafBases().get(0);
                    String[] kb = kbt.split("\\s*\\|\\s*");
//                    installed = kb[0];
                    installed = "true";
                }
            }
            if (patch.getPatchData().getCves().size() > 0) {
                cve = patch.getPatchData().getCves().get(0).getId();
            }
            if (!lessInformation) {
                System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l3 + "s %-" + l4 + "s %-" + l5 + "s%n", patch.getPatchData().getId(),
                        installed, rollup, desc, cve);
            } else {
                System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l4 + "s %-" + l5 + "s%n", patch.getPatchData().getId(),
                        installed, desc, cve);
            }
            int nbase = 1;
            int ncve = 1;
            String base = "";
            cve = "";
            List<String> bases = patch.getResult() != null ? patch.getResult().getKarafBases() : Collections.emptyList();
            List<CVE> cves = patch.getPatchData().getCves();
            while (nbase < bases.size() || ncve < cves.size()) {
                base = bases.size() > nbase ? bases.get(nbase).split("\\s*\\|\\s*")[0] : "";
                cve = cves.size() > ncve ? cves.get(ncve).getId() : "";
                if (!lessInformation) {
                    System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l3 + "s %-" + l4 + "s %-" + l5 + "s%n", " ",
                            base, " ", " ", cve);
                } else {
                    System.out.printf("%-" + l1 + "s %-" + l2 + "s %-" + l4 + "s %-" + l5 + "s%n", " ",
                            base, " ", cve);
                }
                nbase++;
                ncve++;
            }

            if (listBundles) {
                for (String b : patch.getPatchData().getBundles()) {
                    System.out.printf(" - %s%n", b);
                }
            }
        }
    }

    /**
     * Returns existing (added/tracked) patch
     * @param patchId
     * @return
     */
    protected Patch getPatch(String patchId) {
        Patch patch = service.getPatch(patchId);
        if (patch == null) {
            throw new PatchException("Patch '" + patchId + "' not found");
        }
        if (patch.isInstalled()) {
            throw new PatchException("Patch '" + patchId + "' is already installed");
        }
        return patch;
    }

}

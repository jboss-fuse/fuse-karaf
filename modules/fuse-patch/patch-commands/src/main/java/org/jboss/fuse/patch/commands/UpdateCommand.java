/**
 *  Copyright 2005-2017 Red Hat, Inc.
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

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.management.Utils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

@Service
@Command(scope = "patch", name = "update", description = "Updates (or checks for update) the patching mechanism itself")
public class UpdateCommand extends PatchCommandSupport {

    @Option(name = "--simulation", aliases = { "-s" }, description = "Simulates installation of the patch")
    boolean simulation = false;

    @Reference
    BundleContext bundleContext;

    @Reference
    FeaturesService featureService;

    @Override
    protected void doExecute(PatchService service) throws Exception {

        String karafHome = bundleContext.getProperty("karaf.home");
        String defaultRepository = bundleContext.getProperty("karaf.default.repository");
        File system = new File(karafHome, defaultRepository);
        if (!system.isDirectory() || !system.canRead()) {
            System.err.println("Can't locate system directory");
            return;
        }

        File patchManagement = new File(system, "org/jboss/fuse/modules/patch/patch-management");
        if (!patchManagement.isDirectory()) {
            System.out.println("No patch bundles detected in ${karaf.home}/" + defaultRepository + "/org/jboss/fuse/modules/patch/patch-management");
            return;
        }

        String newestVersion = null;
        File[] versionDirs = patchManagement.listFiles(File::isDirectory);
        if (versionDirs != null) {
            // reverse order - newest version first
            Set<String> versions = new TreeSet<String>((v1, v2) ->
                    Utils.getOsgiVersion(v2).compareTo(Utils.getOsgiVersion(v1)));
            versions.addAll(Arrays.stream(versionDirs).map(File::getName).collect(Collectors.toList()));
            if (versions.size() > 0) {
                newestVersion = versions.iterator().next();
            }
        }

        Version currentVersion = FrameworkUtil.getBundle(service.getClass()).getVersion();
        if (newestVersion == null) {
            System.out.println("No patch bundles detected in ${karaf.home}/" + defaultRepository + "/org/jboss/fuse/modules/patch/patch-management");
            return;
        } else {
            System.out.println("Current patch mechanism version: " + currentVersion.toString());
            Version newVersion = Utils.getOsgiVersion(newestVersion);
            if (currentVersion.equals(newVersion)) {
                System.out.println("No newer version of patch bundles detected");
                return;
            }
        }

        System.out.println("New patch mechanism version detected: " + newestVersion);

        if (!simulation) {
            System.out.println("Uninstalling patch features in version " + currentVersion);
            featureService.uninstallFeatures(new HashSet<String>(Arrays.asList("patch", "patch-management")),
                    EnumSet.noneOf(FeaturesService.Option.class));
            featureService.removeRepository(URI.create("mvn:org.jboss.fuse.modules.patch/patch-features/" + currentVersion + "/xml/features"));

            System.out.println("Installing patch features in version " + newestVersion);
            featureService.addRepository(URI.create("mvn:org.jboss.fuse.modules.patch/patch-features/" + newestVersion + "/xml/features"));
            featureService.installFeatures(new HashSet<String>(Arrays.asList("patch", "patch-management")),
                    EnumSet.noneOf(FeaturesService.Option.class));
        }
    }

}

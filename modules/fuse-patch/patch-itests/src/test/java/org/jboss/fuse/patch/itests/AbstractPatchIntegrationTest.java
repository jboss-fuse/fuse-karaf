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
package org.jboss.fuse.patch.itests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.karaf.shell.api.console.Session;
import org.jboss.fuse.itests.karaf.FuseKarafTestSupport;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.management.Patch;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * Abstract base class for all the patching mechanism integration tests (using the OSGi Service)
 */
public abstract class AbstractPatchIntegrationTest extends FuseKarafTestSupport {

    // Bundle-SymbolicName of the bundle we're patching
    private static final String PATCHABLE_BSN = "patchable";

    // Time-out for installing/rolling back patches
    private static final long TIMEOUT = 30 * 1000;

    @Inject
    protected PatchService service;

    @Inject
    protected BundleContext context;

    @Configuration
    public Option[] configuration() {
        MavenUrlReference patchFeature = maven()
                .groupId("org.jboss.fuse.modules.patch").artifactId("patch-features")
                .type("xml").classifier("features").versionAsInProject();

        return combine(
                configurationKaraf(),

                configureSecurity().disableKarafMBeanServerBuilder(),
//                configureConsole().ignoreLocalConsole(),
                editConfigurationFilePut("etc/branding.properties", "welcome", ""), // No welcome banner
                editConfigurationFilePut("etc/branding-ssh.properties", "welcome", ""),

                mavenBundle("org.ops4j.pax.tinybundles", "tinybundles").versionAsInProject(),
                mavenBundle("biz.aQute.bnd", "bndlib").versionAsInProject(),
                mavenBundle("org.apache.commons", "commons-compress").versionAsInProject(),
                mavenBundle("commons-io", "commons-io").versionAsInProject(),

                editConfigurationFilePut("etc/version.properties", "version", "7.0.0"),
                editConfigurationFilePut("etc/custom.properties", "fuse.patch.location", System.getProperty("fuse.patch.location")),
//                debugConfiguration("9999", true),
                features(patchFeature, "patch")
        );
    }

    @Override
    protected boolean usePatching() {
        return true;
    }

    // Install a patch and wait for installation to complete
    protected void install(String name) throws Exception {
        Patch patch = service.getPatch(name);
        service.install(patch, false, false);

        patch = service.getPatch(name);
        long start = System.currentTimeMillis();
        while (!patch.isInstalled() && System.currentTimeMillis() - start < TIMEOUT) {
            patch = service.getPatch(name);
            Thread.sleep(100);
        }
        if (!patch.isInstalled()) {
            fail(String.format("Patch '%s' did not installed within %s ms", name, TIMEOUT));
        }
    }

    // Rollback a patch and wait for rollback to complete
    protected void rollback(String name) throws Exception {
        Patch patch = service.getPatch(name);
        service.rollback(patch, false, false);

        patch = service.getPatch(name);
        long start = System.currentTimeMillis();
        while (patch.isInstalled() && System.currentTimeMillis() - start < TIMEOUT) {
            patch = service.getPatch(name);
            Thread.sleep(100);
        }
        if (patch.isInstalled()) {
            fail(String.format("Patch '%s' did not roll back within %s ms", name, TIMEOUT));
        }
    }

    // Load a patch into the patching service using service
    protected void load(BundleContext context, String name) throws Exception {
        File patch = new File(context.getProperty("fuse.patch.location"), name + ".zip");

        service.download(patch.toURI().toURL());
    }

    // Load a patch into the patching service using command
    protected void load(Session session, String name) throws Exception {
        File patch = new File(context.getProperty("fuse.patch.location"), name + ".zip");

        execute(session, String.format("patch:add %s", patch.toURI().toURL()));
    }

    protected void createPatchZipFiles() throws IOException {
        File patches = new File(context.getProperty("fuse.patch.location"));
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new File(patches, "patch-01.zip"))) {
            zos.putArchiveEntry(new ZipArchiveEntry("patch-01.patch"));
            IOUtils.copy(context.getBundle().getResource("patches/patch-01.patch").openStream(), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.0.1/patchable-1.0.1.jar"));
            IOUtils.copy(createPatchableBundle("1.0.1"), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.1.2/patchable-1.1.2.jar"));
            IOUtils.copy(createPatchableBundle("1.1.2"), zos);
            zos.closeArchiveEntry();
        }

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new File(patches, "patch-02.zip"))) {
            zos.putArchiveEntry(new ZipArchiveEntry("patch-02.patch"));
            IOUtils.copy(context.getBundle().getResource("patches/patch-02.patch").openStream(), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.0.1/patchable-1.0.1.jar"));
            IOUtils.copy(createPatchableBundle("1.0.1"), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.1.2/patchable-1.1.2.jar"));
            IOUtils.copy(createPatchableBundle("1.1.2"), zos);
            zos.closeArchiveEntry();
        }

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new File(patches, "patch-02-without-range.zip"))) {
            zos.putArchiveEntry(new ZipArchiveEntry("patch-02-without-range.patch"));
            IOUtils.copy(context.getBundle().getResource("patches/patch-02-without-range.patch").openStream(), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.0.1/patchable-1.0.1.jar"));
            IOUtils.copy(createPatchableBundle("1.0.1"), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry("repository/org/jboss/fuse/patch/patchable/1.1.2/patchable-1.1.2.jar"));
            IOUtils.copy(createPatchableBundle("1.1.2"), zos);
            zos.closeArchiveEntry();
        }
    }

    // Create a 'patchable' bundle with the specified version
    protected static InputStream createPatchableBundle(String version) {
        return TinyBundles.bundle()
                .set(Constants.BUNDLE_SYMBOLICNAME, PATCHABLE_BSN)
                .set(Constants.BUNDLE_VERSION, version)
                .build();
    }

    // Find the bundle we're patching
    protected Bundle getPatchableBundle() {
        for (Bundle bundle : context.getBundles()) {
            if (PATCHABLE_BSN.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        throw new RuntimeException("Bundle 'patchable' was not installed!");
    }

    protected void installPatchableBundle() throws Exception {
        // let's uninstall any previous bundle versions
        for (Bundle bundle : context.getBundles()) {
            if ("patchable".equals(bundle.getSymbolicName())) {
                bundle.uninstall();
            }
        }

        // now, copy the version 1.0.0 bundle into the system folder...
        File base = new File(System.getProperty("karaf.base"));
        File system = new File(base, "system");
        File target = new File(system, "org/jboss/fuse/patch/patchable/1.0.0/patchable-1.0.0.jar");
        target.getParentFile().mkdirs();

        IOUtils.copy(createPatchableBundle("1.0.0"), new FileOutputStream(target));

        // ... and install the bundle
        context.installBundle("mvn:org.jboss.fuse.patch/patchable/1.0.0");
    }

}

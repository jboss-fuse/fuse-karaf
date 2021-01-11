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
package org.jboss.fuse.patch.management;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.karaf.features.internal.model.processing.BundleReplacements;
import org.apache.karaf.features.internal.model.processing.FeatureReplacements;
import org.apache.karaf.features.internal.model.processing.FeaturesProcessing;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jboss.fuse.patch.management.impl.GitPatchManagementService;
import org.jboss.fuse.patch.management.impl.GitPatchManagementServiceImpl;
import org.jboss.fuse.patch.management.impl.GitPatchRepository;
import org.jboss.fuse.patch.management.impl.InternalUtils;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.startlevel.BundleStartLevel;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GitPatchManagementServiceIT extends PatchTestSupport {

    private GitPatchManagementService pm;
    private BundleStartLevel bsl;

    @Before
    public void init() throws IOException, GitAPIException {
        super.init(true, true);

        bsl = mock(BundleStartLevel.class);
        when(bundle.adapt(BundleStartLevel.class)).thenReturn(bsl);

        when(systemContext.getDataFile("patches")).thenReturn(new File(karafHome, "data/cache/bundle0/data/patches"));
    }

    @Test
    public void disabledPatchManagement() throws IOException, GitAPIException {
        System.setProperty("patching.disabled", "true");
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        assertFalse(pm.isEnabled());
        System.setProperty("patching.disabled", "");
    }

    @Test
    public void enabledPatchManagement() throws IOException, GitAPIException {
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        assertTrue(pm.isEnabled());
    }

    @Test
    public void initializationPerformedNoFuseVersion() throws IOException, GitAPIException {
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        try {
            pm.ensurePatchManagementInitialized();
            fail("Should fail, because versions can't be determined");
        } catch (PatchException e) {
            assertTrue(e.getMessage().contains("Can't find"));
        }
    }

    @Test
    public void initializationPerformedNoBaselineDistribution() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        try {
            pm.ensurePatchManagementInitialized();
            fail("Should fail, because no baseline distribution is found");
        } catch (PatchException e) {
            assertTrue(e.getMessage().contains("Can't find baseline distribution"));
        }
    }

    @Test
    public void initializationPerformedBaselineDistributionFoundInPatches() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        preparePatchZip("src/test/resources/baselines/baseline1", "target/karaf/patches/fuse-karaf-7.0.0-baseline.zip", true);
        validateInitialGitRepository();
        // check one more time - should not do anything harmful
        validateInitialGitRepository();
    }

    @Test
    public void initializationPerformedBaselineDistributionFoundInSystem() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        preparePatchZip("src/test/resources/baselines/baseline1", "target/karaf/system/org/jboss/fuse/fuse-karaf/7.0.0/fuse-karaf-7.0.0-baseline.zip", true);
        validateInitialGitRepository();
        // check one more time - should not do anything harmful
        validateInitialGitRepository();
    }

    @Test
    public void initializationPerformedPatchManagementAlreadyInstalled() throws IOException, GitAPIException {
        testWithAlreadyInstalledPatchManagementBundle("1.2.0");
    }

    @Test
    public void initializationPerformedPatchManagementInstalledAtOlderVersion() throws IOException, GitAPIException {
        testWithAlreadyInstalledPatchManagementBundle("1.1.9");
    }

    private void testWithAlreadyInstalledPatchManagementBundle(String version) throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        String line = String.format("mvn:org.jboss.fuse.modules.patch/patch-management/%s=2\n", version);
        FileUtils.write(new File(karafHome, "etc/startup.properties"), line, "UTF-8", true);
        preparePatchZip("src/test/resources/baselines/baseline1", "target/karaf/system/org/jboss/fuse/fuse-karaf/7.0.0/fuse-karaf-7.0.0-baseline.zip", true);
        validateInitialGitRepository();
    }

    /**
     * Patch 1 is non-rollup patch
     * @throws IOException
     * @throws GitAPIException
     */
    @Test
    public void addPatch1() throws IOException, GitAPIException {
        initializationPerformedBaselineDistributionFoundInSystem();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData = service.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL()).get(0);
        assertThat(patchData.getId(), equalTo("my-patch-1"));
        Patch patch = service.trackPatch(patchData);

        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        // we should see remote branch for the patch, but without checking it out, it won't be available in the clone's local branches
        List<Ref> branches = fork.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        Ref patchBranch = null;
        for (Ref remoteBranch : branches) {
            if (String.format("refs/remotes/origin/patch-%s", patchData.getId()).equals(remoteBranch.getName())) {
                patchBranch = remoteBranch;
                break;
            }
        }
        assertNotNull("Should find remote branch for the added patch", patchBranch);

        assertThat(patch.getManagedPatch().getCommitId(), equalTo(patchBranch.getObjectId().getName()));

        RevCommit patchCommit = new RevWalk(fork.getRepository()).parseCommit(patchBranch.getObjectId());
        // patch commit should be child of baseline commit
        RevCommit baselineCommit = new RevWalk(fork.getRepository()).parseCommit(patchCommit.getParent(0));

        // this baseline commit should be tagged "baseline-VERSION"
        Ref tag = fork.tagList().call().get(0);
        assertThat(tag.getName(), equalTo("refs/tags/baseline-7.0.0"));
        RevCommit baselineCommitFromTag = new RevWalk(fork.getRepository()).parseCommit(tag.getTarget().getObjectId());
        assertThat(baselineCommit.getId(), equalTo(baselineCommitFromTag.getId()));

        // let's see the patch applied to baseline-7.0.0
        fork.checkout()
                .setName("my-patch-1")
                .setStartPoint("origin/patch-my-patch-1")
                .setCreateBranch(true)
                .call();
        String myProperties = FileUtils.readFileToString(new File(fork.getRepository().getWorkTree(), "etc/my.properties"), "UTF-8");
        assertTrue(myProperties.contains("p1 = v1"));

        repository.closeRepository(fork, true);
    }

    @Test
    public void installThreeNonRollupPatches() throws IOException, GitAPIException {
        initializationPerformedBaselineDistributionFoundInSystem();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch5", "target/karaf/patches/source/patch-5.zip", false);
        preparePatchZip("src/test/resources/content/patch6", "target/karaf/patches/source/patch-6.zip", false);

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData1 = service.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL()).get(0);
        Patch patch1 = service.trackPatch(patchData1);
        PatchData patchData5 = service.fetchPatches(new File("target/karaf/patches/source/patch-5.zip").toURI().toURL()).get(0);
        Patch patch5 = service.trackPatch(patchData5);
        PatchData patchData6 = service.fetchPatches(new File("target/karaf/patches/source/patch-6.zip").toURI().toURL()).get(0);
        Patch patch6 = service.trackPatch(patchData6);

        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        String tx = service.beginInstallation(PatchKind.NON_ROLLUP);

        List<BundleUpdate> patch1Updates = new LinkedList<>();
        patch1Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-tranquility/1.2.0")
                .to("mvn:org.jboss.fuse/fuse-tranquility/1.2.3"));
        service.install(tx, patch1, patch1Updates);

        List<BundleUpdate> patch5Updates = new LinkedList<>();
        patch5Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-zen/1.1.44/war")
                .to("mvn:org.jboss.fuse/fuse-zen/1.2.0/war"));
        service.install(tx, patch5, patch5Updates);

        List<BundleUpdate> patch6Updates = new LinkedList<>();
        patch5Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-zen/1.2.4/war")
                .to("mvn:org.jboss.fuse/fuse-zen/1.3.0/war"));
        service.install(tx, patch6, patch6Updates);

        service.commitInstallation(tx);

        String binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        String etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        // version from patch-5 should be chosen, because there's 1.1.44->1.2.0
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        assertFalse(new File(karafHome, "etc/overrides.properties").exists());

        FeaturesProcessing fp = InternalUtils.loadFeatureProcessing(karafHome);
        List<BundleReplacements.OverrideBundle> bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(3));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-tranquility/[1.2.0,1.3.0)".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-tranquility/1.2.3".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.1,1.2)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.2.0/war".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));

        /* rollback time! */

        Patch p5 = service.loadPatch(new PatchDetailsRequest("my-patch-5"));
        service.rollback(p5.getPatchData());

        binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        // rollback wasn't successful
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertFalse(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.1.44/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        fp = InternalUtils.loadFeatureProcessing(karafHome);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(3));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-tranquility/[1.2.0,1.3.0)".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-tranquility/1.2.3".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.1,1.2)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.2.0/war".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));

        Patch p6 = service.loadPatch(new PatchDetailsRequest("my-patch-6"));
        service.rollback(p6.getPatchData());

        binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        fp = InternalUtils.loadFeatureProcessing(karafHome);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(2));
        assertTrue(bundles.stream().noneMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));

        repository.closeRepository(fork, true);
    }

    @Test
    public void installThreeNonRollupPatchesWithExternalizedVersionsProperties() throws IOException, GitAPIException {
        initializationPerformedBaselineDistributionFoundInSystem();
        try (FileOutputStream fos = new FileOutputStream(new File(karafHome, "etc/org.apache.karaf.features.xml"))) {
            FileUtils.copyFile(new File("src/test/resources/processing/oakf.2.xml"), fos);
        }
        FileUtils.write(new File(karafHome, "etc/versions.properties"), "version.zen = 1.1.9\n", "UTF-8");

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch5", "target/karaf/patches/source/patch-5.zip", false);
        preparePatchZip("src/test/resources/content/patch6", "target/karaf/patches/source/patch-6.zip", false);

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData1 = service.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL()).get(0);
        Patch patch1 = service.trackPatch(patchData1);
        PatchData patchData5 = service.fetchPatches(new File("target/karaf/patches/source/patch-5.zip").toURI().toURL()).get(0);
        Patch patch5 = service.trackPatch(patchData5);
        PatchData patchData6 = service.fetchPatches(new File("target/karaf/patches/source/patch-6.zip").toURI().toURL()).get(0);
        Patch patch6 = service.trackPatch(patchData6);

        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        assertTrue(FileUtils.readFileToString(new File(karafHome, "etc/versions.properties"), "UTF-8").contains("version.zen = 1.1.9"));

        String tx = service.beginInstallation(PatchKind.NON_ROLLUP);

        List<BundleUpdate> patch1Updates = new LinkedList<>();
        patch1Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-tranquility/1.2.0")
                .to("mvn:org.jboss.fuse/fuse-tranquility/1.2.3"));
        service.install(tx, patch1, patch1Updates);

        List<BundleUpdate> patch5Updates = new LinkedList<>();
        patch5Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-zen/1.1.44/war")
                .to("mvn:org.jboss.fuse/fuse-zen/1.2.0/war"));
        service.install(tx, patch5, patch5Updates);

        List<BundleUpdate> patch6Updates = new LinkedList<>();
        patch6Updates.add(BundleUpdate.from("mvn:org.jboss.fuse/fuse-zen/1.2.4/war")
                .to("mvn:org.jboss.fuse/fuse-zen/1.3.0/war"));
        service.install(tx, patch6, patch6Updates);

        service.commitInstallation(tx);

        String binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        String etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        // version from patch-5 should be chosen, because there's 1.1.44->1.2.0
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        assertFalse(new File(karafHome, "etc/overrides.properties").exists());

        FeaturesProcessing fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        List<BundleReplacements.OverrideBundle> bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(3));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-tranquility/[1.2.0,1.3.0)".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-tranquility/1.2.3".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.1,1.2)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/${version.zen}/war".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));
        assertTrue(FileUtils.readFileToString(new File(karafHome, "etc/versions.properties"), "UTF-8").contains("version.zen = 1.2.0"));

        /* rollback time! */

        Patch p5 = service.loadPatch(new PatchDetailsRequest("my-patch-5"));
        service.rollback(p5.getPatchData());

        binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        // rollback wasn't successful
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertFalse(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.1.44/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(3));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-tranquility/[1.2.0,1.3.0)".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-tranquility/1.2.3".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.1,1.2)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/${version.zen}/war".equals(b.getReplacement())));
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));
        assertTrue(FileUtils.readFileToString(new File(karafHome, "etc/versions.properties"), "UTF-8").contains("version.zen = 1.2.0"));

        Patch p6 = service.loadPatch(new PatchDetailsRequest("my-patch-6"));
        service.rollback(p6.getPatchData());

        binAdmin = FileUtils.readFileToString(new File(karafHome, "bin/instance"), "UTF-8");
        assertTrue(binAdmin.contains("system/org/jboss/fuse/fuse-tranquility/1.2.3/fuse-tranquility-1.2.3.jar"));

        etcStartupProperties = FileUtils.readFileToString(new File(karafHome, "etc/startup.properties"), "UTF-8");
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-zen/1.2.0/war = 42"));
        assertTrue(etcStartupProperties.contains("mvn\\:org.jboss.fuse/fuse-tranquility/1.2.3 = 42"));

        fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(2));
        assertTrue(bundles.stream().noneMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.3.0,1.4.0)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/1.3.3/war".equals(b.getReplacement())));

        // after rolling back patch 6 we can rollback patch 5
        p5 = service.loadPatch(new PatchDetailsRequest("my-patch-5"));
        service.rollback(p5.getPatchData());

        fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertTrue(bundles.stream().anyMatch(b ->
                "mvn:org.jboss.fuse/fuse-zen/[1.1,1.1.9)/war".equals(b.getOriginalUri())
                        && "mvn:org.jboss.fuse/fuse-zen/${version.zen}/war".equals(b.getReplacement())));
        assertTrue(FileUtils.readFileToString(new File(karafHome, "etc/versions.properties"), "UTF-8").contains("version.zen = 1.1.9"));

        repository.closeRepository(fork, true);
    }

    @Test
    public void installPPatchAndThenRPatch() throws IOException, GitAPIException {
        initializationPerformedBaselineDistributionFoundInSystem();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);

        // simulation of P patch installed using old patching mechanism
        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        FileUtils.write(new File(karafHome, "etc/overrides.properties"), "mvn:org.jboss.fuse/fuse-oceans/1.4.2\n", "UTF-8");
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // non-conflicting
        repository.closeRepository(fork, true);

        // overrides.properties as after installing P patch with old mechanism
        String etcOverridesProperties = FileUtils.readFileToString(new File(karafHome, "etc/overrides.properties"), "UTF-8");
        assertThat(etcOverridesProperties, equalTo("mvn:org.jboss.fuse/fuse-oceans/1.4.2\n"));

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData1 = service.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL()).get(0);
        Patch patch1 = service.trackPatch(patchData1);
        PatchData patchData4 = service.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL()).get(0);
        Patch patch4 = service.trackPatch(patchData4);

        String tx = service.beginInstallation(PatchKind.NON_ROLLUP);
        service.install(tx, patch1, null);
        service.commitInstallation(tx);

        assertTrue("There should be no changes to etc/overrides.properties after installing non-rollup patch",
                new File(karafHome, "etc/overrides.properties").exists());
        // overrides.properties as after installing P patch with new mechanism
        etcOverridesProperties = FileUtils.readFileToString(new File(karafHome, "etc/overrides.properties"), "UTF-8");
        assertThat(etcOverridesProperties, equalTo("mvn:org.jboss.fuse/fuse-oceans/1.4.2\n"));

        // override from P-Patch should go to etc/org.apache.karaf.features.xml
        FeaturesProcessing fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        List<BundleReplacements.OverrideBundle> bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(1));
        assertThat(bundles.get(0).getReplacement(), equalTo("mvn:org.jboss.fuse/fuse-tranquility/1.2.3"));

        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        assertTrue(repository.containsTag(fork, "patch-my-patch-1"));
        assertFalse(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));

        repository.closeRepository(fork, true);

        tx = service.beginInstallation(PatchKind.ROLLUP);
        service.install(tx, patch4, null);
        service.commitInstallation(tx);

        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        assertFalse(repository.containsTag(fork, "patch-my-patch-1"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));

        assertFalse("There should be no etc/overrides.properties after installing rollup patch",
                new File(karafHome, "etc/overrides.properties").exists());

        repository.closeRepository(fork, true);
    }

    @Test
    public void installPPatchWithFeatures() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        try (FileOutputStream fos = new FileOutputStream(new File(karafHome, "etc/org.apache.karaf.features.xml"))) {
            FileUtils.copyFile(new File("src/test/resources/baselines/baseline6/etc/org.apache.karaf.features.xml"), fos);
        }
        preparePatchZip("src/test/resources/baselines/baseline6", "target/karaf/system/org/jboss/fuse/fuse-karaf/7.0.0/fuse-karaf-7.0.0-baseline.zip", true);
        validateInitialGitRepository();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch10", "target/karaf/patches/source/patch-10.zip", false);

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData10 = service.fetchPatches(new File("target/karaf/patches/source/patch-10.zip").toURI().toURL()).get(0);
        Patch patch10 = service.trackPatch(patchData10);

        // before patching:
        FeaturesProcessing fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        List<FeatureReplacements.OverrideFeature> features = fp.getFeatureReplacements().getReplacements();
        assertThat(features.size(), equalTo(2));
        assertThat(features.get(0).getFeature().getBundles().size(), equalTo(1));
        assertThat(features.get(0).getFeature().getBundles().get(0).getLocation(), equalTo("mvn:org.jboss.fuse/fuse-utils/0.9"));
        assertThat(features.get(1).getFeature().getBundles().size(), equalTo(1));
        assertThat(features.get(1).getFeature().getBundles().get(0).getLocation(), equalTo("mvn:org.jboss.fuse/fuse-utils/0.9"));

        String tx = service.beginInstallation(PatchKind.NON_ROLLUP);
        service.install(tx, patch10, null);
        service.commitInstallation(tx);

        // override from P-Patch should go to etc/org.apache.karaf.features.xml - both bundle and feature overrides
        fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        List<BundleReplacements.OverrideBundle> bundles = fp.getBundleReplacements().getOverrideBundles();
        features = fp.getFeatureReplacements().getReplacements();
        assertThat(bundles.size(), equalTo(2)); // unchanged
        assertThat(features.size(), equalTo(2));
        assertThat(features.get(0).getFeature().getBundles().size(), equalTo(2));
        assertThat(features.get(0).getFeature().getBundles().get(0).getLocation(), equalTo("mvn:org.jboss.fuse/fuse-utils/1.0"));
        assertThat(features.get(0).getFeature().getBundles().get(1).getLocation(), equalTo("mvn:org.jboss.fuse/fuse-utils-extra/1.1"));
        assertThat(features.get(1).getFeature().getBundles().size(), equalTo(1));
        assertThat(features.get(1).getFeature().getBundles().get(0).getLocation(), equalTo("mvn:org.jboss.fuse/fuse-utils/0.9"));
    }

    /**
     * Installation of R patch <strong>may</strong> leave P patches installed when they provide <strong>only</strong>
     * bundles and the bundles are at higher version
     * @throws IOException
     * @throws GitAPIException
     */
    @Test
    public void installPPatchHotFixPPatchAndThenRPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        preparePatchZip("src/test/resources/baselines/baseline5", "target/karaf/system/org/jboss/fuse/fuse-karaf/7.0.0/fuse-karaf-7.0.0-baseline.zip", true);
        validateInitialGitRepository();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch2", "target/karaf/patches/source/patch-2.zip", false);
        preparePatchZip("src/test/resources/content/patch9", "target/karaf/patches/source/patch-9.zip", false);

        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData1 = service.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL()).get(0);
        Patch patch1 = service.trackPatch(patchData1);
        PatchData patchData2 = service.fetchPatches(new File("target/karaf/patches/source/patch-2.zip").toURI().toURL()).get(0);
        Patch patch2 = service.trackPatch(patchData2);
        PatchData patchData9 = service.fetchPatches(new File("target/karaf/patches/source/patch-9.zip").toURI().toURL()).get(0);
        Patch patch9 = service.trackPatch(patchData9);

        String tx = service.beginInstallation(PatchKind.NON_ROLLUP);
        service.install(tx, patch1, null);
        service.commitInstallation(tx);

        tx = service.beginInstallation(PatchKind.NON_ROLLUP);
        service.install(tx, patch2, null);
        service.commitInstallation(tx);

        assertFalse("There should be no etc/overrides.properties after installing non-rollup patches",
                new File(karafHome, "etc/overrides.properties").exists());
        FeaturesProcessing fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        List<BundleReplacements.OverrideBundle> bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(3));
        assertThat(bundles.get(0).getReplacement(), equalTo("mvn:org.jboss.fuse/fuse-observations/3.2"));
        assertThat(bundles.get(1).getReplacement(), equalTo("mvn:org.jboss.fuse/fuse-temporary-workaround/2.2.1"));
        assertThat(bundles.get(2).getReplacement(), equalTo("mvn:org.jboss.fuse/fuse-tranquility/1.2.5"));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        assertTrue(repository.containsTag(fork, "patch-my-patch-1"));
        assertTrue(repository.containsTag(fork, "patch-my-patch-2"));
        assertFalse(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));

        repository.closeRepository(fork, true);

        tx = service.beginInstallation(PatchKind.ROLLUP);
        service.install(tx, patch9, null);
        service.commitInstallation(tx);

        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        assertFalse(repository.containsTag(fork, "patch-my-patch-1"));
        assertFalse(repository.containsTag(fork, "patch-my-patch-2"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));

        assertFalse("There still should be no etc/overrides.properties after installing rollup patch",
                new File(karafHome, "etc/overrides.properties").exists());
        // features processing should be conflict-resolved (R-patch version and P-patch version).
        // newer version of fuse-tranquility (from P-Patch) should win
        fp = InternalUtils.loadFeatureProcessing(new File(karafHome, "etc/org.apache.karaf.features.xml"), null);
        bundles = fp.getBundleReplacements().getOverrideBundles();
        assertThat(bundles.size(), equalTo(2));
        assertThat(bundles.get(1).getReplacement(), equalTo("mvn:org.jboss.fuse/fuse-tranquility/1.2.5"));

        repository.closeRepository(fork, true);
    }

    /**
     * Patch 4 is rollup patch (doesn't contain descriptor, contains etc/version.properties)
     * Adding it is not different that adding non-rollup patch. Installation is different
     * @throws IOException
     * @throws GitAPIException
     */
    @Test
    public void addPatch4() throws IOException, GitAPIException {
        initializationPerformedBaselineDistributionFoundInSystem();

        // prepare some ZIP patches
        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);

        PatchManagement service = (PatchManagement) pm;
        PatchData patchData = service.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL()).get(0);
        assertThat(patchData.getId(), equalTo("patch-4"));
        Patch patch = service.trackPatch(patchData);

        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);

        // we should see remote branch for the patch, but without checking it out, it won't be available in the clone's local branches
        List<Ref> branches = fork.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        Ref patchBranch = null;
        for (Ref remoteBranch : branches) {
            if (String.format("refs/remotes/origin/patch-%s", patchData.getId()).equals(remoteBranch.getName())) {
                patchBranch = remoteBranch;
                break;
            }
        }
        assertNotNull("Should find remote branch for the added patch", patchBranch);

        assertThat(patch.getManagedPatch().getCommitId(), equalTo(patchBranch.getObjectId().getName()));

        RevCommit patchCommit = new RevWalk(fork.getRepository()).parseCommit(patchBranch.getObjectId());
        // patch commit should be child of baseline commit
        RevCommit baselineCommit = new RevWalk(fork.getRepository()).parseCommit(patchCommit.getParent(0));

        // this baseline commit should be tagged "baseline-VERSION"
        Ref tag = fork.tagList().call().get(0);
        assertThat(tag.getName(), equalTo("refs/tags/baseline-7.0.0"));
        RevCommit baselineCommitFromTag = new RevWalk(fork.getRepository()).parseCommit(tag.getTarget().getObjectId());
        assertThat(baselineCommit.getId(), equalTo(baselineCommitFromTag.getId()));

        List<DiffEntry> patchDiff = repository.diff(fork, baselineCommit, patchCommit);
        int changes = SystemUtils.IS_OS_WINDOWS ? 9 : 10;
        assertThat("patch-4 should lead to " + changes + " changes", patchDiff.size(), equalTo(changes));
        for (Iterator<DiffEntry> iterator = patchDiff.iterator(); iterator.hasNext();) {
            DiffEntry de = iterator.next();
            if ("bin/start".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if ("bin/stop".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if (!SystemUtils.IS_OS_WINDOWS && "bin/setenv".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if ("etc/startup.properties".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if ("etc/my.properties".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.ADD) {
                iterator.remove();
            }
            if ("etc/system.properties".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if ("etc/version.properties".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                iterator.remove();
            }
            if ("patch-info.txt".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.ADD) {
                iterator.remove();
            }
            if ("bin/fuse".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.ADD) {
                iterator.remove();
            }
            if ("etc/org.apache.karaf.features.xml".equals(de.getNewPath()) && de.getChangeType() == DiffEntry.ChangeType.ADD) {
                iterator.remove();
            }
        }

        assertThat("Unknown changes in patch-4", patchDiff.size(), equalTo(0));

        // let's see the patch applied to baseline-7.0.0
        fork.checkout()
                .setName("patch-4")
                .setStartPoint("origin/patch-patch-4")
                .setCreateBranch(true)
                .call();
        String startupProperties = FileUtils.readFileToString(new File(fork.getRepository().getWorkTree(), "etc/startup.properties"), "UTF-8");
        assertTrue(startupProperties.contains("mvn\\:org.ops4j.pax.url/pax-url-gopher/2.4.0=5"));

        repository.closeRepository(fork, true);
    }

    @Test
    public void listNoPatchesAvailable() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        patchManagement();
        PatchManagement management = (PatchManagement) pm;
        assertThat(management.listPatches(false).size(), equalTo(0));
    }

    @Test
    public void listSingleUntrackedPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        patchManagement();
        PatchManagement management = (PatchManagement) pm;
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        List<Patch> patches = management.listPatches(true);
        assertThat(patches.size(), equalTo(1));

        Patch p = patches.get(0);
        assertNotNull(p.getPatchData());
        assertNull(p.getResult());
        assertNull(p.getManagedPatch());

        assertThat(p.getPatchData().getId(), equalTo("my-patch-1"));
        assertThat(p.getPatchData().getFiles().size(), equalTo(2));
        assertThat(p.getPatchData().getBundles().size(), equalTo(1));
        assertThat(p.getPatchData().getBundles().iterator().next(), equalTo("mvn:org.jboss.fuse/fuse-tranquility/1.2.3"));
    }

    @Test
    public void listSingleTrackedPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;
        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        List<Patch> patches = management.listPatches(true);
        assertThat(patches.size(), equalTo(1));

        Patch p = patches.get(0);
        assertNotNull(p.getPatchData());
        assertNull(p.getResult());
        assertNull(p.getManagedPatch());

        ((PatchManagement) pm).trackPatch(p.getPatchData());

        p = management.listPatches(true).get(0);
        assertNotNull(p.getPatchData());
        assertNull(p.getResult());
        assertNotNull(p.getManagedPatch());

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        Ref ref = fork.checkout()
                .setCreateBranch(true)
                .setName("patch-my-patch-1")
                .setStartPoint("refs/remotes/origin/patch-my-patch-1")
                .call();

        // commit stored in ManagedPatch vs. commit of the patch branch
        assertThat(ref.getObjectId().getName(), equalTo(p.getManagedPatch().getCommitId()));
    }

    @Test
    public void listPatches() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch3", "target/karaf/patches/source/patch-3.zip", false);

        // with descriptor
        management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        // descriptor only
        management.fetchPatches(new File("src/test/resources/descriptors/my-patch-2.patch").toURI().toURL());
        // without descriptor
        management.fetchPatches(new File("target/karaf/patches/source/patch-3.zip").toURI().toURL());

        assertThat(management.listPatches(false).size(), equalTo(3));

        assertTrue(new File(patchesHome, "my-patch-1").isDirectory());
        assertTrue(new File(patchesHome, "my-patch-1.patch").isFile());
        assertFalse(new File(patchesHome, "my-patch-2").exists());
        assertTrue(new File(patchesHome, "my-patch-2.patch").isFile());
        assertTrue(new File(patchesHome, "patch-3").isDirectory());
        assertTrue(new File(patchesHome, "patch-3.patch").isFile());
    }

    @Test
    public void beginRollupPatchInstallation() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        patchManagement();
        PatchManagement management = (PatchManagement) pm;
        String tx = management.beginInstallation(PatchKind.ROLLUP);
        assertTrue(tx.startsWith("refs/heads/patch-install-"));

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        Git fork = transactions.values().iterator().next();
        ObjectId currentBranch = fork.getRepository().resolve("HEAD^{commit}");
        ObjectId tempBranch = fork.getRepository().resolve(tx + "^{commit}");
        ObjectId masterBranch = fork.getRepository().resolve("master^{commit}");
        ObjectId baseline = fork.getRepository().resolve("refs/tags/baseline-7.0.0^{commit}");
        assertThat(tempBranch, equalTo(currentBranch));
        assertThat(tempBranch, equalTo(baseline));
        assertThat(masterBranch, not(equalTo(currentBranch)));
    }

    @Test
    public void beginNonRollupPatchInstallation() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        patchManagement();
        PatchManagement management = (PatchManagement) pm;
        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        assertTrue(tx.startsWith("refs/heads/patch-install-"));

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        Git fork = transactions.values().iterator().next();
        ObjectId currentBranch = fork.getRepository().resolve("HEAD^{commit}");
        ObjectId tempBranch = fork.getRepository().resolve(tx + "^{commit}");
        ObjectId masterBranch = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH + "^{commit}");
        ObjectId baseline = fork.getRepository().resolve("refs/tags/baseline-7.0.0^{commit}");
        assertThat(tempBranch, equalTo(currentBranch));
        assertThat(tempBranch, not(equalTo(baseline)));
        assertThat(masterBranch, equalTo(currentBranch));
    }

    @Test
    public void installRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        repository.prepareCommit(fork, "artificial change, not treated as user change (could be a patch)").call();
        repository.prepareCommit(fork, "artificial change, not treated as user change").call();
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // no changes, but commit
        FileUtils.write(new File(karafHome, "bin/start"), "echo \"another user change\"\n", "UTF-8", true);
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // conflicting change, but commit
        FileUtils.write(new File(karafHome, "bin/test"), "echo \"another user change\"\n", "UTF-8");
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // non-conflicting
        repository.closeRepository(fork, true);

        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        String tx = management.beginInstallation(PatchKind.ROLLUP);
        management.install(tx, patch, null);

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        fork = transactions.values().iterator().next();

        ObjectId since = fork.getRepository().resolve("baseline-7.0.0^{commit}");
        ObjectId to = fork.getRepository().resolve(tx);
        Iterable<RevCommit> commits = fork.log().addRange(since, to).call();
        // only one "user change", because we had two conflicts with new baseline - they were resolved
        // by picking what already comes from rollup patch ("ours"):
        /*
         * Problem with applying the change 657f11c4b65bb7893a2b82f888bb9731a6d5f7d0:
         *  - bin/start: BOTH_MODIFIED
         * Choosing "ours" change
         * Problem with applying the change d9272b97582582f4b056f7170130ec91fc21aeac:
         *  - bin/start: BOTH_MODIFIED
         * Choosing "ours" change
         */
        List<String> commitList = Arrays.asList(
                "[PATCH] Apply user changes",
                "[PATCH] Apply user changes",
                "[PATCH] Apply user changes",
                "[PATCH] Rollup patch patch-4 - resetting overrides",
                "[PATCH] Installing rollup patch patch-4");

        int n = 0;
        for (RevCommit c : commits) {
            String msg = c.getShortMessage();
            assertThat(msg, equalTo(commitList.get(n++)));
        }

        assertThat(n, equalTo(commitList.size()));

        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));
    }

    @Test
    public void installRollupPatchWithFeatureProcessingConflicts() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement("baseline5");
        PatchManagement management = (PatchManagement) pm;

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        // conflicting user change to critical "etc/org.apache.karaf.features.xml" file
        FileUtils.copyFile(new File("src/test/resources/processing/oakf.3.xml"),
                new File(fork.getRepository().getWorkTree(), "etc/org.apache.karaf.features.xml"));
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // non-conflicting
        repository.closeRepository(fork, true);

        preparePatchZip("src/test/resources/content/patch9", "target/karaf/patches/source/patch-9.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-9.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        String tx = management.beginInstallation(PatchKind.ROLLUP);
        management.install(tx, patch, null);

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        fork = transactions.values().iterator().next();

        ObjectId since = fork.getRepository().resolve("baseline-7.0.0^{commit}");
        ObjectId to = fork.getRepository().resolve(tx);
        Iterable<RevCommit> commits = fork.log().addRange(since, to).call();
        // only one "user change", because we had two conflicts with new baseline - they were resolved
        // by picking what already comes from rollup patch ("ours"):
        /*
         * Problem with applying the change 657f11c4b65bb7893a2b82f888bb9731a6d5f7d0:
         *  - bin/start: BOTH_MODIFIED
         * Choosing "ours" change
         * Problem with applying the change d9272b97582582f4b056f7170130ec91fc21aeac:
         *  - bin/start: BOTH_MODIFIED
         * Choosing "ours" change
         */
        List<String> commitList = Arrays.asList(
                "[PATCH] Apply user changes",
                "[PATCH] Apply user changes",
                "[PATCH] Rollup patch patch-9 - resetting overrides",
                "[PATCH] Installing rollup patch patch-9");

        int n = 0;
        for (RevCommit c : commits) {
            String msg = c.getShortMessage();
            assertThat(msg, equalTo(commitList.get(n++)));
        }

        assertThat(n, equalTo(commitList.size()));

        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));

        String rPatchVersion = FileUtils.readFileToString(new File("src/test/resources/content/patch9/etc/org.apache.karaf.features.xml"), "UTF-8");
        String afterPatching = FileUtils.readFileToString(new File(fork.getRepository().getWorkTree(), "etc/org.apache.karaf.features.xml"), "UTF-8");
        assertEquals(rPatchVersion, afterPatching);
    }

    @Test
    public void rollbackRollupPatchInstallation() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve("master");

        String tx = management.beginInstallation(PatchKind.ROLLUP);
        management.install(tx, patch, null);
        management.rollbackInstallation(tx);

        fork.pull().call();
        ObjectId master2 = fork.getRepository().resolve("master");

        assertThat(master1, equalTo(master2));
        assertThat(fork.tagList().call().size(), equalTo(2));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
    }

    @Test
    public void commitRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        String tx = management.beginInstallation(PatchKind.ROLLUP);
        management.install(tx, patch, null);
        management.commitInstallation(tx);

        repository.closeRepository(fork, true);
        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master2 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        assertThat(master1, not(equalTo(master2)));
        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0.redhat-002"));
        assertThat("Baseline should change", repository.findCurrentBaseline(fork).getTagName(), equalTo("baseline-7.0.0.redhat-002"));

        String binStart = FileUtils.readFileToString(new File(karafHome, "bin/start"), "UTF-8");
        assertTrue("bin/start should be patched by patch-4",
                binStart.contains("echo \"we had to add this line, because without it, everything crashed\""));

        // we had conflict, so expect the backup
        String backupRef = new RevWalk(fork.getRepository()).parseCommit(master2).getFullMessage().split("\n\n")[1];
        String oldBinStart = FileUtils.readFileToString(new File(karafHome, "patches/patch-4.backup/"
                + backupRef + "/bin/start"), "UTF-8");
        assertTrue("bin/start should be backed up",
                oldBinStart.contains("echo \"This is user's change\""));

        assertFalse("There should be no etc/overrides.properties after installing rollup patch",
                new File(karafHome, "etc/overrides.properties").exists());
    }

    @Test
    public void rollbackInstalledRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        preparePatchZip("src/test/resources/content/patch4", "target/karaf/patches/source/patch-4.zip", false);

        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        Patch patch1 = management.trackPatch(patches.get(0));
        patches = management.fetchPatches(new File("target/karaf/patches/source/patch-4.zip").toURI().toURL());
        Patch patch4 = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        String tx = management.beginInstallation(PatchKind.ROLLUP);
        management.install(tx, patch4, null);
        management.commitInstallation(tx);

        // install P patch to check if rolling back rollup patch will remove P patch's tag
        tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        management.install(tx, patch1, null);
        management.commitInstallation(tx);

        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        assertTrue(repository.containsTag(fork, "patch-my-patch-1"));

        management.rollback(patch4.getPatchData());

        repository.closeRepository(fork, true);
        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master2 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        assertThat(master1, not(equalTo(master2)));
        assertThat(fork.tagList().call().size(), equalTo(2));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertFalse("When rolling back rollup patch, newer P patches' tags should be removed",
                repository.containsTag(fork, "patch-my-patch-1"));
        assertThat(repository.findCurrentBaseline(fork).getTagName(), equalTo("baseline-7.0.0"));

        // TODO: There should be version restored from backed up conflict
        // but we've changed the way rolledback R patch handled - we copy entire WC after rollback
//        String binStart = FileUtils.readFileToString(new File(karafHome, "bin/start"));
//        assertTrue("bin/start should be at previous version",
//                binStart.contains("echo \"This is user's change\""));
    }

    @Test
    public void installNonRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // no changes, but commit
        repository.prepareCommit(fork, "artificial change, not treated as user change (could be a patch)").call();
        repository.push(fork);
        FileUtils.write(new File(karafHome, "bin/shutdown"), "#!/bin/bash\nexit 42", "UTF-8");
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork);
        repository.closeRepository(fork, true);

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        management.install(tx, patch, null);

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        fork = transactions.values().iterator().next();

        ObjectId since = fork.getRepository().resolve("baseline-7.0.0^{commit}");
        ObjectId to = fork.getRepository().resolve(tx);
        Iterable<RevCommit> commits = fork.log().addRange(since, to).call();
        List<String> commitList = Arrays.asList(
                "[PATCH] Installing patch my-patch-1",
                "[PATCH] Apply user changes",
                "artificial change, not treated as user change (could be a patch)",
                "[PATCH] Apply user changes");

        int n = 0;
        for (RevCommit c : commits) {
            String msg = c.getShortMessage();
            assertThat(msg, equalTo(commitList.get(n++)));
        }

        assertThat(n, equalTo(commitList.size()));

        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "patch-my-patch-1"));

        assertThat("The conflict should be resolved in special way", FileUtils.readFileToString(new File(karafHome, "bin/setenv"), "UTF-8"),
                equalTo("JAVA_MIN_MEM=2G # Minimum memory for the JVM\n"));
    }

    @Test
    public void installNonRollupPatchWithUberJars() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement("baseline4");
        PatchManagement management = (PatchManagement) pm;

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ((GitPatchManagementServiceImpl)pm).applyUserChanges(fork); // no changes, but commit
        repository.prepareCommit(fork, "artificial change, not treated as user change (could be a patch)").call();
        repository.push(fork);
        repository.closeRepository(fork, true);

        preparePatchZip("src/test/resources/content/patch8", "target/karaf/patches/source/patch-8.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-8.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        /*
         * bundle.0 = mvn:org.jboss.fuse/pax-romana/1.0.1
         * bundle.1 = mvn:org.jboss.fuse/pax-hellenica/1.0.1/jar
         * # for these two, bundle.getLocation() will return non-matching location
         * bundle.2 = mvn:org.jboss.fuse/pax-bohemia/1.0.1
         * bundle.3 = mvn:org.jboss.fuse/pax-pomerania/1.0.1/jar
         * # for these two, bundle.getLocation() will return matching location
         * bundle.4 = mvn:org.jboss.fuse/pax-avaria/1.0.1/jar/uber
         * bundle.5 = mvn:org.jboss.fuse/pax-mazovia/1.0.1//uber
         * # for these two, bundle.getLocation() will return non-matching location
         * bundle.6 = mvn:org.jboss.fuse/pax-novgorod/1.0.1/jar/uber
         * bundle.7 = mvn:org.jboss.fuse/pax-castile/1.0.1//uber
         */
        LinkedList<BundleUpdate> bundleUpdatesInThisPatch = new LinkedList<>();
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-romana", "1.0.1",
                "mvn:org.jboss.fuse/pax-romana/1.0.1", "1.0.0", "mvn:org.jboss.fuse/pax-romana/1.0.0"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-hellenica", "1.0.1",
                "mvn:org.jboss.fuse/pax-hellenica/1.0.1/jar", "1.0.0", "mvn:org.jboss.fuse/pax-hellenica/1.0.0/jar"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-bohemia", "1.0.1",
                "mvn:org.jboss.fuse/pax-bohemia/1.0.1", "1.0.0", "mvn:org.jboss.fuse/pax-bohemia/1.0.0/jar"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-pomerania", "1.0.1",
                "mvn:org.jboss.fuse/pax-pomerania/1.0.1/jar", "1.0.0", "mvn:org.jboss.fuse/pax-pomerania/1.0.0"));

        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-avaria", "1.0.1",
                "mvn:org.jboss.fuse/pax-avaria/1.0.1/jar/uber", "1.0.0", "mvn:org.jboss.fuse/pax-avaria/1.0.0/jar/uber"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-mazovia", "1.0.1",
                "mvn:org.jboss.fuse/pax-mazovia/1.0.1//uber", "1.0.0", "mvn:org.jboss.fuse/pax-mazovia/1.0.0//uber"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-novgorod", "1.0.1",
                "mvn:org.jboss.fuse/pax-novgorod/1.0.1/jar/uber", "1.0.0", "mvn:org.jboss.fuse/pax-novgorod/1.0.0//uber"));
        bundleUpdatesInThisPatch.add(new BundleUpdate("pax-castile", "1.0.1",
                "mvn:org.jboss.fuse/pax-castile/1.0.1//uber", "1.0.0", "mvn:org.jboss.fuse/pax-castile/1.0.0/jar/uber"));
        management.install(tx, patch, bundleUpdatesInThisPatch);

        @SuppressWarnings("unchecked")
        Map<String, Git> transactions = (Map<String, Git>) getField(management, "pendingTransactions");
        assertThat(transactions.size(), equalTo(1));
        fork = transactions.values().iterator().next();

        ObjectId since = fork.getRepository().resolve("baseline-7.0.0^{commit}");
        ObjectId to = fork.getRepository().resolve(tx);
        Iterable<RevCommit> commits = fork.log().addRange(since, to).call();
        List<String> commitList = Arrays.asList(
                "[PATCH] Installing patch my-patch-8",
                "artificial change, not treated as user change (could be a patch)",
                "[PATCH] Apply user changes");

        int n = 0;
        for (RevCommit c : commits) {
            String msg = c.getShortMessage();
            assertThat(msg, equalTo(commitList.get(n++)));
        }

        assertThat(n, equalTo(commitList.size()));

        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "patch-my-patch-8"));

        Properties startup = new Properties();
        try (FileReader reader = new FileReader(new File(fork.getRepository().getWorkTree(), "etc/startup.properties"))) {
            startup.load(reader);
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-romana/1.0.1"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-hellenica/1.0.1"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-bohemia/1.0.1"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-pomerania/1.0.1"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-avaria/1.0.1/jar/uber"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-mazovia/1.0.1/jar/uber"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-novgorod/1.0.1/jar/uber"));
            assertTrue(startup.containsKey("mvn:org.jboss.fuse/pax-castile/1.0.1/jar/uber"));

            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-romana/1.0.0"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-hellenica/1.0.0"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-bohemia/1.0.0"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-pomerania/1.0.0"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-avaria/1.0.0/jar/uber"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-mazovia/1.0.0/jar/uber"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-novgorod/1.0.0/jar/uber"));
            assertFalse(startup.containsKey("mvn:org.jboss.fuse/pax-castile/1.0.0/jar/uber"));
        }
    }

    @Test
    public void rollbackNonRollupPatchInstallation() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve("master");

        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        management.install(tx, patch, null);
        management.rollbackInstallation(tx);

        fork.pull().call();
        ObjectId master2 = fork.getRepository().resolve("master");

        assertThat(master1, equalTo(master2));
        assertThat(fork.tagList().call().size(), equalTo(2));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
    }

    @Test
    public void commitNonRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        management.install(tx, patch, null);
        management.commitInstallation(tx);

        repository.closeRepository(fork, true);
        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master2 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        assertThat(master1, not(equalTo(master2)));
        assertThat(fork.tagList().call().size(), equalTo(3));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertTrue(repository.containsTag(fork, "patch-my-patch-1"));
        assertThat("Baseline should not change", repository.findCurrentBaseline(fork).getTagName(), equalTo("baseline-7.0.0"));

        String binStart = FileUtils.readFileToString(new File(karafHome, "bin/start"), "UTF-8");
        assertTrue("bin/start should be patched by patch-1", binStart.contains("echo \"started\""));

        // we had conflict, so expect the backup
        String oldBinStart = FileUtils.readFileToString(new File(karafHome, "patches/my-patch-1.backup/bin/start"), "UTF-8");
        assertTrue("bin/start should be backed up",
                oldBinStart.contains("echo \"This is user's change\""));
    }

    @Test
    public void rollbackInstalledNonRollupPatch() throws IOException, GitAPIException {
        freshKarafStandaloneDistro();
        GitPatchRepository repository = patchManagement();
        PatchManagement management = (PatchManagement) pm;

        preparePatchZip("src/test/resources/content/patch1", "target/karaf/patches/source/patch-1.zip", false);
        List<PatchData> patches = management.fetchPatches(new File("target/karaf/patches/source/patch-1.zip").toURI().toURL());
        Patch patch = management.trackPatch(patches.get(0));

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master1 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        String tx = management.beginInstallation(PatchKind.NON_ROLLUP);
        management.install(tx, patch, null);
        management.commitInstallation(tx);

        management.rollback(patch.getPatchData());

        repository.closeRepository(fork, true);
        fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        ObjectId master2 = fork.getRepository().resolve(GitPatchRepository.HISTORY_BRANCH);

        assertThat(master1, not(equalTo(master2)));
        assertThat(fork.tagList().call().size(), equalTo(2));
        assertTrue(repository.containsTag(fork, "patch-management"));
        assertTrue(repository.containsTag(fork, "baseline-7.0.0"));
        assertFalse(repository.containsTag(fork, "patch-my-patch-1"));

        String binStart = FileUtils.readFileToString(new File(karafHome, "bin/start"), "UTF-8");
        assertTrue("bin/start should be at previous version", binStart.contains("echo \"This is user's change\""));
    }

    private void validateInitialGitRepository() throws IOException, GitAPIException {
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        pm.ensurePatchManagementInitialized();
        GitPatchRepository repository = ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();

        Git fork = repository.cloneRepository(repository.findOrCreateMainGitRepository(), true);
        List<Ref> tags = fork.tagList().call();
        boolean found = false;
        for (Ref tag : tags) {
            if ("refs/tags/baseline-7.0.0".equals(tag.getName())) {
                found = true;
                break;
            }
        }
        assertTrue("Repository should contain baseline tag for version 7.0.0", found);

        // look in etc/startup.properties for installed patch-management bundle
        List<String> lines = FileUtils.readLines(new File(karafHome, "etc/startup.properties"), "UTF-8");
        found = false;
        for (String line : lines) {
            if ("mvn:org.jboss.fuse.modules.patch/patch-management/1.1.9=2".equals(line)) {
                fail("Should not contain old patch-management bundle in etc/startup.properties");
            }
            if ("mvn:org.jboss.fuse.modules.patch/patch-management/1.2.0=2".equals(line)) {
                if (found) {
                    fail("Should contain only one declaration of patch-management bundle in etc/startup.properties");
                }
                found = true;
            }
        }

        repository.closeRepository(fork, true);
    }

    /**
     * Install patch management inside fresh karaf distro. No validation is performed.
     * @return
     * @throws IOException
     */
    private GitPatchRepository patchManagement() throws IOException, GitAPIException {
        return patchManagement("baseline1");
    }

    /**
     * Install patch management inside fresh karaf distro. No validation is performed.
     * @param baseline
     * @return
     * @throws IOException
     */
    private GitPatchRepository patchManagement(String baseline) throws IOException, GitAPIException {
        preparePatchZip("src/test/resources/baselines/" + baseline, "target/karaf/system/org/jboss/fuse/fuse-karaf/7.0.0/fuse-karaf-7.0.0-baseline.zip", true);
        pm = new GitPatchManagementServiceImpl(bundleContext);
        pm.start();
        pm.ensurePatchManagementInitialized();
        return ((GitPatchManagementServiceImpl) pm).getGitPatchRepository();
    }

}

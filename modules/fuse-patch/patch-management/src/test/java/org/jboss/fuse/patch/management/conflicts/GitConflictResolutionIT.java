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
package org.jboss.fuse.patch.management.conflicts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchData;
import org.jboss.fuse.patch.management.PatchKind;
import org.jboss.fuse.patch.management.PatchResult;
import org.jboss.fuse.patch.management.PatchTestSupport;
import org.jboss.fuse.patch.management.impl.GitPatchManagementService;
import org.jboss.fuse.patch.management.impl.GitPatchManagementServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.startlevel.BundleStartLevel;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitConflictResolutionIT extends PatchTestSupport {

    private GitPatchManagementService pm;
    private BundleStartLevel bsl;
    private Git git;

    @Before
    public void init() throws IOException, GitAPIException {
        super.init(true, true);

        bsl = mock(BundleStartLevel.class);
        when(bundle.adapt(BundleStartLevel.class)).thenReturn(bsl);

        when(systemContext.getDataFile("patches")).thenReturn(new File(karafHome, "data/cache/bundle0/data/patches"));

        git = Git.init().setDirectory(new File(karafBase, "data/git-repository")).setGitDir(new File(karafBase, "data/git-repository/.git")).setBare(false).call();
        commit("init").call();
    }

    @Test
    public void mergeConflict() throws Exception {
        prepareChanges();
        RevWalk rw = new RevWalk(git.getRepository());

        git.checkout().setName("custom").setCreateBranch(false).call();
        MergeResult result = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .include(git.getRepository().resolve("patched"))
                .call();

        assertThat(result.getMergeStatus(), equalTo(MergeResult.MergeStatus.CONFLICTING));
        assertThat(result.getConflicts().size(), equalTo(1));

        Map<String, IndexDiff.StageState> conflicts = git.status().call().getConflictingStageState();
        assertThat(conflicts.size(), equalTo(1));
        assertThat(conflicts.get("etc/org.ops4j.pax.logging.cfg"), equalTo(IndexDiff.StageState.BOTH_MODIFIED));
    }

    @Test
    public void cherryPickConflict() throws Exception {
        prepareChanges2();
        RevWalk rw = new RevWalk(git.getRepository());

        git.checkout().setName("custom").setCreateBranch(false).call();
        ObjectId commit = git.getRepository().resolve("patched");
        CherryPickResult result = git.cherryPick().include(commit)
                .call();

        RevCommit cMaster = rw.parseCommit(git.getRepository().resolve("master"));
        RevCommit cCustom = rw.parseCommit(git.getRepository().resolve("custom"));
        RevCommit cPatched = rw.parseCommit(git.getRepository().resolve("patched"));

        assertThat(result.getStatus(), equalTo(CherryPickResult.CherryPickStatus.CONFLICTING));

        Map<String, IndexDiff.StageState> conflicts = git.status().call().getConflictingStageState();
        assertThat(conflicts.size(), equalTo(2));
        assertThat(conflicts.get("etc/org.ops4j.pax.logging.cfg"), equalTo(IndexDiff.StageState.BOTH_MODIFIED));
        assertThat(conflicts.get("etc/file.properties"), equalTo(IndexDiff.StageState.BOTH_MODIFIED));

        new GitPatchManagementServiceImpl().handleCherryPickConflict(null, git, result,
                git.getRepository().parseCommit(commit), true, PatchKind.NON_ROLLUP, "x", false, false);
        RevCommit cMerged = git.commit().setMessage("resolved").call();

        // we have 4 commits (7349224 is a custom change that's conflicting with "patched" branch):
        /*
           * 92a9ad8 - (HEAD -> custom) resolved
           * 7349224 - custom etc/org.ops4j.pax.logging.cfg
           | * 5777547 - (patched) patched etc/org.ops4j.pax.logging.cfg
           |/
           * 306f328 - (master) original etc/org.ops4j.pax.logging.cfg
         */
        FileWriter writer = new FileWriter("target/report.html");
        PatchData pd = new PatchData("my-patch-id2");
        PatchResult patchResult = PatchResult.load(pd, getClass().getResourceAsStream("/conflicts/example1/jboss-fuse-karaf-7.0.0.fuse-000160.patch.result"));
        DiffUtils.generateDiffReport(new Patch(pd, null), patchResult, git, new HashSet<>(),
                cMaster, cCustom, cPatched, cMerged, writer);
        writer.close();
    }

    @Test
    public void reportForXml() throws Exception {
        prepareChanges3();
        RevWalk rw = new RevWalk(git.getRepository());

        git.checkout().setName("custom").setCreateBranch(false).call();
        ObjectId commit = git.getRepository().resolve("patched");
        CherryPickResult result = git.cherryPick().include(commit)
                .call();

        RevCommit cMaster = rw.parseCommit(git.getRepository().resolve("master"));
        RevCommit cCustom = rw.parseCommit(git.getRepository().resolve("custom"));
        RevCommit cPatched = rw.parseCommit(git.getRepository().resolve("patched"));

        new GitPatchManagementServiceImpl().handleCherryPickConflict(null, git, result,
                git.getRepository().parseCommit(commit), true, PatchKind.NON_ROLLUP, "x", false, false);
        RevCommit cMerged = git.commit().setMessage("resolved").call();

        // we have 4 commits (7349224 is a custom change that's conflicting with "patched" branch):
        /*
           * 92a9ad8 - (HEAD -> custom) resolved
           * 7349224 - custom etc/org.ops4j.pax.logging.cfg
           | * 5777547 - (patched) patched etc/org.ops4j.pax.logging.cfg
           |/
           * 306f328 - (master) original etc/org.ops4j.pax.logging.cfg
         */
        FileWriter writer = new FileWriter("target/report2.html");
        PatchData pd = new PatchData("my-patch-id2");
        PatchResult patchResult = PatchResult.load(pd, getClass().getResourceAsStream("/conflicts/example1/jboss-fuse-karaf-7.0.0.fuse-000160.patch.result"));
        Set<String> conflicts = new HashSet<>();
        conflicts.add("etc/maven-settings.xml");
        DiffUtils.generateDiffReport(new Patch(pd, null), patchResult, git, conflicts,
                cMaster, cCustom, cPatched, cMerged, writer);
        writer.close();
    }

    @Test
    public void reportingDiffs() throws Exception {
        prepareChanges();

        ObjectReader reader = git.getRepository().newObjectReader();

        RevWalk rw = new RevWalk(git.getRepository());
        CanonicalTreeParser ctp1 = new CanonicalTreeParser();
        CanonicalTreeParser ctp2 = new CanonicalTreeParser();
        CanonicalTreeParser ctp3 = new CanonicalTreeParser();
        ctp1.reset(reader, rw.parseCommit(git.getRepository().resolve("master")).getTree());
        ctp2.reset(reader, rw.parseCommit(git.getRepository().resolve("custom")).getTree());
        ctp3.reset(reader, rw.parseCommit(git.getRepository().resolve("patched")).getTree());

        TreeWalk walk = new TreeWalk(reader);
        walk.addTree(ctp1);
        walk.addTree(ctp3);
        walk.setRecursive(true);

        List<DiffEntry> diffs = DiffEntry.scan(walk);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiffFormatter df = new DiffFormatter(baos);
        df.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setRepository(git.getRepository());
        df.format(diffs.get(0));
//        System.out.println(new String(baos.toByteArray()));

        AbbreviatedObjectId id1 = diffs.get(0).getOldId();
        AbbreviatedObjectId id2 = diffs.get(0).getNewId();

        byte[] bytes1 = reader.open(id1.toObjectId()).getBytes();
        byte[] bytes2 = reader.open(id2.toObjectId()).getBytes();
        RawText rt1 = new RawText(bytes1);
        RawText rt2 = new RawText(bytes2);
        EditList edits = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                .diff(RawTextComparator.DEFAULT, rt1, rt2);

        int aCur = 0;
        for (Edit curEdit : edits) {
            boolean prolog = aCur < curEdit.getBeginA();
            if (prolog) {
                System.out.print("<div class=\"edit unchanged\">");
            }
            while (aCur < curEdit.getBeginA()) {
                System.out.println(rt1.getString(aCur++));
            }
            if (prolog) {
                System.out.print("</div>");
            }
            if (curEdit.getType() == Edit.Type.INSERT) {
                System.out.print("<div class=\"edit added\">");
                for (int i = curEdit.getBeginB(); i < curEdit.getEndB(); i++) {
                    System.out.println(rt2.getString(i));
                }
                System.out.print("</div>");
            }
            if (curEdit.getType() == Edit.Type.REPLACE) {
                System.out.print("<div class=\"edit changed\"><div class=\"edit removed\">");
                for (int i = curEdit.getBeginA(); i < curEdit.getEndA(); i++) {
                    System.out.println(rt1.getString(i));
                }
                System.out.print("</div><div class=\"edit added\">");
                for (int i = curEdit.getBeginB(); i < curEdit.getEndB(); i++) {
                    System.out.println(rt2.getString(i));
                }
                aCur = curEdit.getEndA();
                System.out.print("</div></div>");
            }
        }
        boolean prolog = aCur < rt1.size();
        if (prolog) {
            System.out.print("<div class=\"edit unchanged\">");
        }
        while (aCur < rt1.size()) {
            System.out.println(rt1.getString(aCur++));
        }
        if (prolog) {
            System.out.print("</div>");
        }
    }

    private void prepareChanges() throws IOException, GitAPIException {
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.base.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        git.add().addFilepattern(".").call();
        commit("original etc/org.ops4j.pax.logging.cfg").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("custom").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.custom.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        git.add().addFilepattern(".").call();
        commit("custom etc/org.ops4j.pax.logging.cfg").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("patched").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.patched.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        git.add().addFilepattern(".").call();
        commit("custom etc/org.ops4j.pax.logging.cfg").call();
    }

    private void prepareChanges2() throws IOException, GitAPIException {
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.base.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        FileUtils.copyFile(new File("src/test/resources/conflicts/example2/file.base.properties"),
                new File(git.getRepository().getWorkTree(), "etc/file.properties"));
        FileUtils.copyFile(new File("src/test/resources/conflicts/example3/org.apache.karaf.features.after-create.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.apache.karaf.features.cfg"));
        git.add().addFilepattern(".").call();
        commit("original").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("custom").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.custom.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        FileUtils.copyFile(new File("src/test/resources/conflicts/example2/file.custom.properties"),
                new File(git.getRepository().getWorkTree(), "etc/file.properties"));
        git.add().addFilepattern(".").call();
        commit("custom change").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("patched").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example1/org.ops4j.pax.logging.patched.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.ops4j.pax.logging.cfg"));
        FileUtils.copyFile(new File("src/test/resources/conflicts/example2/file.patched.properties"),
                new File(git.getRepository().getWorkTree(), "etc/file.properties"));
        FileUtils.copyFile(new File("src/test/resources/conflicts/example3/org.apache.karaf.features.patched.cfg"),
                new File(git.getRepository().getWorkTree(), "etc/org.apache.karaf.features.cfg"));
        git.add().addFilepattern(".").call();
        commit("patched change").call();
    }

    private void prepareChanges3() throws IOException, GitAPIException {
        FileUtils.copyFile(new File("src/test/resources/conflicts/example3/maven-settings.base.xml"),
                new File(git.getRepository().getWorkTree(), "etc/maven-settings.xml"));
        git.add().addFilepattern(".").call();
        commit("original").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("custom").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example3/maven-settings.custom.xml"),
                new File(git.getRepository().getWorkTree(), "etc/maven-settings.xml"));
        git.add().addFilepattern(".").call();
        commit("custom change").call();

        git.checkout().setCreateBranch(true).setStartPoint("master").setName("patched").call();
        FileUtils.copyFile(new File("src/test/resources/conflicts/example3/maven-settings.patched.xml"),
                new File(git.getRepository().getWorkTree(), "etc/maven-settings.xml"));
        git.add().addFilepattern(".").call();
        commit("patched change").call();
    }

    private CommitCommand commit(String message) {
        return git.commit().setMessage(message).setAuthor("Paranoid Android", "test@jboss.org");
    }

}

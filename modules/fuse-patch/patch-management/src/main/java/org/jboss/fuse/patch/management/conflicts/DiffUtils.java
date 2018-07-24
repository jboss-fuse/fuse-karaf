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
package org.jboss.fuse.patch.management.conflicts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchData;
import org.jboss.fuse.patch.management.PatchReport;
import org.jboss.fuse.patch.management.PatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to generate diff reports after patching
 */
public final class DiffUtils {

    public static final Logger LOG = LoggerFactory.getLogger(DiffUtils.class);
    private static final DateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static String reportHeader;
    private static String reportFooter;
    private static String fileHeader1;
    private static String fileHeader2;
    private static String fileFooter;

    static {
        try {
            reportHeader = IOUtils.toString(DiffUtils.class.getResourceAsStream("/patch-report-header.html"), "UTF-8");
            reportFooter = IOUtils.toString(DiffUtils.class.getResourceAsStream("/patch-report-footer.html"), "UTF-8");
            String[] fileHeader = IOUtils.toString(DiffUtils.class.getResourceAsStream("/patch-file-header.html"), "UTF-8").split("@FILE@");
            fileHeader1 = fileHeader[0];
            fileHeader2 = fileHeader[1];
            fileFooter = IOUtils.toString(DiffUtils.class.getResourceAsStream("/patch-file-footer.html"), "UTF-8");
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    private DiffUtils() {
    }

    /**
     * <p>Having four commits, generate single, HTML report about all modified files</p>
     * <p>Please excuse inline html code.</p>
     * @param patch
     * @param git
     * @param conflicts
     * @param base
     * @param ours
     * @param theirs
     * @param resolved
     * @param result
     */
    public static void generateDiffReport(Patch patch, PatchResult patchResult, Git git, Set<String> conflicts,
                                          RevCommit base, RevCommit ours, RevCommit theirs, RevCommit resolved,
                                          Writer result) throws IOException {
        ObjectReader reader = git.getRepository().newObjectReader();

        CanonicalTreeParser ctpBase = new CanonicalTreeParser();
        CanonicalTreeParser ctpOurs = new CanonicalTreeParser();
        CanonicalTreeParser ctpTheirs = new CanonicalTreeParser();
        CanonicalTreeParser ctpResolved = new CanonicalTreeParser();
        ctpBase.reset(reader, base.getTree());
        ctpOurs.reset(reader, ours.getTree());
        ctpTheirs.reset(reader, theirs.getTree());
        ctpResolved.reset(reader, resolved.getTree());

        // this map will contain 3 diffs for each file/path:
        // 0 - diff between base and "ours" ("ours" depends on patch kind and it's really "ours" in P-Patch,
        //     because patch change is cherry-picked on top of custom change. In R-Patch, custom changes come after
        //     patch, so they're called "theirs" in diff/git terminology)
        // 1 - diff between base and "theirs" (see above)
        // 2 - diff between base and resolved, effective and final state of history
        Map<String, DiffEntry[]> report = new LinkedHashMap<>();

        // 1. base -> ours
        TreeWalk walk = new TreeWalk(reader);
        walk.addTree(ctpBase);
        walk.addTree(ctpOurs);
        walk.setRecursive(true);
        List<DiffEntry> diffs = DiffEntry.scan(walk);
        diffs.forEach(de -> collect(report, de, 0));

        // 2. base -> theirs
        walk.reset();
        ctpBase.reset(reader, base.getTree());
        walk.addTree(ctpBase);
        walk.addTree(ctpTheirs);
        walk.setRecursive(true);
        diffs = DiffEntry.scan(walk);
        diffs.forEach(de -> collect(report, de, 1));

        // 3. base -> resolved
        walk.reset();
        ctpBase.reset(reader, base.getTree());
        walk.addTree(ctpBase);
        walk.addTree(ctpResolved);
        walk.setRecursive(true);
        diffs = DiffEntry.scan(walk);
        diffs.forEach(de -> collect(report, de, 2));

        // report generation
        PatchData pd = patchResult.getPatchData();
        result.write(reportHeader.replace("@PATCH_ID@", pd.getId()));
        PatchReport pr = patchResult.getReport();

        result.write("<table class=\"summary\">\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Patch ID:</td><td>" + pr.getId() + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Patch type:</td><td>" + (pr.isRollup() ? "rollup" : "non-rollup") + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Installation date:</td><td>" + DATE.format(pr.getTimestamp()) + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Bundles updated</td><td>" + pr.getUpdatedBundles() + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Features updated</td><td>" + pr.getUpdatedFeatures() + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Features removed</td><td>" + pr.getRemovedFeatures() + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">Features overriden</td><td>" + pr.getOverridenFeatures() + "</td>\n"
                + "        </tr>\n"
                + "        <tr>\n"
                + "            <td class=\"f\">File conflicts</td><td>" + conflicts.size() + "</td>\n"
                + "        </tr>\n"
                + "    </table>\n"
                + "</div>\n");

        if (conflicts.size() > 0) {
            result.write("<h1 class=\"header\">\n"
                    + "  <div>Conflicting files</div>\n"
                    + "</h1>\n");
        }

        for (Map.Entry<String, DiffEntry[]> e : report.entrySet()) {
            if (!conflicts.contains(e.getKey())) {
                // we don't care about diffs that aren't really conflicts
                continue;
            }

            result.write(fileHeader1);
            result.write(e.getKey());
            result.write(fileHeader2);
            // we have max 3 entries (not all entries may be present
            result.write("<td class=\"side\">\n"
                    + "          <div class=\"header\">Custom version</div>\n"
                    + "          <div class=\"content" + (e.getValue()[0] != null ? "" : " empty") + "\">");
            if (e.getValue()[0] != null) {
                // custom change
                diff(git, reader, e.getValue()[0], result);
            } else {
                result.write("No change");
            }
            result.write("</div>\n"
                    + "        </td>");
            result.write("<td class=\"side\">\n"
                    + "          <div class=\"header\">Patch</div>\n"
                    + "          <div class=\"content" + (e.getValue()[1] != null ? "" : " empty") + "\">");
            if (e.getValue()[1] != null) {
                // patch change
                diff(git, reader, e.getValue()[1], result);
            } else {
                result.write("No change");
            }
            result.write("</div>\n"
                    + "        </td>");
            result.write("<td class=\"side\">\n"
                    + "          <div class=\"header\">Final version</div>\n"
                    + "          <div class=\"content" + (e.getValue()[2] != null ? "" : " empty") + "\">");
            if (e.getValue()[2] != null) {
                // effective change - should always be available
                // or maybe not when both patch and user removed the file?
                diff(git, reader, e.getValue()[2], result);
            } else {
                result.write("No change");
            }
            result.write("</div>\n"
                    + "        </td>");
            result.write(fileFooter);
        }

        result.write(reportFooter);
    }

    /**
     * Puts a {@link DiffEntry} for given path and index
     * @param report
     * @param de
     * @param idx
     */
    private static void collect(Map<String, DiffEntry[]> report, DiffEntry de, int idx) {
        String path = null;
        switch (de.getChangeType()) {
            case ADD:
            case MODIFY:
                path = de.getNewPath();
                break;
            case DELETE:
                path = de.getOldPath();
                break;
            case RENAME:
            case COPY:
            default:
                break;
        }
        if (path != null) {
            report.computeIfAbsent(path, p -> new DiffEntry[3])[idx] = de;
        }
    }

    private static void diff(Git git, ObjectReader reader, DiffEntry diff, Writer result) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DiffFormatter df = new DiffFormatter(baos);
        df.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setRepository(git.getRepository());
        df.format(diff);
//        System.out.println(new String(baos.toByteArray()));

        AbbreviatedObjectId id1 = diff.getOldId();
        AbbreviatedObjectId id2 = diff.getNewId();

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
                result.write("<div class=\"edit unchanged\">");
            }
            while (aCur < curEdit.getBeginA()) {
                result.write(html(rt1.getString(aCur++)) + "\n");
            }
            if (prolog) {
                result.write("</div>");
            }
            if (curEdit.getType() == Edit.Type.INSERT) {
                result.write("<div class=\"edit added\">");
                for (int i = curEdit.getBeginB(); i < curEdit.getEndB(); i++) {
                    result.write(html(rt2.getString(i)) + "\n");
                }
                result.write("</div>");
            }
            if (curEdit.getType() == Edit.Type.REPLACE) {
                result.write("<div class=\"edit changed\"><div class=\"edit removed\">");
                for (int i = curEdit.getBeginA(); i < curEdit.getEndA(); i++) {
                    result.write(html(rt1.getString(i)) + "\n");
                }
                result.write("</div><div class=\"edit added\">");
                for (int i = curEdit.getBeginB(); i < curEdit.getEndB(); i++) {
                    result.write(html(rt2.getString(i)) + "\n");
                }
                aCur = curEdit.getEndA();
                result.write("</div></div>");
            }
            if (curEdit.getType() == Edit.Type.DELETE) {
                result.write("<div class=\"edit changed\"><div class=\"edit removed\">");
                for (int i = curEdit.getBeginA(); i < curEdit.getEndA(); i++) {
                    result.write(html(rt1.getString(i)) + "\n");
                }
                aCur = curEdit.getEndA();
                result.write("</div></div>");
            }
        }
        boolean prolog = aCur < rt1.size();
        if (prolog) {
            result.write("<div class=\"edit unchanged\">");
        }
        while (aCur < rt1.size()) {
            result.write(html(rt1.getString(aCur++)) + "\n");
        }
        if (prolog) {
            result.write("</div>");
        }
    }

    private static String html(String v) {
        if (v != null) {
            return v.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        }
        return null;
    }

}

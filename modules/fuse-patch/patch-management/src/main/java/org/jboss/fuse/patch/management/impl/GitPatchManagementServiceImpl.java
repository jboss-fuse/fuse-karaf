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
package org.jboss.fuse.patch.management.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.karaf.features.LocationPattern;
import org.apache.karaf.features.internal.model.processing.BundleReplacements;
import org.apache.karaf.features.internal.model.processing.FeatureReplacements;
import org.apache.karaf.features.internal.model.processing.FeaturesProcessing;
import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.jboss.fuse.patch.management.Artifact;
import org.jboss.fuse.patch.management.BundleUpdate;
import org.jboss.fuse.patch.management.CVE;
import org.jboss.fuse.patch.management.EnvService;
import org.jboss.fuse.patch.management.EnvType;
import org.jboss.fuse.patch.management.ManagedPatch;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchData;
import org.jboss.fuse.patch.management.PatchDetailsRequest;
import org.jboss.fuse.patch.management.PatchException;
import org.jboss.fuse.patch.management.PatchKind;
import org.jboss.fuse.patch.management.PatchManagement;
import org.jboss.fuse.patch.management.PatchResult;
import org.jboss.fuse.patch.management.Pending;
import org.jboss.fuse.patch.management.Utils;
import org.jboss.fuse.patch.management.conflicts.ConflictResolver;
import org.jboss.fuse.patch.management.conflicts.DiffUtils;
import org.jboss.fuse.patch.management.conflicts.PropertiesFileResolver;
import org.jboss.fuse.patch.management.conflicts.Resolver;
import org.jboss.fuse.patch.management.conflicts.ResolverEx;
import org.jboss.fuse.patch.management.io.EOLFixingFileOutputStream;
import org.jboss.fuse.patch.management.io.EOLFixingFileUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.service.log.LogService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.eclipse.jgit.lib.IndexDiff.StageState.BOTH_MODIFIED;
import static org.jboss.fuse.patch.management.Utils.closeQuietly;
import static org.jboss.fuse.patch.management.Utils.getPermissionsFromUnixMode;
import static org.jboss.fuse.patch.management.Utils.getSystemRepository;
import static org.jboss.fuse.patch.management.Utils.mkdirs;
import static org.jboss.fuse.patch.management.Utils.mvnurlToArtifact;
import static org.jboss.fuse.patch.management.Utils.stripSymbolicName;
import static org.jboss.fuse.patch.management.Utils.updateKarafPackageVersion;

/**
 * <p>An implementation of Git-based patch management system. Deals with patch distributions and their unpacked
 * content.</p>
 * <p>This class delegates lower-level operations to {@link GitPatchRepository} and performs more complex git
 * operations in temporary clone+working copies.</p>
 */
public class GitPatchManagementServiceImpl implements PatchManagement, GitPatchManagementService {

    private static final String[] MANAGED_DIRECTORIES = new String[] {
            "bin", "etc", "lib", "welcome-content"
    };

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("patch-management/(\\d+\\.\\d+\\.\\d+(?:\\.[^. =]+)?)");

    private static final String MARKER_BASELINE_COMMIT_PATTERN = "[PATCH/baseline] Installing baseline-%s";
    private static final String MARKER_BASELINE_CHILD_COMMIT_PATTERN = "[PATCH/baseline] Installing baseline-child-%s";

    private static final String MARKER_BASELINE_RESET_OVERRIDES_PATTERN
            = "[PATCH/baseline] baseline-%s - resetting overrides";
    private static final String MARKER_BASELINE_REPLACE_PATCH_FEATURE_PATTERN
            = "[PATCH/baseline] baseline-%s - switching to patch feature repository %s";

    /* Patterns for rollup patch installation */
    private static final String MARKER_R_PATCH_INSTALLATION_PATTERN = "[PATCH] Installing rollup patch %s";
    private static final String MARKER_R_PATCH_RESET_OVERRIDES_PATTERN = "[PATCH] Rollup patch %s - resetting overrides";

    /* Patterns for non-rollup patch installation */
    private static final String MARKER_P_PATCH_INSTALLATION_PREFIX = "[PATCH] Installing patch ";
    private static final String MARKER_P_PATCH_INSTALLATION_PATTERN = MARKER_P_PATCH_INSTALLATION_PREFIX + "%s";

    /** A pattern of commit message when installing patch-management (this) bundle in etc/startup.properties */
    private static final String MARKER_PATCH_MANAGEMENT_INSTALLATION_COMMIT_PATTERN
            = "[PATCH/management] patch-management-%s.jar installed in etc/startup.properties";

    /** Commit message when applying user changes to managed directories */
    private static final String MARKER_USER_CHANGES_COMMIT = "[PATCH] Apply user changes";

    private static final Pattern FEATURES_FILE = Pattern.compile(".+(?:-features(?:-core)?|-karaf)$");

    /* patch installation support */

    protected Map<String, Git> pendingTransactions = new HashMap<>();
    protected Map<String, PatchKind> pendingTransactionsTypes = new HashMap<>();

    protected Map<String, BundleListener> pendingPatchesListeners = new HashMap<>();

    private BundleContext bundleContext;
    private BundleContext systemContext;

    private EnvType env = EnvType.UNKNOWN;

    private GitPatchRepository gitPatchRepository;
    private ConflictResolver conflictResolver = new ConflictResolver();
    private EnvService envService;

    private AtomicBoolean aligning = new AtomicBoolean(false);

    // ${karaf.home}
    private File karafHome;
    // ${karaf.base}
    private File karafBase;
    // ${karaf.data}
    private File karafData;
    // ${karaf.etc} (absolute)
    private File karafEtc;
    // main patches directory at ${fuse.patch.location} (defaults to ${karaf.home}/patches)
    private File patchesDir;

    // files to read feature processing instructions from - they do not have to exist
    private String featureProcessing;
    private String featureProcessingVersions;

    // latched when git repository is initialized
    private CountDownLatch initialized = new CountDownLatch(1);

    // synchronization of "ensuring" operation, where git repository status is verified and (possibly) changed
    private Lock ensuringLock = new ReentrantLock();

    public GitPatchManagementServiceImpl() throws IOException {

    }

    /**
     * <p>Creates patch management service</p>
     * <p>It checks the environment it's running in and use different strategies to initialize low level
     * structures - like the place where patch management history is kept</p>
     * @param context
     */
    public GitPatchManagementServiceImpl(BundleContext context) throws IOException {
        this.bundleContext = context;
        this.systemContext = context.getBundle(0).getBundleContext();
        karafHome = new File(systemContext.getProperty("karaf.home"));
        karafBase = new File(systemContext.getProperty("karaf.base"));
        karafData = new File(systemContext.getProperty("karaf.data"));
        karafEtc = new File(systemContext.getProperty("karaf.etc"));

        envService = new DefaultEnvService(systemContext, karafHome, karafBase);
        env = envService.determineEnvironmentType();

        if (env == EnvType.UNKNOWN) {
            return;
        }

        String patchLocation = systemContext.getProperty("fuse.patch.location");
        if (patchLocation == null) {
            if (env == EnvType.STANDALONE_CHILD) {
                // instance:create child shares patch git repository with parent
                patchLocation = new File(karafHome, "patches").getCanonicalPath();
            } else {
                patchLocation = new File(karafBase, "patches").getCanonicalPath();
            }
        }
        patchesDir = new File(patchLocation);
        if (!patchesDir.isDirectory()) {
            Utils.mkdirs(patchesDir);
        }

        // always init/open local (${karaf.base}/patches/.management/history) repo
        File patchRepositoryLocation = new File(patchesDir, GitPatchRepositoryImpl.MAIN_GIT_REPO_LOCATION);

        GitPatchRepositoryImpl repository = new GitPatchRepositoryImpl(env, patchRepositoryLocation,
                karafHome, karafBase, karafData, patchesDir);
        setGitPatchRepository(repository);

        File featuresConfiguration = new File(karafEtc, "org.apache.karaf.features.cfg");
        String featureProcessingLocation = null;
        String featureProcessingVersionsLocation = null;
        if (featuresConfiguration.isFile()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(featuresConfiguration)) {
                props.load(fis);
            }
            featureProcessingLocation = props.getProperty("featureProcessing");
            featureProcessingVersionsLocation = props.getProperty("featureProcessingVersions");
        }
        if (featureProcessingLocation == null || !new File(karafEtc, featureProcessingLocation).isFile()) {
            featureProcessing = "org.apache.karaf.features.xml";
        } else {
            featureProcessing = featureProcessingLocation;
        }
        if (featureProcessingVersionsLocation == null) {
            featureProcessingVersions = "versions.properties";
        } else {
            featureProcessingVersions = featureProcessingVersionsLocation;
        }
    }

    public GitPatchRepository getGitPatchRepository() {
        return gitPatchRepository;
    }

    public void setGitPatchRepository(GitPatchRepository repository) {
        this.gitPatchRepository = repository;
    }

    @Override
    public List<Patch> listPatches(boolean details) throws PatchException {
        List<Patch> patches = new LinkedList<>();
        File[] patchDescriptors = patchesDir.listFiles((dir, name) ->
                name.endsWith(".patch") && new File(dir, name).isFile());

        try {
            if (patchDescriptors != null) {
                for (File pd : patchDescriptors) {
                    Patch p = loadPatch(pd, details);
                    patches.add(p);
                }
            }
        } catch (IOException e) {
            throw new PatchException(e.getMessage(), e);
        }

        return patches;
    }

    /**
     * Retrieves patch information from existing file
     * @param patchDescriptor existing file with patch descriptor (<code>*.patch</code> file)
     * @param details whether the returned {@link Patch} should contain {@link ManagedPatch} information
     * @return
     * @throws IOException
     */
    private Patch loadPatch(File patchDescriptor, boolean details) throws IOException {
        Patch p = new Patch();

        if (!patchDescriptor.exists() || !patchDescriptor.isFile()) {
            return null;
        }

        PatchData data = PatchData.load(new FileInputStream(patchDescriptor));
        p.setPatchData(data);

        File patchDirectory = new File(patchesDir, FilenameUtils.getBaseName(patchDescriptor.getName()));
        if (patchDirectory.exists() && patchDirectory.isDirectory()) {
            // not every descriptor downloaded may be a ZIP file, not every patch has content
            data.setPatchDirectory(patchDirectory);

            File featureOverridesLocation = new File(patchDirectory, "org.apache.karaf.features.xml");
            if (featureOverridesLocation.isFile()) {
                // This patch file ships additional feature overrides - let's unmarshall them, so we have
                // them available during all patch operations
                try {
                    FeaturesProcessing featureOverrides = InternalUtils.loadFeatureProcessing(featureOverridesLocation, null);
                    List<String> overrides = new LinkedList<>();
                    if (featureOverrides.getFeatureReplacements().getReplacements() != null) {
                        featureOverrides.getFeatureReplacements().getReplacements()
                                .forEach(of -> overrides.add(of.getFeature().getId()));
                    }
                    data.setFeatureOverrides(overrides);
                } catch (Exception e) {
                    Activator.log(LogService.LOG_WARNING, "Problem loading org.apache.karaf.features.xml from patch " + data.getId() + ": " + e.getMessage());
                }
            }
        }
        data.setPatchLocation(patchesDir);

        File resultFile = new File(patchesDir, FilenameUtils.getBaseName(patchDescriptor.getName()) + ".patch.result");
        if (resultFile.exists() && resultFile.isFile()) {
            PatchResult result = PatchResult.load(data, new FileInputStream(resultFile));
            p.setResult(result);
        }

        if (details) {
            ManagedPatch mp = gitPatchRepository.getManagedPatch(data.getId());
            p.setManagedPatch(mp);
        }

        return p;
    }

    @Override
    public Patch loadPatch(PatchDetailsRequest request) throws PatchException {
        File descriptor = new File(patchesDir, request.getPatchId() + ".patch");
        try {
            Patch patch = loadPatch(descriptor, true);
            if (patch == null) {
                return null;
            }
            Git repo = gitPatchRepository.findOrCreateMainGitRepository();
            List<DiffEntry> diff = null;
            if (request.isFiles() || request.isDiff()) {
                // fetch the information from git
                ObjectId commitId = repo.getRepository().resolve(patch.getManagedPatch().getCommitId());
                RevCommit commit = new RevWalk(repo.getRepository()).parseCommit(commitId);
                diff = gitPatchRepository.diff(repo, commit.getParent(0), commit);
            }
            if (request.isBundles()) {
                // it's already in PatchData
            }
            if (request.isFiles() && diff != null) {
                for (DiffEntry de : diff) {
                    DiffEntry.ChangeType ct = de.getChangeType();
                    String newPath = de.getNewPath();
                    String oldPath = de.getOldPath();
                    switch (ct) {
                        case ADD:
                            patch.getManagedPatch().getFilesAdded().add(newPath);
                            break;
                        case MODIFY:
                            patch.getManagedPatch().getFilesModified().add(newPath);
                            break;
                        case DELETE:
                            patch.getManagedPatch().getFilesRemoved().add(oldPath);
                            break;
                        default:
                            break;
                    }
                }
            }
            if (request.isDiff() && diff != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(baos);
                formatter.setContext(4);
                formatter.setRepository(repo.getRepository());
                for (DiffEntry de : diff) {
                    formatter.format(de);
                }
                formatter.flush();
                patch.getManagedPatch().setUnifiedDiff(new String(baos.toByteArray(), "UTF-8"));
            }
            return patch;
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    @Override
    public List<PatchData> fetchPatches(URL url) throws PatchException {
        try {
            List<PatchData> patches = new ArrayList<>(1);

            File patchFile = new File(patchesDir, Long.toString(System.currentTimeMillis()) + ".patch.tmp");
            InputStream input = url.openStream();
            FileOutputStream output = new FileOutputStream(patchFile);
            ZipFile zf = null;
            try {
                IOUtils.copy(input, output);
            } finally {
                Utils.closeQuietly(input);
                Utils.closeQuietly(output);
            }
            try {
                zf = new ZipFile(patchFile);
            } catch (IOException ignored) {
                if (!FilenameUtils.getExtension(url.getFile()).equals("patch")) {
                    throw new PatchException("Patch should be ZIP file or *.patch descriptor");
                }
            }

            // patchFile may "be" a patch descriptor or be a ZIP file containing descriptor
            PatchData patchData = null;
            // in case patch ZIP file has no descriptor, we'll "generate" patch data on the fly
            // no descriptor -> assume we have rollup patch or even full, new distribution
            PatchData fallbackPatchData = new PatchData(FilenameUtils.getBaseName(url.getPath()));
            fallbackPatchData.setGenerated(true);
            Bundle thisBundle = FrameworkUtil.getBundle(this.getClass());
            if (thisBundle != null) {
                fallbackPatchData.setServiceVersion(thisBundle.getVersion().toString());
            }
            fallbackPatchData.setRollupPatch(true);
            fallbackPatchData.setPatchDirectory(new File(patchesDir, fallbackPatchData.getId()));
            fallbackPatchData.setPatchLocation(patchesDir);

            List<CVE> cves = new LinkedList<>();

            if (zf != null) {
                File systemRepo = getSystemRepository(karafHome, systemContext);
                try {
                    List<ZipArchiveEntry> otherResources = new LinkedList<>();
                    boolean skipRootDir = false;
                    for (Enumeration<ZipArchiveEntry> e = zf.getEntries(); e.hasMoreElements();) {
                        ZipArchiveEntry entry = e.nextElement();
                        if (!skipRootDir && entry.isDirectory() && entry.getName().startsWith("fuse-karaf-")) {
                            skipRootDir = true;
                        }
                        if (entry.isDirectory() || entry.isUnixSymlink()) {
                            continue;
                        }
                        String name = entry.getName();
                        if (skipRootDir) {
                            name = name.substring(name.indexOf('/') + 1);
                        }
                        if (!name.contains("/") && name.endsWith(".patch")) {
                            // patch descriptor in ZIP's root directory
                            if (patchData == null) {
                                // load data from patch descriptor inside ZIP. This may or may not be a rollup
                                // patch
                                File target = new File(patchesDir, name);
                                extractZipEntry(zf, entry, target);
                                patchData = loadPatchData(target);

                                // ENTESB-4600: try checking the target version of the patch
                                Version version = Utils.findVersionInName(patchData.getId());
                                if (version.getMajor() == 6 && version.getMinor() == 1) {
                                    throw new PatchException("Can't install patch \"" + patchData.getId() + "\", it is released for version 6.x of the product");
                                }

                                patchData.setGenerated(false);
                                patchData.setServiceVersion(fallbackPatchData.getServiceVersion());
                                File targetDirForPatchResources = new File(patchesDir, patchData.getId());
                                patchData.setPatchDirectory(targetDirForPatchResources);
                                patchData.setPatchLocation(patchesDir);
                                File dest = new File(patchesDir, patchData.getId() + ".patch");
                                if (dest.isFile()) {
                                    Activator.log(LogService.LOG_WARNING, "Patch with ID " + patchData.getId() + " was already added. Overriding existing patch.");
                                }
                                target.renameTo(dest);
                                patches.add(patchData);
                            } else {
                                throw new PatchException(
                                        String.format("Multiple patch descriptors: already have patch %s and now encountered entry %s",
                                                patchData.getId(), name));
                            }
                        } else {
                            File target = null;
                            String relativeName = null;
                            if (name.startsWith("system/")) {
                                // copy to ${karaf.default.repository}
                                relativeName = name.substring("system/".length());
                                target = new File(systemRepo, relativeName);
                            } else if (name.startsWith("repository/")) {
                                // copy to ${karaf.default.repository}
                                relativeName = name.substring("repository/".length());
                                target = new File(systemRepo, relativeName);
                            } else {
                                // other files that should be applied to ${karaf.home} when the patch is installed
                                otherResources.add(entry);
                            }
                            if (target != null) {
                                // we unzip to system repository
                                extractAndTrackZipEntry(fallbackPatchData, zf, entry, target, skipRootDir);

                                // CVE metadata handling
                                if (relativeName.startsWith("org/jboss/redhat-fuse/fuse-karaf-patch-metadata/")) {
                                    String metadata = relativeName.substring("org/jboss/redhat-fuse/fuse-karaf-patch-metadata/".length());
                                    if (metadata.contains("/") && metadata.endsWith(".xml")) {
                                        // should be <metadata xmlns="urn:redhat:fuse:patch-metadata:1"> XML document
                                        cves.addAll(parseMetadata(target));
                                    }
                                }
                            }
                        }
                    }

                    File targetDirForPatchResources = new File(patchesDir, patchData == null ? fallbackPatchData.getId() : patchData.getId());
                    // now copy non-maven resources (we should now know where to copy them)
                    for (ZipArchiveEntry entry : otherResources) {
                        String name = entry.getName();
                        if (skipRootDir) {
                            name = name.substring(name.indexOf('/'));
                        }
                        File target = new File(targetDirForPatchResources, name);
                        extractAndTrackZipEntry(fallbackPatchData, zf, entry, target, skipRootDir);
                    }
                } finally {
                    zf.close();
                    patchFile.delete();
                }
            } else {
                // If the file is not a zip/jar, assume it's a single patch file
                patchData = loadPatchData(patchFile);
                // no patch directory - no attached content, assuming only references to bundles
                patchData.setPatchDirectory(null);
                patchFile.renameTo(new File(patchesDir, patchData.getId() + ".patch"));
                patches.add(patchData);
            }

            if (patches.size() == 0) {
                // let's use generated patch descriptor
                File generatedPatchDescriptor = new File(patchesDir, fallbackPatchData.getId() + ".patch");
                FileOutputStream out = new FileOutputStream(generatedPatchDescriptor);

                // to prevent patch:add for a random ZIP file (and treat it as Rollup patch), let's verify the
                // existence of some fundamental files
                List<String> files = fallbackPatchData.getFiles();
                boolean rollup = files.contains("etc/version.properties");
                rollup &= files.contains("bin/fuse");
                rollup &= files.contains("etc/org.apache.karaf.features.xml");
                fallbackPatchData.setRollupPatch(rollup);
                try {
                    fallbackPatchData.storeTo(out);
                } finally {
                    Utils.closeQuietly(out);
                }
                patches.add(fallbackPatchData);
            } else {
                // should be only one - from ZIP
                for (PatchData pd : patches) {
                    pd.getCves().addAll(cves);
                    if (cves.size() > 0) {
                        // update patch data with CVEs found
                        try (FileOutputStream out = new FileOutputStream(new File(patchesDir, pd.getId() + ".patch"))) {
                            pd.storeTo(out);
                        }
                    }
                }
            }

            return patches;
        } catch (IOException e) {
            throw new PatchException("Unable to download patch from url " + url, e);
        }
    }

    private List<CVE> parseMetadata(File target) {
        List<CVE> cves = new LinkedList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(false);
            try {
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (ParserConfigurationException ignored) {
            }
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document d = db.parse(target);
            NodeList nl = d.getDocumentElement().getChildNodes();
            int c = nl.getLength();
            for (int i = 0; i < c; i++) {
                Node n = nl.item(i);
                if (n instanceof Element && n.getLocalName().equals("cves")) {
                    nl = n.getChildNodes();
                    c = nl.getLength();
                    for (int j = 0; j < c; j++) {
                        n = nl.item(j);
                        if (n instanceof Element && n.getLocalName().equals("cve")) {
                            CVE cve = new CVE();
                            cve.setId(((Element) n).getAttribute("id"));
                            cve.setDescription(((Element) n).getAttribute("description"));
                            cve.setCveLink(((Element) n).getAttribute("cve-link"));
                            cve.setBzLink(((Element) n).getAttribute("bz-link"));
                            cves.add(cve);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Activator.log(LogService.LOG_WARNING, "Problem parsing Patch Metadata: " + e.getMessage());
        }
        return cves;
    }

    /**
     * When extracting patch-ZIP entry, track the item in {@link PatchData static patch data}
     * @param patchData
     * @param zip
     * @param entry
     * @param target
     */
    public static void extractAndTrackZipEntry(PatchData patchData, ZipFile zip, ZipArchiveEntry entry, File target,
                                               boolean skipRootDir) throws IOException {
        extractZipEntry(zip, entry, target);

        String name = entry.getName();
        if (skipRootDir) {
            name = name.substring(name.indexOf('/') + 1);
        }
        if (name.startsWith("system/") || name.startsWith("repository/")) {
            // Maven artifact: a bundle, feature definition file, configuration file
            if (name.startsWith("system/")) {
                name = name.substring("system/".length());
            } else if (name.startsWith("repository/")) {
                name = name.substring("repository/".length());
            }
            String fileName = FilenameUtils.getBaseName(name);
            String extension = FilenameUtils.getExtension(name);

            name = Utils.pathToMvnurl(name);
            if ("jar".equals(extension) || "war".equals(extension)) {
                patchData.getBundles().add(name);
            } else if ("xml".equals(extension) && FEATURES_FILE.matcher(fileName).matches()) {
                patchData.getFeatureFiles().add(name);
            } else if (name != null) {
                // must be a config, a POM (irrelevant) or other maven artifact (like ZIP)
                patchData.getOtherArtifacts().add(name);
            }
        } else {
            // ordinary entry to be applied to ${karaf.root}
            patchData.getFiles().add(name);
        }
    }

    /**
     * Extracts ZIP entry into target file. Sets correct file permissions if found in ZIP entry.
     * @param zip
     * @param entry
     * @param target
     * @throws IOException
     */
    public static void extractZipEntry(ZipFile zip, ZipArchiveEntry entry, File target) throws IOException {
        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) {
            throw new IOException(String.format("Can't create %s directory to extract %s",
                    target.getParentFile(), entry.getName()));
        }
        FileOutputStream targetOutputStream = new FileOutputStream(target);
        IOUtils.copyLarge(zip.getInputStream(entry), targetOutputStream);
        closeQuietly(targetOutputStream);
        if (Files.getFileAttributeView(target.toPath(), PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(target.toPath(), getPermissionsFromUnixMode(target, entry.getUnixMode()));
        }
    }

    /**
     * Unpacks a ZIP file to targetDirectory
     * @param zipFile
     * @param targetDirectory
     * @param skipInitialDirectories how many levels of a path to skip when unpacking (like skipping base directory inside ZIP)
     * @throws IOException
     */
    public static void unpack(File zipFile, File targetDirectory, int skipInitialDirectories) throws IOException {

        try (ZipFile zf = new ZipFile(zipFile)) {
            for (Enumeration<ZipArchiveEntry> e = zf.getEntries(); e.hasMoreElements();) {
                ZipArchiveEntry entry = e.nextElement();
                String name = entry.getName();
                int skip = skipInitialDirectories;
                while (skip-- > 0) {
                    name = name.substring(name.indexOf('/') + 1);
                }
                if (entry.isDirectory()) {
                    mkdirs(new File(targetDirectory, name));
                } else /*if (!entry.isUnixSymlink())*/ {
                    File file = new File(targetDirectory, name);
                    mkdirs(file.getParentFile());
                    FileOutputStream output = new EOLFixingFileOutputStream(targetDirectory, file);
                    IOUtils.copyLarge(zf.getInputStream(entry), output);
                    closeQuietly(output);
                    if (Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class) != null) {
                        Files.setPosixFilePermissions(file.toPath(), getPermissionsFromUnixMode(file, entry.getUnixMode()));
                    }
                }
            }
        }
    }

    /**
     * Reads content of patch descriptor into non-(yet)-managed patch data structure
     * @param patchDescriptor
     * @return
     */
    private PatchData loadPatchData(File patchDescriptor) throws IOException {
        Properties properties = new Properties();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(patchDescriptor);
            properties.load(inputStream);
            boolean ok = properties.containsKey("id") && properties.containsKey("bundle.count");
            if (!ok) {
                throw new PatchException("Patch descriptor is not valid");
            }
            return PatchData.load(properties);
        } finally {
            Utils.closeQuietly(inputStream);
        }
    }

    /**
     * <p>This method turns static information about a patch into managed patch - i.e., patch added to git
     * repository.</p>
     *
     * <p>Such patch has its own branch ready to be merged (when patch is installed). Before installation we can verify
     * the patch,
     * examine the content, check the differences, conflicts and perform simulation (merge to temporary branch created
     * from main patch branch)</p>
     *
     * <p>The strategy is as follows:<ul>
     *     <li><em>main patch branch</em> in git repository tracks all changes (from baselines, patch-management
     *     system, patches and user changes)</li>
     *     <li>Initially there are 3 commits: baseline, patch-management bundle installation in etc/startup.properties,
     *     initial user changes</li>
     *     <li>We always <strong>tag the baseline commit</strong></li>
     *     <li>User changes may be applied each time Framework is restarted</li>
     *     <li>When we add a patch, we create <em>named branch</em> from the <strong>latest baseline</strong></li>
     *     <li>When we install a patch, we <strong>merge</strong> the patch branch with the <em>main patch branch</em>
     *     (that may contain additional user changes)</li>
     *     <li>When patch ZIP contains new baseline distribution, after merging patch branch, we tag the merge commit
     *     in <em>main patch branch</em> branch as new baseline</li>
     *     <li>Branches for new patches will then be created from new baseline commit</li>
     * </ul></p>
     * @param patchData
     * @return
     */
    @Override
    public Patch trackPatch(PatchData patchData) throws PatchException {
        try {
            awaitInitialization();
        } catch (InterruptedException e) {
            throw new PatchException("Patch management system is not ready yet");
        }
        Git fork = null;
        try {
            Git mainRepository = gitPatchRepository.findOrCreateMainGitRepository();
            // prepare single fork for all the below operations
            fork = gitPatchRepository.cloneRepository(mainRepository, true);

            // 1. find current baseline
            RevTag latestBaseline = gitPatchRepository.findCurrentBaseline(fork);
            if (latestBaseline == null) {
                throw new PatchException("Can't find baseline distribution tracked in patch management. Is patch management initialized?");
            }

            // the commit from the patch should be available from main patch branch
            RevCommit commit = new RevWalk(fork.getRepository()).parseCommit(latestBaseline.getObject());

            // create dedicated branch for this patch. We'll immediately add patch content there so we can examine the
            // changes from the latest baseline
            gitPatchRepository.checkout(fork)
                    .setCreateBranch(true)
                    .setName("patch-" + patchData.getId())
                    .setStartPoint(commit)
                    .call();

            // copy patch resources (but not maven artifacts from system/ or repository/) to working copy
            if (patchData.getPatchDirectory() != null) {
                boolean removeTargetDir = patchData.isRollupPatch();
                final File workTree = fork.getRepository().getWorkTree();
                copyManagedDirectories(patchData.getPatchDirectory(), workTree, removeTargetDir, false, false);

                // this is the place to apply non-rollup patch file removals
                if (patchData.getFileRemovals() != null && patchData.getFileRemovals().size() > 0) {
                    Set<String> doNotTouch = new HashSet<>(patchData.getFileRemovals().keySet());
                    patchData.getFileRemovals().values().forEach(removalPattern -> {
                        // for example:
                        // file.0 = lib/ext/bcprov-jdk15on-1.68.jar
                        // file.0.delete = lib/ext/bcprov-jdk15on-*.jar
                        // file.1 = lib/ext/bcpkix-jdk15on-1.68.jar
                        // file.1.delete = lib/ext/bcpkix-jdk15on-*.jar
                        File targetDir = workTree;
                        String[] removalPatternSegments = removalPattern.split("/");
                        for (int s = 0; s < removalPatternSegments.length - 1; s++) {
                            // all path segments but the last should not contain wildcards
                            targetDir = new File(targetDir, removalPatternSegments[s]);
                        }
                        Pattern p = Pattern.compile(removalPatternSegments[removalPatternSegments.length - 1]
                                .replace(".", "\\.").replace("*", ".*"));
                        File[] files = targetDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                String name = f.getName();
                                boolean proceed = true;
                                for (String dnt : doNotTouch) {
                                    try {
                                        proceed &= !f.getCanonicalPath().endsWith(dnt);
                                    } catch (IOException ignored) {
                                    }
                                }
                                if (proceed && p.matcher(name).matches()) {
                                    f.delete();
                                }
                            }
                        }
                    });
                }
            }

            // add the changes
            fork.add().addFilepattern(".").call();

            // remove the deletes
            for (String missing : fork.status().call().getMissing()) {
                fork.rm().addFilepattern(missing).call();
            }

            // record information about other "patches" included in added patch (e.g., Fuse patch
            // may contain patches to instance:create based containers in standalone mode)
            StringWriter sw = new StringWriter();
            sw.append("# tags for patches included in \"").append(patchData.getId()).append("\"\n");
            for (String bundle : patchData.getBundles()) {
                // maybe we'll be discovering other kinds of patches, but now - only for instance:create based
                // containers that want to patch:install patches added in root containers
                if (bundle.contains("mvn:org.apache.karaf.instance/org.apache.karaf.instance.core/")) {
                    Artifact a = Utils.mvnurlToArtifact(bundle, true);
                    if (a != null) {
                        sw.append(String.format(EnvType.STANDALONE_CHILD.getBaselineTagFormat(), a.getVersion())).append("\n");
                    }
                    break;
                }
            }
            FileUtils.write(new File(fork.getRepository().getWorkTree(), "patch-info.txt"), sw.toString(), "UTF-8");
            fork.add().addFilepattern(".").call();

            // commit the changes (patch vs. baseline) to patch branch
            gitPatchRepository.prepareCommit(fork, String.format("[PATCH] Tracking patch %s", patchData.getId())).call();

            // push the patch branch
            gitPatchRepository.push(fork, "patch-" + patchData.getId());

            // for instance:create child containers
            trackBaselinesForChildContainers(fork);

            return new Patch(patchData, gitPatchRepository.getManagedPatch(patchData.getId()));
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        } finally {
            if (fork != null) {
                gitPatchRepository.closeRepository(fork, true);
            }
        }
    }

    /**
     * This service is published before it can initialize the system. It may be an issue in integration tests.
     */
    private void awaitInitialization() throws InterruptedException {
        initialized.await(30, TimeUnit.SECONDS);
    }

    @Override
    public String beginInstallation(PatchKind kind) {
        try {
            Git fork = gitPatchRepository.cloneRepository(gitPatchRepository.findOrCreateMainGitRepository(), true);
            Ref installationBranch = null;

            // let's pick up latest user changes
            applyUserChanges(fork);

            switch (kind) {
                case ROLLUP:
                    // create temporary branch from the current baseline - rollup patch installation is a rebase
                    // of existing user changes on top of new baseline (more precisely - cherry pick)
                    RevTag currentBaseline = gitPatchRepository.findCurrentBaseline(fork);
                    installationBranch = gitPatchRepository.checkout(fork)
                            .setName(String.format("patch-install-%s", GitPatchRepository.TS.format(new Date())))
                            .setCreateBranch(true)
                            .setStartPoint(currentBaseline.getTagName() + "^{commit}")
                            .call();
                    break;
                case NON_ROLLUP:
                    // create temporary branch from main-patch-branch/HEAD - non-rollup patch installation is cherry-pick
                    // of non-rollup patch commit over existing user changes - we can fast forward when finished
                    installationBranch = gitPatchRepository.checkout(fork)
                            .setName(String.format("patch-install-%s", GitPatchRepository.TS.format(new Date())))
                            .setCreateBranch(true)
                            .setStartPoint(gitPatchRepository.getMainBranchName())
                            .call();
                    break;
            }

            pendingTransactionsTypes.put(installationBranch.getName(), kind);
            pendingTransactions.put(installationBranch.getName(), fork);

            return installationBranch.getName();
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    @Override
    public void install(String transaction, Patch patch, List<BundleUpdate> bundleUpdatesInThisPatch) {
        transactionIsValid(transaction, patch);

        Git fork = pendingTransactions.get(transaction);

        // for report preparation purposes
        RevWalk walk = new RevWalk(fork.getRepository());
        RevCommit reportCommitBase;
        RevCommit reportCommitOurs;
        RevCommit reportCommitPatch;
        RevCommit reportCommitResolved;

        try {
            switch (pendingTransactionsTypes.get(transaction)) {
                case ROLLUP: {
                    Activator.log2(LogService.LOG_INFO, String.format("Installing rollup patch \"%s\"", patch.getPatchData().getId()));

                    // We can install only one rollup patch within single transaction
                    // and it is equal to cherry-picking all user changes on top of transaction branch
                    // after cherry-picking the commit from the rollup patch branch.
                    // Rollup patches do their own update to etc/startup.properties
                    // We're operating on patch branch, HEAD of the patch branch points to the baseline
                    ObjectId since = fork.getRepository().resolve("HEAD^{commit}");
                    reportCommitBase = walk.parseCommit(since);
                    // we'll pick all user changes between baseline and main patch branch
                    // we'll consider all real user changes and some P-patch changes if HF-patches install newer
                    // bundles than currently installed R-patch (very rare situation)
                    ObjectId to = fork.getRepository().resolve(gitPatchRepository.getMainBranchName() + "^{commit}");

                    // Custom changes: since..to
                    reportCommitOurs = walk.parseCommit(to);

                    Iterable<RevCommit> mainChanges = fork.log().addRange(since, to).call();
                    List<RevCommit> userChanges = new LinkedList<>();
                    // gather lines of HF patches - patches that have *only* bundle updates
                    // if any of HF patches provide newer version of artifact than currently installed R patch,
                    // we will leave the relevant line in etc/org.apache.karaf.features.xml
                    List<PatchData> hfChanges = new LinkedList<>();
                    for (RevCommit rc : mainChanges) {
                        if (isUserChangeCommit(rc)) {
                            userChanges.add(rc);
                        } else {
                            String hfPatchId = isHfChangeCommit(rc);
                            if (hfPatchId != null) {
                                hfChanges.add(gatherOverrides(hfPatchId, patch));
                            }
                        }
                    }


                    String patchRef = patch.getManagedPatch().getCommitId();
                    if (env == EnvType.STANDALONE_CHILD) {
                        // we're in a slightly different situation:
                        //  - patch was patch:added in root container
                        //  - its main commit should be used when patching full Fuse/AMQ container
                        //  - it created "side" commits (with tags) for this case of patching instance:create based containers
                        //  - those tags are stored in special patch-info.txt file within patch' commit
                        String patchInfo = gitPatchRepository.getFileContent(fork, patchRef, "patch-info.txt");
                        if (patchInfo != null) {
                            BufferedReader reader = new BufferedReader(new StringReader(patchInfo));
                            String line = null;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("#")) {
                                    continue;
                                }
                                Pattern p = Pattern.compile(env.getBaselineTagFormat().replace("%s", "(.*)"));
                                if (p.matcher(line).matches()) {
                                    // this means we have another commit/tag that we should chery-pick as a patch
                                    // for this standalone child container
                                    patchRef = line.trim();
                                }
                            }
                        } else {
                            // hmm, we actually can't patch standalone child container then...
                            Activator.log2(LogService.LOG_WARNING,
                                    String.format("Can't install rollup patch \"%s\" in instance:create-based container - no information about child container patch", patch.getPatchData().getId()));
                            return;
                        }
                    }

                    if (env == EnvType.STANDALONE) {
                        // pick the rollup patch
                        fork.cherryPick()
                                .include(fork.getRepository().resolve(patchRef))
                                .setNoCommit(true)
                                .call();

                        gitPatchRepository.prepareCommit(fork,
                                String.format(MARKER_R_PATCH_INSTALLATION_PATTERN, patch.getPatchData().getId())).call();
                    } else if (env == EnvType.STANDALONE_CHILD) {
                        // rebase on top of rollup patch
                        fork.reset()
                                .setMode(ResetCommand.ResetType.HARD)
                                .setRef("refs/tags/" + patchRef + "^{commit}")
                                .call();
                    }

                    // next commit - reset overrides - this is 2nd step of installing rollup patch
                    // if there are hot fix patches applied before rollup patch and the changes are newer (very rare
                    // situation), we have to add these overrides after patch' etc/org.apache.karaf.features.xml
                    // we always remove etc/overrides.properties
                    resetOverrides(fork, fork.getRepository().getWorkTree(), hfChanges);
                    fork.add().addFilepattern("etc/" + featureProcessing).call();
                    if (new File(fork.getRepository().getWorkTree(), "etc/" + featureProcessingVersions).isFile()) {
                        fork.add().addFilepattern("etc/" + featureProcessingVersions).call();
                    }
                    RevCommit c = gitPatchRepository.prepareCommit(fork,
                            String.format(MARKER_R_PATCH_RESET_OVERRIDES_PATTERN, patch.getPatchData().getId())).call();

                    // R-patch changes: since..c
                    reportCommitPatch = walk.parseCommit(c);

                    if (env == EnvType.STANDALONE) {
                        // tag the new rollup patch as new baseline
                        String newFuseVersion = determineVersion(fork.getRepository().getWorkTree());
                        fork.tag()
                                .setName(String.format(EnvType.STANDALONE.getBaselineTagFormat(), newFuseVersion))
                                .setObjectId(c)
                                .call();
                    }

                    // reapply those user changes that are not conflicting
                    // for each conflicting cherry-pick we do a backup of user files, to be able to restore them
                    // when rollup patch is rolled back
                    ListIterator<RevCommit> it = userChanges.listIterator(userChanges.size());
                    int prefixSize = Integer.toString(userChanges.size()).length();
                    int count = 1;

                    // when there are not user changes, the "resolved" point will be just after cherryPicking patch
                    // commit. If there are user changes - these will be latest
                    reportCommitResolved = c;
                    Set<String> conflicts = new LinkedHashSet<>();

                    while (it.hasPrevious()) {
                        RevCommit userChange = it.previous();
                        String prefix = String.format("%0" + prefixSize + "d-%s", count++, userChange.getName());
                        CherryPickResult result = fork.cherryPick()
                                .include(userChange)
                                .setNoCommit(true)
                                .call();

                        // ENTESB-5492: remove etc/overrides.properties if there is such file left from old patch
                        // mechanism
                        File overrides = new File(fork.getRepository().getWorkTree(), "etc/overrides.properties");
                        if (overrides.isFile()) {
                            overrides.delete();
                            fork.rm().addFilepattern("etc/overrides.properties").call();
                        }

                        // if there's conflict here, prefer patch version (which is "ours" (first) in this case)
                        Set<String> conflicting = handleCherryPickConflict(patch.getPatchData().getPatchDirectory(), fork, result, userChange,
                                false, PatchKind.ROLLUP, prefix, true, false);
                        if (conflicting != null) {
                            conflicts.addAll(conflicting);
                        }

                        // always commit even empty changes - to be able to restore user changes when rolling back
                        // rollup patch.
                        // commit has the original commit id appended to the message.
                        // when we rebase on OLDER baseline (rollback) we restore backed up files based on this
                        // commit id (from patches/patch-id.backup/number-commit directory)
                        String newMessage = userChange.getFullMessage() + "\n\n";
                        newMessage += prefix;
                        reportCommitResolved = gitPatchRepository.prepareCommit(fork, newMessage).call();

                        // we may have unadded changes - when file mode is changed
                        fork.reset().setMode(ResetCommand.ResetType.HARD).call();
                    }

                    // finally - let's get rid of all the tags related to non-rollup patches installed between
                    // previous baseline and previous HEAD, because installing rollup patch makes all previous P
                    // patches obsolete
                    RevCommit c1 = walk.parseCommit(since);
                    RevCommit c2 = walk.parseCommit(to);
                    Map<String, RevTag> tags = gitPatchRepository.findTagsBetween(fork, c1, c2);
                    for (Map.Entry<String, RevTag> entry : tags.entrySet()) {
                        if (entry.getKey().startsWith("patch-")) {
                            fork.tagDelete().setTags(entry.getKey()).call();
                            fork.push()
                                    .setRefSpecs(new RefSpec()
                                            .setSource(null)
                                            .setDestination("refs/tags/" + entry.getKey()))
                                    .call();

                            // and remove the patch itself
                            String id = entry.getKey().substring("patch-".length());
                            Patch pPatch = loadPatch(new PatchDetailsRequest(id));
                            if (pPatch != null) {
                                Activator.log2(LogService.LOG_DEBUG, "Deleting previously installed non-rollup patch " + id + ".");
                                delete(pPatch);
                            }
                        }
                    }

                    // we have 4 commits and we can now prepare the report
                    File reportFile = new File(patch.getPatchData().getPatchLocation(), patch.getPatchData().getId() + ".patch.result.html");
                    try (FileWriter writer = new FileWriter(reportFile)) {
                        DiffUtils.generateDiffReport(patch, patch.getResult(), fork, conflicts,
                                reportCommitBase, reportCommitOurs, reportCommitPatch, reportCommitResolved,
                                writer);
                    } catch (Exception e) {
                        Activator.log(LogService.LOG_WARNING, "Problem generatic patch report for patch " + patch.getPatchData().getId() + ": " + e.getMessage());
                    }

                    break;
                }
                case NON_ROLLUP: {
                    Activator.log2(LogService.LOG_INFO, String.format("Installing non-rollup patch \"%s\"", patch.getPatchData().getId()));

                    // simply cherry-pick patch commit to transaction branch
                    // non-rollup patches require manual change to artifact references in all files

                    // pick the non-rollup patch
                    RevCommit commit = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve(patch.getManagedPatch().getCommitId()));
                    CherryPickResult result = fork.cherryPick()
                            .include(commit)
                            .setNoCommit(true)
                            .call();
                    handleCherryPickConflict(patch.getPatchData().getPatchDirectory(), fork, result, commit,
                            true, PatchKind.NON_ROLLUP, null, true, false);

                    // there are several files in ${karaf.home} that need to be changed together with patch
                    // commit, to make them reference updated bundles (paths, locations, ...)
                    updateFileReferences(fork, patch.getPatchData(), bundleUpdatesInThisPatch);
                    updateOverrides(fork.getRepository().getWorkTree(), Collections.singletonList(patch.getPatchData()));
                    fork.add().addFilepattern(".").call();

                    // always commit non-rollup patch
                    RevCommit c = gitPatchRepository.prepareCommit(fork,
                        String.format(MARKER_P_PATCH_INSTALLATION_PATTERN, patch.getPatchData().getId())).call();

                    // we may have unadded changes - when file mode is changed
                    fork.reset().setMode(ResetCommand.ResetType.MIXED).call();
                    fork.reset().setMode(ResetCommand.ResetType.HARD).call();

                    // tag the installed patch (to easily rollback and to prevent another installation)
                    String tagName = String.format("patch-%s", patch.getPatchData().getId().replace(' ', '-'));
                    if (env == EnvType.STANDALONE_CHILD) {
                        tagName += "-" + gitPatchRepository.getStandaloneChildkarafName();
                    }
                    fork.tag()
                            .setName(tagName)
                            .setObjectId(c)
                            .call();

                    break;
                }
            }
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    /**
     * <p>Updates existing <code>etc/org.apache.karaf.features.xml</code> after installing single {@link PatchKind#NON_ROLLUP}
     * patch. Both bundle and feature replacements are taken into account.</p>
     * @param workTree
     * @param patches
     */
    private void updateOverrides(File workTree, List<PatchData> patches) throws IOException {
        File overrides = new File(workTree, "etc/" + featureProcessing);
        File versions = new File(workTree, "etc/" + featureProcessingVersions);

        // we need two different versions to detect whether the version is externalized in etc/versions.properties
        FeaturesProcessing fp1;
        FeaturesProcessing fp2;
        if (overrides.isFile()) {
            fp1 = InternalUtils.loadFeatureProcessing(overrides, versions);
            fp2 = InternalUtils.loadFeatureProcessing(overrides, null);
        } else {
            fp1 = fp2 = new FeaturesProcessing();
        }
        List<BundleReplacements.OverrideBundle> br1 = fp1.getBundleReplacements().getOverrideBundles();
        List<BundleReplacements.OverrideBundle> br2 = fp2.getBundleReplacements().getOverrideBundles();

        org.apache.felix.utils.properties.Properties props = null;
        boolean propertyChanged = false;
        if (versions.isFile()) {
            props = new org.apache.felix.utils.properties.Properties(versions);
        }

        for (PatchData patchData : patches) {
            for (String bundle : patchData.getBundles()) {
                Artifact artifact = mvnurlToArtifact(bundle, true);
                if (artifact == null) {
                    continue;
                }

                if (patchData.getOriginalGroupId(bundle) != null) {
                    artifact.setGroupId(patchData.getOriginalGroupId(bundle));
                }
                if (patchData.getOriginalArtifactId(bundle) != null) {
                    artifact.setArtifactId(patchData.getOriginalArtifactId(bundle));
                }

                // Compute patch bundle version and range
                Version oVer = Utils.getOsgiVersion(artifact.getVersion());
                String vr = patchData.getVersionRange(bundle);
                if (vr != null && !vr.isEmpty()) {
                    artifact.setVersion(vr);
                } else {
                    Version v1 = new Version(oVer.getMajor(), oVer.getMinor(), 0);
                    Version v2 = new Version(oVer.getMajor(), oVer.getMinor() + 1, 0);
                    artifact.setVersion(new VersionRange(VersionRange.LEFT_CLOSED, v1, v2, VersionRange.RIGHT_OPEN).toString());
                }

                // features processing file may contain e.g.,:
                // <bundle originalUri="mvn:org.jboss.fuse/fuse-zen/[1,2)/war"
                //         replacement="mvn:org.jboss.fuse/fuse-zen/${version.test2}/war" mode="maven" />
                // patch descriptor contains e.g.,:
                // bundle.0 = mvn:org.jboss.fuse/fuse-zen/1.2.0/war
                // bundle.0.range = [1.1,1.2)
                //
                // we will always match by replacement attribute, ignoring originalUri - the patch descriptor must be
                // prepared correctly

                int idx = 0;
                BundleReplacements.OverrideBundle existing = null;
                // we'll examine model with resolved property placeholders, but modify the other one
                for (BundleReplacements.OverrideBundle override : br1) {
                    LocationPattern lp = new LocationPattern(artifact.getCanonicalUri());
//                    if (lp.matches(override.getOriginalUri())) {
                    if (lp.matches(override.getReplacement())) {
                        // we've found existing override in current etc/org.apache.karaf.features.xml
                        // and the replacement URI matches. But if we have:
                        // <bundle originalUri="mvn:com.sun.xml.bind/jaxb-xjc/[2.3,2.4)"
                        //         replacement="mvn:org.apache.servicemix.bundles/org.apache.servicemix.bundles.jaxb-xjc/2.2.11_1"
                        //         mode="maven"/>
                        // and a new location for patched bundle:
                        //  - mvn:org.apache.servicemix.bundles/org.apache.servicemix.bundles.jaxb-impl/[2.2,2.3)
                        // we can't simply replace given override. We have to match original group and artifact id
                        Artifact oldOriginalUri = mvnurlToArtifact(override.getOriginalUri(), true);
                        // newOriginalUri will have group/artifact IDs set properly from patch descriptor
                        Artifact newOriginalUri = mvnurlToArtifact(artifact.getCanonicalUri(), true);
                        if (oldOriginalUri != null && newOriginalUri != null
                                && oldOriginalUri.getGroupId().equals(newOriginalUri.getGroupId())
                                && oldOriginalUri.getArtifactId().equals(newOriginalUri.getArtifactId())) {
                            existing = br2.get(idx);
                            break;
                        }
                    }
                    idx++;
                }
                if (existing == null) {
                    existing = new BundleReplacements.OverrideBundle();
                    br2.add(existing);
                }
                // either update existing override or configure a new one
                existing.setMode(BundleReplacements.BundleOverrideMode.MAVEN);
                existing.setOriginalUri(artifact.getCanonicalUri());
                String replacement = existing.getReplacement();
                if (replacement != null && replacement.contains("${")) {
                    // assume that we have existing replacement="mvn:org.jboss.fuse/fuse-zen/${version.test2}/war"
                    // so we can't change the replacement and instead we have to update properties
                    String property = null;
                    String value = null;

                    if (replacement.startsWith("mvn:")) {
                        LocationPattern existingReplacement = new LocationPattern(replacement);
                        property = existingReplacement.getVersionString().substring(existingReplacement.getVersionString().indexOf("${") + 2);
                        if (property.contains("}")) {
                            // it should...
                            property = property.substring(0, property.indexOf("}"));
                        }

                        LocationPattern newReplacement = new LocationPattern(bundle);
                        value = newReplacement.getVersionString();
                    } else {
                        // non-mvn? then we can't determine the version from non-mvn: URI...
                    }

                    // we are not changing replacement - we'll have to update properties
                    if (props != null && property != null) {
                        props.setProperty(property, value);
                        propertyChanged = true;
                    }
                } else {
                    existing.setReplacement(bundle);
                }
            }

            // feature overrides
            File featureOverridesLocation = new File(patchData.getPatchDirectory(), "org.apache.karaf.features.xml");
            if (featureOverridesLocation.isFile()) {
                FeaturesProcessing featureOverrides = InternalUtils.loadFeatureProcessing(featureOverridesLocation, null);
                Map<String, FeatureReplacements.OverrideFeature> patchedFeatures = new LinkedHashMap<>();
                List<FeatureReplacements.OverrideFeature> mergedOverrides = new LinkedList<>();
                featureOverrides.getFeatureReplacements().getReplacements()
                        .forEach(of -> patchedFeatures.put(of.getFeature().getId(), of));
                fp2.getFeatureReplacements().getReplacements().forEach(of -> {
                    FeatureReplacements.OverrideFeature override = patchedFeatures.remove(of.getFeature().getId());
                    mergedOverrides.add(override == null ? of : override);
                });
                // add remaining
                mergedOverrides.addAll(patchedFeatures.values());

                fp2.getFeatureReplacements().getReplacements().clear();
                fp2.getFeatureReplacements().getReplacements().addAll(mergedOverrides);
            }
        }

        if (propertyChanged) {
            props.save();
        }

        InternalUtils.saveFeatureProcessing(fp2, overrides, versions);
    }

    /**
     * <p>Removes <code>etc/overrides.properties</code> and possible changes <code>etc/org.apache.karaf.features.xml</code></p>
     * <p>Each baseline ships new feature repositories and from this point (or the point where new rollup patch
     * is installed) we should start with 0-sized overrides.properties in order to have easier non-rollup
     * patch installation - P-patch should not ADD overrides.properties - they have to only MODIFY it because
     * it's easier to revert such modification (in case P-patch is rolled back - probably in different order
     * than it was installed)</p>
     * @param karafHome
     * @throws IOException
     */
    private void resetOverrides(Git git, File karafHome, List<PatchData> overridesToKeep) throws IOException {
        File overrides = new File(karafHome, "etc/overrides.properties");
        if (overrides.isFile()) {
            // we just don't use etc/overrides.properties in Fuse 7/Karaf 4.2
            overrides.delete();
            git.rm().addFilepattern("etc/overrides.properties");
        }
        if (overridesToKeep != null && overridesToKeep.size() > 0) {
            // we'll add/modify <bundleReplacements>/<bundle> entries in etc/org.apache.karaf.features.xml
            updateOverrides(git.getRepository().getWorkTree(), overridesToKeep);
        }
    }

    @Override
    public void commitInstallation(String transaction) {
        transactionIsValid(transaction, null);

        Git fork = pendingTransactions.get(transaction);

        try {
            switch (pendingTransactionsTypes.get(transaction)) {
                case ROLLUP: {
                    // hard reset of main patch branch to point to transaction branch + apply changes to ${karaf.home}
                    gitPatchRepository.checkout(fork)
                            .setName(gitPatchRepository.getMainBranchName())
                            .call();

                    // before we reset main patch branch to originate from new baseline, let's find previous baseline
                    RevTag baseline = gitPatchRepository.findCurrentBaseline(fork);
                    RevCommit c1 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve(baseline.getTagName() + "^{commit}"));

                    // hard reset of main patch branch - to point to other branch, originating from new baseline
                    fork.reset()
                            .setMode(ResetCommand.ResetType.HARD)
                            .setRef(transaction)
                            .call();
                    gitPatchRepository.push(fork);

                    RevCommit c2 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve("HEAD"));

                    // apply changes from single range of commits
//                    applyChanges(fork, c1, c2);
                    applyChanges(fork, false);
                    break;
                }
                case NON_ROLLUP: {
                    // fast forward merge of main patch branch with transaction branch
                    gitPatchRepository.checkout(fork)
                            .setName(gitPatchRepository.getMainBranchName())
                            .call();
                    // current version of ${karaf.home}
                    RevCommit c1 = new RevWalk(fork.getRepository()).parseCommit(fork.getRepository().resolve("HEAD"));

                    // fast forward over patch-installation branch - possibly over more than 1 commit
                    fork.merge()
                            .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                            .include(fork.getRepository().resolve(transaction))
                            .call();

                    gitPatchRepository.push(fork);

                    // apply a change from commits of all installed patches
                    RevCommit c2 = new RevWalk(fork.getRepository()).parseCommit(fork.getRepository().resolve("HEAD"));
                    applyChanges(fork, c1, c2);
//                    applyChanges(fork);
                    break;
                }
            }
            gitPatchRepository.push(fork);
        } catch (GitAPIException | IOException e) {
            throw new PatchException(e.getMessage(), e);
        } finally {
            gitPatchRepository.closeRepository(fork, true);
        }

        pendingTransactions.remove(transaction);
        pendingTransactionsTypes.remove(transaction);
    }

    @Override
    public void rollbackInstallation(String transaction) {
        transactionIsValid(transaction, null);

        Git fork = pendingTransactions.get(transaction);

        try {
            switch (pendingTransactionsTypes.get(transaction)) {
                case ROLLUP:
                case NON_ROLLUP:
                    // simply do nothing - do not push changes to origin
                    break;
            }
        } finally {
            gitPatchRepository.closeRepository(fork, true);
        }

        pendingTransactions.remove(transaction);
        pendingTransactionsTypes.remove(transaction);
    }

    @Override
    public void rollback(PatchData patchData) {
        Git fork = null;
        try {
            fork = gitPatchRepository.cloneRepository(gitPatchRepository.findOrCreateMainGitRepository(), true);
            Ref installationBranch = null;

            PatchKind kind = patchData.isRollupPatch() ? PatchKind.ROLLUP : PatchKind.NON_ROLLUP;

            switch (kind) {
                case ROLLUP: {
                    Activator.log2(LogService.LOG_INFO, String.format("Rolling back rollup patch \"%s\"", patchData.getId()));

                    // rolling back a rollup patch should rebase (cherry-pick) all user commits done after current baseline
                    // to previous baseline
                    RevTag currentBaseline = gitPatchRepository.findCurrentBaseline(fork);
                    RevCommit c1 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve(currentBaseline.getTagName() + "^{commit}"));
                    // remember the commit to discover P patch tags installed on top of rolledback baseline
                    RevCommit since = c1;
                    RevCommit c2 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve("HEAD"));
                    RevCommit to = c2;
                    Iterable<RevCommit> mainChangesSinceRollupPatch = fork.log().addRange(c1, c2).call();
                    List<RevCommit> userChanges = new LinkedList<>();
                    for (RevCommit rc : mainChangesSinceRollupPatch) {
                        if (isUserChangeCommit(rc)) {
                            userChanges.add(rc);
                        }
                    }

                    if (env == EnvType.STANDALONE) {
                        // remove the tag
                        fork.tagDelete()
                                .setTags(currentBaseline.getTagName())
                                .call();
                    }

                    // baselines are stacked on each other
                    RevTag previousBaseline = gitPatchRepository.findNthPreviousBaseline(fork, env == EnvType.STANDALONE ? 0 : 1);
                    c1 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve(previousBaseline.getTagName() + "^{commit}"));

                    // hard reset of main patch branch - to point to other branch, originating from previous baseline
                    fork.reset()
                            .setMode(ResetCommand.ResetType.HARD)
                            .setRef(previousBaseline.getTagName() + "^{commit}")
                            .call();

                    // reapply those user changes that are not conflicting
                    ListIterator<RevCommit> it = userChanges.listIterator(userChanges.size());

                    Status status = fork.status().call();
                    if (!status.isClean()) {
                        // unstage any garbage
                        fork.reset()
                                .setMode(ResetCommand.ResetType.MIXED)
                                .call();
                        for (String p : status.getModified()) {
                            gitPatchRepository.checkout(fork).addPath(p).call();
                        }
                    }
                    while (it.hasPrevious()) {
                        RevCommit userChange = it.previous();
                        CherryPickResult cpr = fork.cherryPick()
                                .include(userChange.getId())
                                .setNoCommit(true)
                                .call();

                        // this time prefer user change on top of previous baseline - this change shouldn't be
                        // conflicting, because when rolling back, patch change was preferred over user change
                        handleCherryPickConflict(patchData.getPatchDirectory(), fork, cpr, userChange,
                                true, PatchKind.ROLLUP, null, false, true);

                        // restore backed up content from the reapplied user change
                        String[] commitMessage = userChange.getFullMessage().split("\n\n");
                        if (commitMessage.length > 1) {
                            // we have original commit (that had conflicts) stored in this commit's full message
                            String ref = commitMessage[commitMessage.length - 1];
                            File backupDir = new File(patchesDir, patchData.getId() + ".backup");
                            if (isStandaloneChild()) {
                                backupDir = new File(patchesDir, patchData.getId() + "." + System.getProperty("karaf.name") + ".backup");
                            }
                            backupDir = new File(backupDir, ref);
                            if (backupDir.exists() && backupDir.isDirectory()) {
                                Activator.log2(LogService.LOG_DEBUG, String.format("Restoring content of %s", backupDir.getCanonicalPath()));
                                copyManagedDirectories(backupDir, karafBase, false, false, false);
                            }
                        }

                        gitPatchRepository.prepareCommit(fork, userChange.getFullMessage()).call();
                    }

                    // rollback should not lead to restoration of old patch management features
                    String productVersion = determineVersion(fork.getRepository().getWorkTree());
                    File featuresCfg = new File(fork.getRepository().getWorkTree(), "etc/org.apache.karaf.features.cfg");
                    if (featuresCfg.isFile()) {
                        if (setCurrentPatchManagementVersion(featuresCfg, productVersion)) {
                            // artificial updates to etc/startup.properties
                            String pmNew = String.format("mvn:org.jboss.fuse.modules.patch/patch-management/%s", bundleContext.getBundle().getVersion().toString());
                            String pmOld = String.format("mvn:org.jboss.fuse.modules.patch/patch-management/%s", productVersion);
                            BundleUpdate update = new BundleUpdate(null, null, pmNew, null, pmOld);
                            List<BundleUpdate> patchManagementUpdates = Collections.singletonList(update);
                            updateReferences(fork, "etc/startup.properties", "", Utils.collectLocationUpdates(patchManagementUpdates));

                            fork.add()
                                    .addFilepattern("etc/org.apache.karaf.features.cfg")
                                    .addFilepattern("etc/startup.properties")
                                    .call();
                            gitPatchRepository.prepareCommit(fork, String.format(MARKER_BASELINE_REPLACE_PATCH_FEATURE_PATTERN,
                                    productVersion, bundleContext.getBundle().getVersion().toString())).call();
                        }
                    }

                    gitPatchRepository.push(fork);
                    if (env == EnvType.STANDALONE) {
                        // remove remote tag
                        fork.push()
                                .setRefSpecs(new RefSpec()
                                        .setSource(null)
                                        .setDestination("refs/tags/" + currentBaseline.getTagName()))
                                .call();
                    }

                    // remove tags related to non-rollup patches installed between
                    // rolled back baseline and previous HEAD, because rolling back to previous rollup patch
                    // (previous baseline) equal effectively to starting from fresh baseline
                    RevWalk walk = new RevWalk(fork.getRepository());
                    Map<String, RevTag> tags = gitPatchRepository.findTagsBetween(fork, since, to);
                    for (Map.Entry<String, RevTag> entry : tags.entrySet()) {
                        if (entry.getKey().startsWith("patch-")) {
                            fork.tagDelete().setTags(entry.getKey()).call();
                            fork.push()
                                    .setRefSpecs(new RefSpec()
                                            .setSource(null)
                                            .setDestination("refs/tags/" + entry.getKey()))
                                    .call();

                            // and remove karaf base from tracked patch result, or even remove the result itself
                            String patchId = entry.getKey().substring("patch-".length());
                            Patch patch = loadPatch(new PatchDetailsRequest(patchId));
                            if (patch != null && patch.getResult() != null) {
                                boolean removed = false;
                                for (Iterator<String> iterator = patch.getResult().getKarafBases().iterator(); iterator.hasNext();) {
                                    String base = iterator.next();
                                    if (base.contains("|")) {
                                        String[] kb = base.split("\\s*\\|\\s*");
                                        String containerId = kb[0];
                                        if (System.getProperty("karaf.name", "").equals(containerId)) {
                                            iterator.remove();
                                            removed = true;
                                            break;
                                        }
                                    }
                                }
                                if (removed) {
                                    if (patch.getResult().getKarafBases().size() == 0) {
                                        // just remove the result entirely
                                        new File(patch.getPatchData().getPatchLocation(), patchId + ".patch.result").delete();
                                    } else {
                                        patch.getResult().store();
                                    }
                                    if (isStandaloneChild()) {
                                        File file = new File(patch.getPatchData().getPatchLocation(), patchId + "." + System.getProperty("karaf.name") + ".patch.result");
                                        if (file.isFile()) {
                                            file.delete();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // HEAD of main patch branch after reset and cherry-picks
                    c2 = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve("HEAD"));
//                    applyChanges(fork, c1, c2);
                    applyChanges(fork, false);

                    break;
                }
                case NON_ROLLUP: {
                    Activator.log2(LogService.LOG_INFO, String.format("Rolling back non-rollup patch \"%s\"", patchData.getId()));

                    // rolling back a non-rollup patch is a revert of the patch commit and removal of patch tag

                    String patchTagName = String.format("patch-%s",
                            env == EnvType.STANDALONE ? patchData.getId() : patchData.getId() + "-" + gitPatchRepository.getStandaloneChildkarafName());
                    ObjectId oid = fork.getRepository().resolve(patchTagName);
                    if (oid == null) {
                        throw new PatchException(String.format("Can't find installed patch (tag %s is missing)",
                                patchTagName));
                    }
                    RevCommit commit = new RevWalk(fork.getRepository()).parseCommit(oid);

                    RevertCommand revertCommand = fork.revert().include(commit);
                    RevCommit reverted = revertCommand.call();
                    if (reverted == null) {
                        List<String> unmerged = revertCommand.getUnmergedPaths();
                        Activator.log2(LogService.LOG_WARNING, "Problem rolling back patch \"" + patchData.getId() + "\". The following files where updated later:");
                        for (String path : unmerged) {
                            Activator.log2(LogService.LOG_WARNING, " - " + path);
                        }
                        RevWalk walk = new RevWalk(fork.getRepository());
                        RevCommit head = walk.parseCommit(fork.getRepository().resolve("HEAD"));

                        Map<String, RevTag> tags = gitPatchRepository.findTagsBetween(fork, commit, head);
                        List<RevTag> laterPatches = new LinkedList<>();
                        if (tags.size() > 0) {
                            for (Map.Entry<String, RevTag> tag : tags.entrySet()) {
                                if (tag.getKey().startsWith("patch-")) {
                                    laterPatches.add(tag.getValue());
                                }
                            }
                            Activator.log2(LogService.LOG_INFO, "The following patches were installed after \"" + patchData.getId() + "\":");
                            for (RevTag t : laterPatches) {
                                String message = " - " + t.getTagName().substring("patch-".length());
                                RevObject object = walk.peel(t);
                                if (object != null) {
                                    RevCommit c = walk.parseCommit(object.getId());
                                    String date = GitPatchRepository.FULL_DATE.format(new Date(c.getCommitTime() * 1000L));
                                    message += " (" + date + ")";
                                }
                                Activator.log2(LogService.LOG_INFO, message);
                            }
                        }
                        return;
                    }

                    // TODO: should we restore the backup possibly created when instalilng P patch?

                    // remove the tag
                    fork.tagDelete()
                            .setTags(patchTagName)
                            .call();

                    gitPatchRepository.push(fork);
                    // remove remote tag
                    fork.push()
                            .setRefSpecs(new RefSpec()
                                    .setSource(null)
                                    .setDestination(String.format("refs/tags/%s", patchTagName)))
                            .call();

                    // HEAD of main patch branch after reset and cherry-picks
                    RevCommit c = new RevWalk(fork.getRepository())
                            .parseCommit(fork.getRepository().resolve("HEAD"));
                    applyChanges(fork, c.getParent(0), c);
//                    applyChanges(fork);

                    break;
                }
            }
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        } finally {
            if (fork != null) {
                gitPatchRepository.closeRepository(fork, true);
            }
        }
    }

    /**
     * Resolve cherry-pick conflict before committing. Always prefer the change from patch, backup custom change
     * @param patchDirectory the source directory of the applied patch - used as a reference for backing up
     * conflicting files.
     * @param fork
     * @param result
     * @param commit conflicting commit
     * @param preferNew whether to use "theirs" change - the one from cherry-picked commit. for rollup patch, "theirs"
     * is user change (second, applied after patch), for non-rollup change, "theirs" is a change from patch (applied
     * after user change)
     * @param kind
     * @param cpPrefix prefix for a cherry-pick to have nice backup directory names.
     * @param performBackup if <code>true</code>, we backup rejected version (should be false during rollback of patches)
     * @param rollback is the resolution performed during patch rollback?
     * @return set of paths that had conflicts during cherry-pick
     */
    public Set<String> handleCherryPickConflict(File patchDirectory, Git fork, CherryPickResult result, RevCommit commit,
                                            boolean preferNew, PatchKind kind, String cpPrefix, boolean performBackup,
                                            boolean rollback)
            throws GitAPIException, IOException {
        if (result.getStatus() == CherryPickResult.CherryPickStatus.CONFLICTING) {
            DirCache cache = fork.getRepository().readDirCache();
            boolean patchInfoOnly = true;
            for (int i = 0; i < cache.getEntryCount(); i++) {
                DirCacheEntry entry = cache.getEntry(i);
                if (entry.getStage() == DirCacheEntry.STAGE_0) {
                    continue;
                }
                if (!"patch-info.txt".equals(entry.getPathString())) {
                    patchInfoOnly = false;
                    break;
                }
            }
            if (!patchInfoOnly) {
                Activator.log2(LogService.LOG_WARNING, "Problem applying the change " + commit.getName() + ":");
            }

            String choose = null;
            String backup = null;
            switch (kind) {
                case ROLLUP:
                    choose = !preferNew ? "change from patch" : "custom change";
                    backup = !preferNew ? "custom change" : "change from patch";
                    break;
                case NON_ROLLUP:
                    choose = preferNew ? "change from patch" : "custom change";
                    backup = preferNew ? "custom change" : "change from patch";
                    break;
            }

            return handleConflict(patchDirectory, fork, preferNew, cpPrefix, performBackup, choose, backup, rollback);
        }

        return null;
    }

    private Set<String> handleConflict(File patchDirectory, Git fork, boolean preferNew, String cpPrefix,
                                boolean performBackup, String choose, String backup, boolean rollback) throws GitAPIException, IOException {
        Map<String, IndexDiff.StageState> conflicts = fork.status().call().getConflictingStageState();
        DirCache cache = fork.getRepository().readDirCache();
        // path -> [oursObjectId, baseObjectId, theirsObjectId]
        Map<String, ObjectId[]> threeWayMerge = new HashMap<>();
        Set<String> conflictingFiles = new LinkedHashSet<>();

        // collect conflicts info
        for (int i = 0; i < cache.getEntryCount(); i++) {
            DirCacheEntry entry = cache.getEntry(i);
            if (entry.getStage() == DirCacheEntry.STAGE_0) {
                continue;
            }
            if (!threeWayMerge.containsKey(entry.getPathString())) {
                threeWayMerge.put(entry.getPathString(), new ObjectId[] { null, null, null });
            }
            if (entry.getStage() == DirCacheEntry.STAGE_1) {
                // base
                threeWayMerge.get(entry.getPathString())[1] = entry.getObjectId();
            }
            if (entry.getStage() == DirCacheEntry.STAGE_2) {
                // ours
                threeWayMerge.get(entry.getPathString())[0] = entry.getObjectId();
            }
            if (entry.getStage() == DirCacheEntry.STAGE_3) {
                // theirs
                threeWayMerge.get(entry.getPathString())[2] = entry.getObjectId();
            }
        }

        // resolve conflicts
        ObjectReader objectReader = fork.getRepository().newObjectReader();

        for (Map.Entry<String, ObjectId[]> entry : threeWayMerge.entrySet()) {
            if (entry.getKey().equals("patch-info.txt")) {
                fork.rm().addFilepattern(entry.getKey()).call();
                continue;
            }
            Resolver resolver = conflictResolver.getResolver(entry.getKey());
            // resolved version - either by custom resolved or using automatic algorithm
            String resolved = null;
            if (resolver != null && entry.getValue()[0] != null && entry.getValue()[2] != null) {
                // custom conflict resolution (don't expect DELETED_BY_X kind of conflict, only BOTH_MODIFIED)
                String message = String.format(" - %s (%s): %s", entry.getKey(), conflicts.get(entry.getKey()), "Using " + resolver.getClass().getName() + " to resolve the conflict");
                Activator.log2(LogService.LOG_INFO, message);

                // when doing custom resolution of conflict, we know that both user and patch has changed the file
                // in non-mergeable way.
                // If there was no resolver, we simply check what to choose by "preferNew" flag
                // But because we have custom resolver, we use "preferNew" flag to check which STAGE points to patch'
                // version and we select this patch' version of conflicting file as less important file inside
                // custom resolver
                File base = null;
                File first = null;
                File second = null;
                try {
                    ObjectLoader loader = null;
                    if (entry.getValue()[1] != null) {
                        base = new File(fork.getRepository().getWorkTree(), entry.getKey() + ".1");
                        loader = objectReader.open(entry.getValue()[1]);
                        try (FileOutputStream fos = new FileOutputStream(base)) {
                            loader.copyTo(fos);
                        }
                    }

                    // if preferNew == true (P patch) then "first" file (less important) will be file
                    // provided by patch ("theirs", STAGE_3)
                    first = new File(fork.getRepository().getWorkTree(), entry.getKey() + ".2");
                    loader = objectReader.open(entry.getValue()[preferNew ? 2 : 0]);
                    try (FileOutputStream fos = new FileOutputStream(first)) {
                        loader.copyTo(fos);
                    }

                    // "second", more important file will be user change
                    second = new File(fork.getRepository().getWorkTree(), entry.getKey() + ".3");
                    loader = objectReader.open(entry.getValue()[preferNew ? 0 : 2]);
                    try (FileOutputStream fos = new FileOutputStream(second)) {
                        loader.copyTo(fos);
                    }

                    // resolvers treat patch change as less important - user lines overwrite patch lines
                    if (resolver instanceof PropertiesFileResolver) {
                        // by default we use a file that comes from patch and we may add property changes
                        // from user
                        // in R patch, preferNew == false, because patch comes first
                        // in P patch, preferNew == true, because patch comes last
                        boolean useFirstChangeAsBase = true;
                        if (entry.getKey().startsWith("etc/")) {
                            // files in etc/ directory are "for user", so we use them as base (possibly changed
                            // by user - comments, layout, ...)
                            if (rollback) {
                                useFirstChangeAsBase = true;
                            } else {
                                useFirstChangeAsBase = false;
                            }
                        }
                        resolved = ((ResolverEx)resolver).resolve(first, base, second, useFirstChangeAsBase, rollback);
                    } else {
                        resolved = resolver.resolve(first, base, second);
                    }

                    if (resolved != null) {
                        // we have resolution done by custom resolver
                        conflictingFiles.add(entry.getKey());

                        FileUtils.write(new File(fork.getRepository().getWorkTree(), entry.getKey()), resolved, "UTF-8");
                        fork.add().addFilepattern(entry.getKey()).call();
                    }
                } finally {
                    if (base != null) {
                        base.delete();
                    }
                    if (first != null) {
                        first.delete();
                    }
                    if (second != null) {
                        second.delete();
                    }
                }
            }
            if (resolved == null) {
                // automatic conflict resolution
                String message = String.format(" - %s (%s): Choosing %s", entry.getKey(), conflicts.get(entry.getKey()), choose);

                if (conflicts.get(entry.getKey()) == BOTH_MODIFIED) {
                    // from the report point of view, we care about real conflicts
                    conflictingFiles.add(entry.getKey());
                }

                ObjectLoader loader = null;
                ObjectLoader loaderForBackup = null;
                // longer code, but more readable then series of elvis operators (?:)
                if (preferNew) {
                    switch (conflicts.get(entry.getKey())) {
                        case BOTH_ADDED:
                        case BOTH_MODIFIED:
                            loader = objectReader.open(entry.getValue()[2]);
                            loaderForBackup = objectReader.open(entry.getValue()[0]);
                            break;
                        case BOTH_DELETED:
                            break;
                        case DELETED_BY_THEM:
                            // ENTESB-6003: special case: when R patch removes something and we've modified it
                            // let's preserve our version
                            message = String.format(" - %s (%s): Keeping custom change", entry.getKey(), conflicts.get(entry.getKey()));
                            loader = objectReader.open(entry.getValue()[0]);
                            break;
                        case DELETED_BY_US:
                            loader = objectReader.open(entry.getValue()[2]);
                            break;
                    }
                } else {
                    switch (conflicts.get(entry.getKey())) {
                        case BOTH_ADDED:
                        case BOTH_MODIFIED:
                            loader = objectReader.open(entry.getValue()[0]);
                            loaderForBackup = objectReader.open(entry.getValue()[2]);
                            break;
                        case DELETED_BY_THEM:
                            loader = objectReader.open(entry.getValue()[0]);
                            break;
                        case BOTH_DELETED:
                        case DELETED_BY_US:
                            break;
                    }
                }

                Activator.log2(LogService.LOG_WARNING, message);

                if (loader != null) {
                    try (FileOutputStream fos = new FileOutputStream(new File(fork.getRepository().getWorkTree(), entry.getKey()))) {
                        loader.copyTo(fos);
                    }
                    fork.add().addFilepattern(entry.getKey()).call();
                } else {
                    fork.rm().addFilepattern(entry.getKey()).call();
                }

                if (performBackup) {
                    // the other entry should be backed up
                    if (loaderForBackup != null) {
                        File target = new File(patchDirectory.getParent(), patchDirectory.getName() + ".backup");
                        if (isStandaloneChild()) {
                            target = new File(patchDirectory.getParent(), patchDirectory.getName() + "." + System.getProperty("karaf.name") + ".backup");
                        }
                        if (cpPrefix != null) {
                            target = new File(target, cpPrefix);
                        }
                        File file = new File(target, entry.getKey());
                        message = String.format("Backing up %s to \"%s\"", backup, file.getCanonicalPath());
                        Activator.log2(LogService.LOG_DEBUG, message);
                        Utils.mkdirs(file.getParentFile());
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            loaderForBackup.copyTo(fos);
                        }
                    }
                }
            }
        }

        return conflictingFiles;
    }

    /**
     * Very important method - {@link PatchKind#NON_ROLLUP non rollup patches} do not ship such files as
     * <code>etc/startup.properties</code>, but we <strong>have to</strong> update references to artifacts from those
     * files to point them to updated bundles.
     * Also we have to update/add <code>etc/overrides.properties</code> to have features working.
     * @param fork
     * @param patchData
     * @param bundleUpdatesInThisPatch
     */
    private void updateFileReferences(Git fork, PatchData patchData, List<BundleUpdate> bundleUpdatesInThisPatch) {
        if (patchData.isRollupPatch()) {
            return;
        }

        /*
         * we generally have a white list of files to update. We'll update them line by line if needed
         * these are the files & patterns to change:
         *
         * bin/instance, bin/instance.bat, bin/client, bin/client.bat, bin/shell, bin/shell.bat:
         *  - system/MAVEN_LOCATION (or \ in *.bat files)
         *
         * etc/config.properties:
         *  - karaf.framework.equinox=mvn\:org.eclipse.platform/org.eclipse.osgi/x.y.z
         *  - karaf.framework.felix=mvn\:org.apache.felix/org.apache.felix.framework/x.y.z
         *  - org.apache.karaf.version;version="4.2.0.redhat-xxx", \
         *  - org.apache.karaf.version;version="4.2.0.redhat-700-SNAPSHOT",\
         *  - org.apache.karaf.jaas.boot.principal;uses:=javax.security.auth;version="4.2.0.redhat-700-SNAPSHOT",\
         *  - org.apache.karaf.jaas.boot;uses:="javax.security.auth,javax.security.auth.callback,...,javax.security.auth.spi,org.osgi.framework";version="4.2.0.redhat-700-SNAPSHOT",\
         *  these are the versions exported from lib/karaf.jar and this may be changed by non-rollup patch too...
         *
         * etc/startup.properties:
         *  - MAVEN_URI (with mvn\:...) - in Fuse 6.3 it was just MAVEN_LOCATION relative to ${karaf.default.repository}
         *
         * etc/org.apache.karaf.features.cfg:
         *  - don't touch that file. NON-ROLLUP patches handle features using overrides and ROLLUP
         *  patches overwrite relevant etc/* files
         *
         * etc/org.apache.karaf.features.xml:
         *  - don't touch that file here. It's a "new overrides mechanism" and there's dedicated part of patching
         *  mechanism for that
         *
         * etc/profile.cfg:
         *  - don't care about this file - it's only a report from Karaf custom distro assembly (karaf-maven-plugin)
         */

        Map<String, String> locationUpdates = Utils.collectLocationUpdates(bundleUpdatesInThisPatch);

        // update some files in generic way
        updateReferences(fork, "bin/instance", "system/", locationUpdates);
        updateReferences(fork, "bin/client", "system/", locationUpdates);
        updateReferences(fork, "bin/shell", "system/", locationUpdates);
        updateReferences(fork, "bin/instance.bat", "system/", locationUpdates, true);
        updateReferences(fork, "bin/client.bat", "system/", locationUpdates, true);
        updateReferences(fork, "bin/shell.bat", "system/", locationUpdates, true);
        updateReferences(fork, "etc/startup.properties", "", locationUpdates);
        updateReferences(fork, "etc/config.properties", "", locationUpdates);
        updateConfigProperties(fork, patchData);

        // update system karaf package versions in etc/config.properties
        File configProperties = new File(fork.getRepository().getWorkTree(), "etc/config.properties");
        for (String file : patchData.getFiles()) {
            if (file.startsWith("lib/boot/org.apache.karaf.main-") && file.endsWith(".jar")) {
                // update:
                //  - org.apache.karaf.version;version="4.2.0.redhat-700-SNAPSHOT",\
                String newVersion = getBundleVersion(new File(fork.getRepository().getWorkTree(), file));
                updateKarafPackageVersion(configProperties, newVersion,
                        "org.apache.karaf.version");
            } else if (file.startsWith("lib/boot/org.apache.karaf.jaas.boot-") && file.endsWith(".jar")) {
                // update:
                //  - org.apache.karaf.jaas.boot.principal;uses:=javax.security.auth;version="4.2.0.redhat-700-SNAPSHOT",\
                //  - org.apache.karaf.jaas.boot;uses:="javax.security.auth,...,javax.security.auth.login,javax.security.auth.spi,org.osgi.framework";version="4.2.0.redhat-700-SNAPSHOT",\
                String newVersion = getBundleVersion(new File(fork.getRepository().getWorkTree(), file));
                updateKarafPackageVersion(configProperties, newVersion,
                        "org.apache.karaf.jaas.boot",
                        "org.apache.karaf.jaas.boot.principal");
            }
        }
    }

    /**
     * Changes prefixed references (to artifacts in <code>${karaf.default.repository}</code>) according to
     * a list of bundle updates.
     * @param fork
     * @param file
     * @param prefix
     * @param locationUpdates
     * @param useBackSlash
     */
    protected void updateReferences(Git fork, String file, String prefix, Map<String, String> locationUpdates, boolean useBackSlash) {
        File updatedFile = new File(fork.getRepository().getWorkTree(), file);
        if (!updatedFile.isFile()) {
            return;
        }

        BufferedReader reader = null;
        StringWriter sw = new StringWriter();
        try {
            Activator.log2(LogService.LOG_INFO, "Updating \"" + file + "\"");

            reader = new BufferedReader(new FileReader(updatedFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                for (Map.Entry<String, String> entry : locationUpdates.entrySet()) {
                    String pattern = prefix + entry.getKey();
                    String replacement = prefix + entry.getValue();
                    if (useBackSlash) {
                        pattern = pattern.replaceAll("/", "\\\\");
                        replacement = replacement.replaceAll("/", "\\\\");
                    }
                    if (line.contains(pattern)) {
                        line = line.replace(pattern, replacement);
                    }
                }
                sw.append(line);
                if (useBackSlash) {
                    // Assume it's .bat file
                    sw.append("\r");
                }
                sw.append("\n");
            }
            Utils.closeQuietly(reader);
            FileUtils.write(updatedFile, sw.toString(), "UTF-8");
        } catch (Exception e) {
            Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
        } finally {
            Utils.closeQuietly(reader);
        }
    }

    /**
     * Changes prefixed references (to artifacts in <code>${karaf.default.repository}</code>) according to
     * a list of bundle updates.
     * @param fork
     * @param file
     * @param prefix
     * @param locationUpdates
     */
    private void updateReferences(Git fork, String file, String prefix, Map<String, String> locationUpdates) {
        updateReferences(fork, file, prefix, locationUpdates, false);
    }

    /**
     * {@code etc/config.properties} is quite special. It contains package declarations that should be exported
     * from system bundle - together with versions. When I had to upgrade BouncyCastle library, I found, that
     * I have to update a lot of package declarations... What's problematic is that there's huge
     * {@code org.osgi.framework.system.packages.extra} property formatted specially (with {@code \} new line
     * continuation), which we can only change in line-by-line way.
     *
     * @param fork
     * @param patchData
     */
    private void updateConfigProperties(Git fork, PatchData patchData) {
        File updatedFile = new File(fork.getRepository().getWorkTree(), "etc/config.properties");
        if (!updatedFile.isFile()) {
            return;
        }

        BufferedReader reader = null;
        StringWriter sw = new StringWriter();
        try {
            Activator.log2(LogService.LOG_INFO, "Updating package versions in \"etc/config.properties\"");

            reader = new BufferedReader(new FileReader(updatedFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                for (String cp : patchData.getConfigPackages()) {
                    // For example:
                    //     configPackage.0 = org.bouncycastle/1.68
                    //     configPackage.0.range = [1.66,1.68)
                    //     configPackage.count = 1
                    //
                    // in etc/config.properties:
                    // org.osgi.framework.system.packages.extra = \
                    //    ...
                    //    org.bouncycastle;version=1.66, \
                    //    org.bouncycastle.asn1;uses:=org.bouncycastle.util;version=1.66, \
                    //    ...

                    String[] packageVersion = cp.split("/");
                    String tmpLine = line.trim();
                    if (!tmpLine.startsWith(packageVersion[0])) {
                        continue;
                    }
                    int v = tmpLine.indexOf("version=");
                    if (v == -1) {
                        continue;
                    }
                    String tmpVersion = tmpLine.substring(v + "version=".length());
                    if (tmpVersion.length() < 2) {
                        continue;
                    }
                    String configVersion = null;
                    if (tmpVersion.charAt(0) == '"') {
                        configVersion = tmpVersion.substring(1, tmpLine.indexOf('"'));
                    } else if (tmpVersion.charAt(0) == '\'') {
                        configVersion = tmpVersion.substring(1, tmpLine.indexOf('\''));
                    } else {
                        configVersion = tmpVersion;
                        // org.bouncycastle;version=1.66;something=something-else \
                        int end = configVersion.indexOf(";");
                        if (end < 0) {
                            // org.bouncycastle;version=1.66, \
                            end = configVersion.indexOf(",");
                        }
                        if (end < 0) {
                            // org.bouncycastle;version=1.66 \
                            end = configVersion.indexOf(" ");
                        }
                        if (end < 0) {
                            // org.bouncycastle;version=1.66\
                            end = configVersion.indexOf("\\");
                        }
                        if (end < 0) {
                            // org.bouncycastle;version=1.66
                            end = configVersion.length();
                        }
                        configVersion = configVersion.substring(0, end);
                    }

                    if (patchData.getConfigPackageRanges().containsKey(cp)) {
                        // we have to match the version first
                        String vr = patchData.getConfigPackageRanges().get(cp);
                        Version oVer = Utils.getOsgiVersion(configVersion);
                        VersionRange range = new VersionRange(vr);
                        if (range.includes(oVer)) {
                            line = line.replaceAll(configVersion.replace(".", "\\."), packageVersion[1]);
                        }
                    } else {
                        // we just replace the version
                        line = line.replaceAll(configVersion.replace(".", "\\."), packageVersion[1]);
                    }
                }
                sw.append(line);
                sw.append("\n");
            }
            Utils.closeQuietly(reader);
            FileUtils.write(updatedFile, sw.toString(), "UTF-8");
        } catch (Exception e) {
            Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
        } finally {
            Utils.closeQuietly(reader);
        }
    }

    /**
     * Retrieves <code>Bundle-Version</code> header from JAR file
     * @param file
     * @return
     */
    private String getBundleVersion(File file) {
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(new FileInputStream(file));
            Manifest mf = jis.getManifest();
            return mf.getMainAttributes().getValue(Constants.BUNDLE_VERSION);
        } catch (Exception e) {
            return null;
        } finally {
            Utils.closeQuietly(jis);
        }
    }

    /**
     * Checks if the commit is user (non P-patch installation) change
     * @param rc
     * @return
     */
    protected boolean isUserChangeCommit(RevCommit rc) {
        return MARKER_USER_CHANGES_COMMIT.equals(rc.getShortMessage());
    }

    /**
     * Checks whether the commit is related to HotFix patch installation.
     * Such patches are P patches that update <strong>only</strong> bundles.
     * @param rc
     * @return patchId of HF patch if one is detected
     */
    private String isHfChangeCommit(RevCommit rc) {
        String msg = rc.getShortMessage();
        boolean pPatch = msg != null && msg.startsWith(MARKER_P_PATCH_INSTALLATION_PREFIX);
        if (pPatch) {
            String patchId = msg.length() > MARKER_P_PATCH_INSTALLATION_PREFIX.length() ? msg.substring(MARKER_P_PATCH_INSTALLATION_PREFIX.length()) : null;
            if (patchId != null) {
                Patch p = loadPatch(new PatchDetailsRequest(patchId));
                if (p != null && p.getPatchData() != null) {
                    try {
                        boolean hfPatch = p.getPatchData().getBundles().size() > 0;
                        hfPatch &= p.getPatchData().getFeatureFiles().size() == 0;
                        // let's allow files to be updated in HF patch
//                        hfPatch &= p.getPatchData().getFiles().size() == 0;
                        hfPatch &= p.getPatchData().getOtherArtifacts().size() == 0;
                        return patchId;
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns list of bundle updates (maven coordinates) from HF/P patch that should be preserved during
     * installation of R patch
     * @param hfPatchId ID of patch that was detected to be HF patch installed previously (before R patch just being installed)
     * @param patch R patch which is currently being installed
     * @return an artificial {@link PatchData} with a list of maven URIs for bundles that are newer in previous P-patches than the ones in currently installed R-patch
     */
    private PatchData gatherOverrides(String hfPatchId, Patch patch) {
        Patch hf = loadPatch(new PatchDetailsRequest(hfPatchId));
        List<String> bundles = new LinkedList<>();
        Map<String, String> ranges = new LinkedHashMap<>();

        if (hf != null && hf.getPatchData() != null) {
            for (String bundle : hf.getPatchData().getBundles()) {
                bundles.add(bundle);
                String versionRange = hf.getPatchData().getVersionRange(bundle);
                if (versionRange != null && !versionRange.trim().equals("")) {
                    ranges.put(bundle, versionRange);
                }
            }


            // leave only these artifacts that are in newer version than in R patch being installed
            if (patch != null && patch.getPatchData() != null) {
                Map<String, Artifact> cache = new HashMap<>();
                for (String bu : patch.getPatchData().getBundles()) {
                    Artifact rPatchArtifact = Utils.mvnurlToArtifact(bu, true);
                    if (rPatchArtifact != null) {
                        cache.put(String.format("%s:%s", rPatchArtifact.getGroupId(), rPatchArtifact.getArtifactId()), rPatchArtifact);
                    }
                }

                for (String bu : hf.getPatchData().getBundles()) {
                    Artifact hfPatchArtifact = Utils.mvnurlToArtifact(bu, true);
                    if (hfPatchArtifact != null) {
                        String key = String.format("%s:%s", hfPatchArtifact.getGroupId(), hfPatchArtifact.getArtifactId());
                        if (cache.containsKey(key)) {
                            Version hfVersion = Utils.getOsgiVersion(hfPatchArtifact.getVersion());
                            Version rVersion = Utils.getOsgiVersion(cache.get(key).getVersion());
                            if (rVersion.compareTo(hfVersion) >= 0) {
                                bundles.remove(bu);
                                ranges.remove(bu);
                            }
                        }
                    }
                }
            }
        }

        return new PatchData(null, null, bundles, null, ranges, null, null);
    }

    /**
     * Validates state of the transaction for install/commit/rollback purposes
     * @param transaction
     * @param patch
     */
    private void transactionIsValid(String transaction, Patch patch) {
        if (!pendingTransactions.containsKey(transaction)) {
            if (patch != null) {
                throw new PatchException(String.format("Can't proceed with \"%s\" patch - illegal transaction \"%s\".",
                        patch.getPatchData().getId(),
                        transaction));
            } else {
                throw new PatchException(String.format("Can't proceed - illegal transaction \"%s\".",
                        transaction));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return patchesDir != null && patchesDir.isDirectory() && patchesDir.exists();
    }

    @Override
    public void start() throws IOException, GitAPIException {
        if (patchesDir != null) {
            gitPatchRepository.open();
        }
    }

    @Override
    public void stop() {
        if (patchesDir != null) {
            gitPatchRepository.close();
        }
    }

    /**
     * Check if Fuse/Karaf installation is correctly managed by patch mechanism. Check if main git repository
     * is created and is intialized with correct content, there are no conflicts and no pending updates in main Karaf
     * directory. After this method is invoked, we're basically ready to perform rollup patches backed up by git
     * repository.
     */
    @Override
    public void ensurePatchManagementInitialized() {
        Activator.log(LogService.LOG_INFO, "Configuring patch management system");

        Git fork = null;
        try {
            Git mainRepository = gitPatchRepository.findOrCreateMainGitRepository();

            ensuringLock.lock();

            // prepare single fork for all the below operations - switch to different branches later, as needed
            fork = gitPatchRepository.cloneRepository(mainRepository, true);

            if (env == EnvType.STANDALONE) {
                // do standalone history initialization. We're in root Fuse/AMQ container
                String currentFuseVersion = determineVersion(karafHome);

                // one of the steps may return a commit that has to be tagged as first baseline
                RevCommit baselineCommit = null;
                if (!gitPatchRepository.containsTag(fork, String.format(env.getBaselineTagFormat(), currentFuseVersion))) {
                    baselineCommit = trackBaselineRepository(fork);
                    RevCommit c2 = installPatchManagementBundle(fork);
                    if (c2 != null) {
                        baselineCommit = c2;
                    }
                }
                // because patch management is already installed, we have to add consecutive (post patch-management installation) changes
                applyUserChanges(fork);

                if (baselineCommit != null) {
                    // and we'll tag the baseline *after* steps related to first baseline
                    fork.tag()
                            .setName(String.format(env.getBaselineTagFormat(), currentFuseVersion))
                            .setObjectId(baselineCommit)
                            .call();

                    gitPatchRepository.push(fork);
                }

                // now we have to do the same for existing/future instance:create based child containers
                // it's the root container that takes care of this
                trackBaselinesForChildContainers(fork);
            } else if (env == EnvType.STANDALONE_CHILD) {
                // we're in instance:create based child container. we share patch management git repository
                // with the container that created us
                String currentKarafVersion = determineVersion(karafBase);
                String tagName = String.format(env.getBaselineTagFormat(), currentKarafVersion);

                handleNonCurrentBaseline(fork, currentKarafVersion, tagName, true, true);
            }

            // remove pending patches listeners
            for (BundleListener bl : pendingPatchesListeners.values()) {
                systemContext.removeBundleListener(bl);
            }
        } catch (GitAPIException | IOException e) {
            Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
        } finally {
            ensuringLock.unlock();
            initialized.countDown();
            if (fork != null) {
                gitPatchRepository.closeRepository(fork, true);
            }
        }
    }

    /**
     * <p>Called to check if product has current baseline - if not, some alignment is performed - different for initial
     * rebase and consecutive rebase</p>
     * @param fork
     * @param currentProductVersion
     * @param tagName
     * @param restartFileInstall
     * @param requireTags whether the tags should be present when baseline is non current
     * @return <code>true</code> if there was switch of baselines. <code>false</code> is returned when there's no change
     * or when there was first alignment performed
     * @throws GitAPIException
     * @throws IOException
     */
    private boolean handleNonCurrentBaseline(Git fork, String currentProductVersion, String tagName,
                                          boolean restartFileInstall, boolean requireTags) throws GitAPIException, IOException {
        RevTag tag = gitPatchRepository.findCurrentBaseline(fork);

        if (tag == null || !tagName.equals(tag.getTagName())) {
            if (!requireTags && !gitPatchRepository.containsTag(fork, tagName)) {
                String location = "";
                if (fork.getRepository().getConfig() != null
                        && fork.getRepository().getConfig().getString("remote", "origin", "url") != null) {
                    location = " in " + fork.getRepository().getConfig().getString("remote", "origin", "url");
                }
                fork.getRepository().getConfig().getString("remote", "origin", "url");
                Activator.log(LogService.LOG_INFO, "Tag \"" + tagName + "\" is not available" + location
                        + ", alignment will be performed later.");
                return false;
            }
        }
        if (tag == null) {
            ensureCorrectContainerHistory(fork, currentProductVersion);
            applyUserChanges(fork);
            return false;
        } else if (!tagName.equals(tag.getTagName())) {
            applyUserChanges(fork);
            ensureCorrectContainerHistory(fork, currentProductVersion);

            String standaloneTagName = String.format(EnvType.STANDALONE.getBaselineTagFormat(), currentProductVersion);
            if (!standaloneTagName.equals(tag.getTagName())) {
                applyChanges(fork, restartFileInstall);
                return true;
            }
        }

        return false;
    }

    /**
     * Return version of product used, but probably based on different karafHome
     * @param home
     * @return
     */
    private String determineVersion(File home) {
        if (env == EnvType.STANDALONE) {
            File versions = new File(home, "etc/version.properties");
            if (versions.exists() && versions.isFile()) {
                Properties props = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(versions);
                    props.load(fis);
                    return props.getProperty("version");
                } catch (IOException e) {
                    Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
                    return null;
                } finally {
                    Utils.closeQuietly(fis);
                }
            } else {
                Activator.log2(LogService.LOG_ERROR, "Can't find etc/version.properties");
            }
        } else {
            // for child container we have to be more careful and not examine root container's etc/version.properties!
            File startup = new File(home, "etc/startup.properties");
            if (startup.exists() && startup.isFile()) {
                Properties props = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(startup);
                    props.load(fis);
                    for (String key : props.stringPropertyNames()) {
                        if (key.contains("org.apache.karaf.features/org.apache.karaf.features.core/")) {
                            Artifact artifact = Utils.mvnurlToArtifact(key, true);
                            if (artifact != null) {
                                return artifact.getVersion();
                            }
                        }
                    }
                } catch (IOException e) {
                    Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
                    return null;
                } finally {
                    Utils.closeQuietly(fis);
                }
            } else {
                Activator.log2(LogService.LOG_ERROR, "Can't find etc/startup.properties file in child container");
            }

        }
        return null;
    }

    /**
     * Adds baseline distribution to the repository.
     * In standalone mode mode, the same branch is used to track baselines and for versioning of the container itself.
     * In standalone mode, patches are added to separate branch each.
     * @param git non-bare repository to perform the operation with correct branch checked out already
     */
    private RevCommit trackBaselineRepository(Git git) throws IOException, GitAPIException {
        // initialize repo with baseline version and push to reference repo
        String currentFuseVersion = determineVersion(karafHome);

        // check what product are we in
        File systemRepo = getSystemRepository(karafHome, systemContext);
        File baselineDistribution = null;

        // the preferred location of baseline for standalone Red Hat Fuse
        String location = systemRepo.getCanonicalPath() + "/org/jboss/fuse/fuse-karaf/%1$s/fuse-karaf-%1$s-baseline.zip";
        location = String.format(location, currentFuseVersion);
        if (new File(location).isFile()) {
            baselineDistribution = new File(location);
            Activator.log(LogService.LOG_INFO, "Found baseline distribution: " + baselineDistribution.getCanonicalPath());
        } else {
            // fallback/test location
            location = patchesDir.getCanonicalPath() + "/fuse-karaf-%1$s-baseline.zip";
            location = String.format(location, currentFuseVersion);
            if (new File(location).isFile()) {
                baselineDistribution = new File(location);
                Activator.log(LogService.LOG_INFO, "Found baseline distribution: " + baselineDistribution.getCanonicalPath());
            }
        }

        if (baselineDistribution != null) {
            return trackBaselineRepository(git, baselineDistribution, currentFuseVersion);
        } else {
            String message = "Can't find baseline distribution inside system repository or in " + patchesDir + ".";
            Activator.log2(LogService.LOG_WARNING, message);
            throw new PatchException(message);
        }
    }

    /**
     * Tracks all baselines for child containers that weren't tracked already. These are looked up inside
     * <code>system/org/apache/karaf/instance/org.apache.karaf.instance.core/**</code>
     * @param fork
     */
    private void trackBaselinesForChildContainers(Git fork) throws IOException, GitAPIException {
        if (fork.getRepository().findRef("refs/heads/" + gitPatchRepository.getChildBranchName()) == null) {
            // checkout patches-child branch - it'll track baselines for child containers
            String startPoint = "patch-management^{commit}";
            if (fork.getRepository().findRef("refs/remotes/origin/" + gitPatchRepository.getChildBranchName()) != null) {
                startPoint = "refs/remotes/origin/" + gitPatchRepository.getChildBranchName();
            }
            gitPatchRepository.checkout(fork)
                    .setName(gitPatchRepository.getChildBranchName())
                    .setStartPoint(startPoint)
                    .setCreateBranch(true)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .call();
        } else {
            gitPatchRepository.checkout(fork)
                    .setName(gitPatchRepository.getChildBranchName())
                    .call();
        }

        File systemRepo = getSystemRepository(karafHome, systemContext);
        File[] versionDirs = new File(systemRepo, "org/apache/karaf/instance/org.apache.karaf.instance.core").listFiles();
        Set<Version> versions = new TreeSet<>();

        if (versionDirs != null) {
            for (File version : versionDirs) {
                if (version.isDirectory()) {
                    versions.add(Utils.getOsgiVersion(version.getName()));
                }
            }
        }
        for (Version v : versions) {
            String karafVersion = v.toString();
            String tagName = String.format(EnvType.STANDALONE_CHILD.getBaselineTagFormat(), karafVersion);

            if (gitPatchRepository.containsTag(fork, tagName)) {
                continue;
            }

            File baselineDistribution = null;
            String location = String.format(systemRepo.getCanonicalPath() + "/org/apache/karaf/instance/org.apache.karaf.instance.core/%1$s/org.apache.karaf.instance.core-%1$s.jar", karafVersion);
            if (new File(location).isFile()) {
                baselineDistribution = new File(location);
                Activator.log(LogService.LOG_INFO, "Found child baseline distribution: " + baselineDistribution.getCanonicalPath());
            }

            if (baselineDistribution != null) {
                try {
                    unzipKarafInstanceJar(baselineDistribution, fork.getRepository().getWorkTree());

                    fork.add()
                            .addFilepattern(".")
                            .call();
                    RevCommit commit = gitPatchRepository.prepareCommit(fork,
                            String.format(MARKER_BASELINE_CHILD_COMMIT_PATTERN, karafVersion))
                            .call();

                    // and we'll tag the child baseline
                    fork.tag()
                            .setName(tagName)
                            .setObjectId(commit)
                            .call();

                } catch (Exception e) {
                    Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
                }
            }
        }

        gitPatchRepository.push(fork, gitPatchRepository.getChildBranchName());
    }

    /**
     * Unzips <code>bin</code> and <code>etc</code> from org.apache.karaf.instance.core.
     * @param artifact
     * @param targetDirectory
     * @throws IOException
     */
    private void unzipKarafInstanceJar(File artifact, File targetDirectory) throws IOException {
        String prefix = "org/apache/karaf/instance/resources/";
        try (ZipFile zf = new ZipFile(artifact)) {
            for (Enumeration<ZipArchiveEntry> e = zf.getEntries(); e.hasMoreElements();) {
                ZipArchiveEntry entry = e.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                name = name.substring(prefix.length());
                if (!name.startsWith("bin") && !name.startsWith("etc")) {
                    continue;
                }
                // flags from karaf.instance.core
                // see: org.apache.karaf.instance.core.internal.InstanceServiceImpl.createInstance()
                boolean windows = System.getProperty("os.name").startsWith("Win");
                boolean cygwin = windows && new File(System.getProperty("karaf.home"), "bin/instance").exists();

                if (!entry.isDirectory() && !entry.isUnixSymlink()) {
                    if (windows && !cygwin) {
                        if (name.startsWith("bin/") && !name.endsWith(".bat")) {
                            continue;
                        }
                    } else {
                        if (name.startsWith("bin/") && name.endsWith(".bat")) {
                            continue;
                        }
                    }
                    File file = new File(targetDirectory, name);
                    Utils.mkdirs(file.getParentFile());
                    FileOutputStream output = new EOLFixingFileOutputStream(targetDirectory, file);
                    IOUtils.copyLarge(zf.getInputStream(entry), output);
                    Utils.closeQuietly(output);
                    if (Files.getFileAttributeView(file.toPath(), PosixFileAttributeView.class) != null) {
                        if (name.startsWith("bin/") && !name.endsWith(".bat")) {
                            Files.setPosixFilePermissions(file.toPath(), getPermissionsFromUnixMode(file, 0775));
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the content of baseline Fuse/AMQ distribution to git repository.
     * These are the "biggest" baselines - we also do etc/overrides.properties reset.
     * @param git
     * @param baselineDistribution
     * @param version
     * @throws IOException
     * @throws GitAPIException
     */
    private RevCommit trackBaselineRepository(Git git, File baselineDistribution, String version) throws IOException, GitAPIException {
        for (String managedDirectory : MANAGED_DIRECTORIES) {
            FileUtils.deleteDirectory(new File(git.getRepository().getWorkTree(), managedDirectory));
        }
        unpack(baselineDistribution, git.getRepository().getWorkTree(), 1);

        String productVersion = determineVersion(git.getRepository().getWorkTree());

        git.add()
                .addFilepattern(".")
                .call();
        // remove the deletes (without touching specially-managed etc/overrides.properties)
        for (String missing : git.status().call().getMissing()) {
            git.rm().addFilepattern(missing).call();
        }
        gitPatchRepository.prepareCommit(git, String.format(MARKER_BASELINE_COMMIT_PATTERN, version)).call();

        // let's replace the reference to "patch" feature repository, to be able to do rollback to this very first
        // baseline
        File featuresCfg = new File(git.getRepository().getWorkTree(), "etc/org.apache.karaf.features.cfg");
        if (featuresCfg.isFile()) {
            if (setCurrentPatchManagementVersion(featuresCfg, productVersion)) {
                git.add()
                        .addFilepattern("etc/org.apache.karaf.features.cfg")
                        .call();
                gitPatchRepository.prepareCommit(git, String.format(MARKER_BASELINE_REPLACE_PATCH_FEATURE_PATTERN,
                        version, bundleContext.getBundle().getVersion().toString())).call();

                // let's assume that user didn't change this file and replace it with our version
                FileUtils.copyFile(featuresCfg,
                        new File(karafBase, "etc/org.apache.karaf.features.cfg"));
            }
        }

        // each baseline ships new feature repositories and from this point (or the point where new rollup patch
        // is installed) we should start with no additional overrides in org.apache.karaf.features.xml, in order
        // to have easier non-rollup patch installation - no P-patch should ADD overrides.properties - they have to
        // only MODIFY it because it's easier to revert such modification (in case P-patch is rolled back - probably
        // in different order than it was installed)
        resetOverrides(git, git.getRepository().getWorkTree(), Collections.emptyList());
        return gitPatchRepository.prepareCommit(git,
                String.format(MARKER_BASELINE_RESET_OVERRIDES_PATTERN, version)).call();
    }

    /**
     * Ensures that {@code etc/org.apache.karaf.features.cfg} and {@code etc/startup.properties} reference
     * current version of patch management bundles and features
     */
    public boolean setCurrentPatchManagementVersion(File featuresCfg, String oldVersion) throws IOException {
        boolean needsRewriting = false;
        List<String> lines = FileUtils.readLines(featuresCfg, "UTF-8");
        List<String> newVersion = new LinkedList<>();
        // in first iteration we'll record references to old patch management features and repositories
        // in Fuse 6, featuresBoot contained only feature names. In Fuse 7 versions are used too
        String prefix = "mvn:org.jboss.fuse.modules.patch/patch-features/";
        for (String line : lines) {
            if (oldVersion == null || !line.contains(prefix)) {
                newVersion.add(line);
            } else {
                // use version of current bundle, so rolling back to that baseline won't bring us
                // to old patch management
                String newLine = line.replace(oldVersion, bundleContext.getBundle().getVersion().toString());
                newVersion.add(newLine);
                if (!line.equals(newLine)) {
                    needsRewriting = true;
                }
            }
        }
        if (needsRewriting) {
            StringBuilder sb = new StringBuilder();
            for (String newLine : newVersion) {
                if (newLine.contains("patch/" + oldVersion)) {
                    newLine = newLine.replace("patch/" + oldVersion, "patch/" + bundleContext.getBundle().getVersion().toString());
                }
                if (newLine.contains("patch-management/" + oldVersion)) {
                    newLine = newLine.replace("patch-management/" + oldVersion, "patch-management/" + bundleContext.getBundle().getVersion().toString());
                }
                sb.append(newLine).append("\n");
            }
            FileUtils.write(featuresCfg, sb.toString(), "UTF-8");
            return true;
        }
        return false;
    }

    /**
     * Update private history tracking branch for standalone child container - generally
     * when patch management is done in another container. This method is called when it's needed, not every time
     * patch-management bundle is started/stopped/updated.
     * Method <strong>always</strong> changes private history branch and align current version
     * @param fork
     */
    private void ensureCorrectContainerHistory(Git fork, String version) throws IOException, GitAPIException {
        if (fork.getRepository().findRef("refs/heads/" + gitPatchRepository.getMainBranchName()) == null) {
            String startPoint = "patch-management^{commit}";
            if (fork.getRepository().findRef("refs/remotes/origin/" + gitPatchRepository.getMainBranchName()) != null) {
                startPoint = "refs/remotes/origin/" + gitPatchRepository.getMainBranchName();
            }
            gitPatchRepository.checkout(fork)
                    .setName(gitPatchRepository.getMainBranchName())
                    .setStartPoint(startPoint)
                    .setCreateBranch(true)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .call();
        } else {
            gitPatchRepository.checkout(fork)
                    .setName(gitPatchRepository.getMainBranchName())
                    .call();
        }

        // user changes in history
        ObjectId since = fork.getRepository().resolve("patch-management^{commit}");
        // we'll pick all user changes between baseline and main patch branch without P installations
        ObjectId to = fork.getRepository().resolve(gitPatchRepository.getMainBranchName() + "^{commit}");
        Iterable<RevCommit> mainChanges = fork.log().addRange(since, to).call();
        List<RevCommit> userChanges = new LinkedList<>();
        for (RevCommit rc : mainChanges) {
            if (isUserChangeCommit(rc)) {
                userChanges.add(rc);
            }
        }

        // let's rewrite history
        fork.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(String.format(env.getBaselineTagFormat(), version))
                .call();

        // and pick up user changes just like we'd install Rollup patch

        // reapply those user changes that are not conflicting
        // for each conflicting cherry-pick we do a backup of user files, to be able to restore them
        // when rollup patch is rolled back
        ListIterator<RevCommit> it = userChanges.listIterator(userChanges.size());
        int prefixSize = Integer.toString(userChanges.size()).length();
        int count = 1;

        // we may have unadded changes - when file mode is changed
        fork.reset().setMode(ResetCommand.ResetType.MIXED).call();
        fork.reset().setMode(ResetCommand.ResetType.HARD).call();

        while (it.hasPrevious()) {
            RevCommit userChange = it.previous();
            String prefix = String.format("%0" + prefixSize + "d-%s", count++, userChange.getName());
            CherryPickResult result = fork.cherryPick()
                    .include(userChange)
                    .setNoCommit(true)
                    .call();
            // no backup (!?)
            handleCherryPickConflict(null, fork, result, userChange, false, PatchKind.ROLLUP, null, false, false);

            gitPatchRepository.prepareCommit(fork, userChange.getFullMessage()).call();

            // we may have unadded changes - when file mode is changed
            fork.reset().setMode(ResetCommand.ResetType.MIXED).call();
            fork.reset().setMode(ResetCommand.ResetType.HARD).call();
        }

        gitPatchRepository.push(fork, gitPatchRepository.getMainBranchName());
    }

    /**
     * <p>Applies existing user changes in ${karaf.home}/{bin,etc,lib,quickstarts,welcome-content} directories to patch
     * management Git repository, doesn't modify ${karaf.home}</p>
     * <p>TODO: Maybe we should ask user whether the change was intended or not? blacklist some changes?</p>
     * @param git non-bare repository to perform the operation
     */
    public void applyUserChanges(Git git) throws GitAPIException, IOException {
        File wcDir = git.getRepository().getWorkTree();

        try {
            // let's simply copy all user files on top of git working copy
            // then we can check the differences simply by committing the changes
            // there should be no conflicts, because we're not merging anything
            // "true" would mean that target dir is first deleted to detect removal of files
            copyManagedDirectories(karafBase, wcDir, false, true, false);

            // commit the changes to main repository
            Status status = git.status().call();
            if (!status.isClean()) {
                boolean amend = false;
                if (status.getUntracked().size() == 0
                        && status.getMissing().size() == 0
                        && status.getModified().size() == 1) {
                    // in Fuse 6.x we were amending changes to etc/io.fabric8.mq.fabric.server-broker.cfg
                    if ("a-file-that-changes-on-each-restart".equals(status.getModified().iterator().next())) {
                        amend = true;
                    }
                }
                Activator.log(LogService.LOG_INFO, (amend ? "Amending" : "Storing") + " user changes");

                git.add()
                        .addFilepattern(".")
                        .call();

                // let's not do removals when tracking user changes. if we do, cherry-picking user changes over
                // a rollup patch that introduced new file would simply remove it
//                for (String name : status.getMissing()) {
//                    git.rm().addFilepattern(name).call();
//                }

                gitPatchRepository.prepareCommit(git, MARKER_USER_CHANGES_COMMIT)
                        .setAmend(amend)
                        .call();
                gitPatchRepository.push(git);

                // now main repository has exactly the same content as ${karaf.home}
                // We have two methods of synchronization wrt future rollup changes:
                // 1. we can pull from "origin" in the MAIN working copy (${karaf.hoome}) (making sure the copy is initialized)
                // 2. we can apply rollup patch to temporary fork + working copy, perform merges, resolve conflicts, etc
                //    and if everything goes fine, simply override ${karaf.hoome} content with the working copy content
                // method 2. doesn't require real working copy and ${karaf.home}/.git directory
            } else {
                Activator.log(LogService.LOG_INFO, "No user changes detected");
            }
        } catch (GitAPIException | IOException e) {
            Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
        }
    }

    /**
     * Copy content of managed directories from source (like ${karaf.home}) to target (e.g. working copy of git repository)
     * @param sourceDir
     * @param targetDir
     * @param removeTarget whether to delete content of targetDir/<em>managedDirectory</em> first (helpful to detect removals from source)
     * @param onlyModified whether to copy only modified files (to preserve modification time when target file is
     * not changed)
     * @param useLibNext whether to rename lib to lib.next during copy
     * @throws IOException
     */
    private void copyManagedDirectories(File sourceDir, File targetDir, boolean removeTarget, boolean onlyModified, boolean useLibNext) throws IOException {
        for (String dir : MANAGED_DIRECTORIES) {
            File managedSrcDir = new File(sourceDir, dir);
            if (!managedSrcDir.exists()) {
                continue;
            }
            File destDir = new File(targetDir, dir);
            if (useLibNext && "lib".equals(dir)) {
                destDir = new File(targetDir, "lib.next");
                if (removeTarget) {
                    FileUtils.deleteQuietly(destDir);
                }
                FileUtils.copyDirectory(managedSrcDir, destDir);
            } else {
                if (removeTarget) {
                    FileUtils.deleteQuietly(destDir);
                }
                EOLFixingFileUtils.copyDirectory(managedSrcDir, targetDir, destDir, onlyModified);
            }
            if ("bin".equals(dir)) {
                // repair file permissions
                File[] files = destDir.listFiles();
                if (files != null) {
                    for (File script : files) {
                        if (!script.getName().endsWith(".bat")) {
                            if (Files.getFileAttributeView(script.toPath(), PosixFileAttributeView.class) != null) {
                                Files.setPosixFilePermissions(script.toPath(), getPermissionsFromUnixMode(script, 0775));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Assuming there's no track of adding patch-management bundle to etc/startup.properties, we have to
     * ensure it's present in startup.properties
     * This should not happen in Fuse 7
     * @param git non-bare repository to perform the operation
     */
    private RevCommit installPatchManagementBundle(Git git) throws IOException, GitAPIException {
        String bundleVersion = bundleContext.getBundle().getVersion().toString();

        return replacePatchManagementBundleInStartupPropertiesIfNecessary(git, bundleVersion);
    }

    /**
     * One stop method that does everything related to installing patch-management bundle in etc/startup.properties.
     * It removes old version of the bundle, doesn't do anything if the bundle is already there and appends a declaration if there was none.
     * @param git
     * @param bundleVersion
     * @throws IOException
     * @throws GitAPIException
     */
    private RevCommit replacePatchManagementBundleInStartupPropertiesIfNecessary(Git git, String bundleVersion) throws IOException, GitAPIException {
        boolean modified = false;
        boolean installed = false;

        File etcStartupProperties = new File(git.getRepository().getWorkTree(), "etc/startup.properties");
        List<String> lines = FileUtils.readLines(etcStartupProperties, "UTF-8");
        List<String> newVersion = new LinkedList<>();
        for (String line : lines) {
            if (!line.startsWith("mvn\\:org.jboss.fuse.modules.patch/patch-management/")) {
                // copy unchanged
                newVersion.add(line);
            } else {
                // is it old, same, (newer??) version?
                Matcher matcher = VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    // it should match
                    String alreadyInstalledVersion = matcher.group(1);
                    Version v1 = Utils.getOsgiVersion(alreadyInstalledVersion);
                    Version v2 = Utils.getOsgiVersion(bundleVersion);
                    if (v1.equals(v2)) {
                        // already installed at correct version
                        installed = true;
                    } else if (v1.compareTo(v2) < 0) {
                        // we'll install new version
                        modified = true;
                    } else {
                        // newer installed? why?
                    }
                }
            }
        }
        if (modified || !installed) {
            newVersion.add("");
            newVersion.add("# installed by patch-management");
            newVersion.add(String.format("mvn\\:org.jboss.fuse.modules.patch/patch-management/%s=%d",
                    bundleVersion, Activator.PATCH_MANAGEMENT_START_LEVEL));

            StringBuilder sb = new StringBuilder();
            for (String newLine : newVersion) {
                sb.append(newLine).append("\n");
            }
            FileUtils.write(new File(git.getRepository().getWorkTree(), "etc/startup.properties"), sb.toString(), "UTF-8");

            // now to git working copy
            git.add()
                    .addFilepattern("etc/startup.properties")
                    .call();

            RevCommit commit = gitPatchRepository
                    .prepareCommit(git, String.format(MARKER_PATCH_MANAGEMENT_INSTALLATION_COMMIT_PATTERN, bundleVersion))
                    .call();

            // "checkout" the above change in main "working copy" (${karaf.home})
            applyChanges(git, commit.getParent(0), commit);

            Activator.log(LogService.LOG_INFO, String.format("patch-management-%s.jar installed in etc/startup.properties.", bundleVersion));

            return commit;
        }

        return null;
    }

    /**
     * <p>This method updates ${karaf.base} simply by copying all files from currently checked out working copy
     * (usually HEAD of main patch branch) to <code>${karaf.base}</code></p>
     * @param git
     * @param restartFileInstall whether to start fileinstall bundle at the end
     * @throws IOException
     * @throws GitAPIException
     */
    private void applyChanges(Git git, boolean restartFileInstall) throws IOException, GitAPIException {
        Bundle fileInstall = null;
        for (Bundle b : systemContext.getBundles()) {
            if (b.getSymbolicName() != null
                    && Utils.stripSymbolicName(b.getSymbolicName()).equals("org.apache.felix.fileinstall")) {
                fileInstall = b;
                break;
            }
        }

        if (fileInstall != null) {
            try {
                fileInstall.stop(Bundle.STOP_TRANSIENT);
            } catch (Exception e) {
                Activator.log(LogService.LOG_WARNING, e.getMessage());
            }
        }

        File wcDir = git.getRepository().getWorkTree();
        copyManagedDirectories(wcDir, karafBase, true, true, true);
        File lib = new File(wcDir, "lib");
        if (lib.exists()) {
            FileUtils.copyDirectory(lib, new File(karafBase, "lib.next"));
        }
        // we do exception for etc/overrides.properties
        File overrides = new File(karafBase, "etc/overrides.properties");
        if (overrides.exists() && overrides.length() == 0) {
            FileUtils.deleteQuietly(overrides);
        }
        FileUtils.deleteQuietly(new File(karafBase, "patch-info.txt"));

        if (restartFileInstall && fileInstall != null) {
            try {
                fileInstall.start(Bundle.START_TRANSIENT);
            } catch (Exception e) {
                Activator.log(LogService.LOG_WARNING, e.getMessage());
            }
        }
    }

    /**
     * <p>This method takes a range of commits (<code>c1..c2</code>) and performs manual update to ${karaf.home}.
     * If ${karaf.home} was also a checked out working copy, it'd be a matter of <code>git pull</code>. We may consider
     * this implementation, but now I don't want to keep <code>.git</code> directory in ${karaf.home}. Also, jgit
     * doesn't support <code>.git</code> <em>platform agnostic symbolic link</em>
     * (see: <code>git init --separate-git-dir</code>)</p>
     * <p>We don't have to fetch data from repository blobs, because <code>git</code> still points to checked-out
     * working copy</p>
     * <p>TODO: maybe we just have to copy <strong>all</strong> files from working copy to ${karaf.home}?</p>
     * @param git
     * @param commit1
     * @param commit2
     */
    private void applyChanges(Git git, RevCommit commit1, RevCommit commit2) throws IOException, GitAPIException {
        File wcDir = git.getRepository().getWorkTree();

        List<DiffEntry> diff = this.gitPatchRepository.diff(git, commit1, commit2);

        // Changes to the lib dir get done in the lib.next directory.  Lets copy
        // the lib dir just in case we do have modification to it.
        File lib = new File(karafBase, "lib");
        if (lib.isDirectory()) {
            FileUtils.copyDirectory(lib, new File(karafBase, "lib.next"));
        }
        boolean libDirectoryChanged = false;

        for (DiffEntry de : diff) {
            DiffEntry.ChangeType ct = de.getChangeType();
            /*
             * old path:
             *  - file add: always /dev/null
             *  - file modify: always getNewPath()
             *  - file delete: always the file being deleted
             *  - file copy: source file the copy originates from
             *  - file rename: source file the rename originates from
             * new path:
             *  - file add: always the file being created
             *  - file modify: always getOldPath()
             *  - file delete: always /dev/null
             *  - file copy: destination file the copy ends up at
             *  - file rename: destination file the rename ends up at
             */
            String newPath = de.getNewPath();
            String oldPath = de.getOldPath();
            switch (ct) {
                case ADD:
                case MODIFY:
                    Activator.log(LogService.LOG_DEBUG, "[PATCH-change] Modifying " + newPath);
                    String targetPath = newPath;
                    if (newPath.startsWith("lib/")) {
                        targetPath = "lib.next/" + newPath.substring(4);
                        libDirectoryChanged = true;
                    }
                    File srcFile = new File(wcDir, newPath);
                    File destFile = new File(karafBase, targetPath);
                    // we do exception for etc/overrides.properties
                    if ("etc/overrides.properties".equals(newPath) && srcFile.exists() && srcFile.length() == 0) {
                        FileUtils.deleteQuietly(destFile);
                    } else {
                        FileUtils.copyFile(srcFile, destFile);
                    }
                    break;
                case DELETE:
                    Activator.log(LogService.LOG_DEBUG, "[PATCH-change] Deleting " + oldPath);
                    if (oldPath.startsWith("lib/")) {
                        oldPath = "lib.next/" + oldPath.substring(4);
                        libDirectoryChanged = true;
                    }
                    FileUtils.deleteQuietly(new File(karafBase, oldPath));
                    break;
                case COPY:
                case RENAME:
                    // not handled now
                    break;
            }
        }

        if (!libDirectoryChanged) {
            // lib.next directory might not be needed.
            FileUtils.deleteDirectory(new File(karafBase, "lib.next"));
        }

        FileUtils.deleteQuietly(new File(karafBase, "patch-info.txt"));
    }

    @Override
    public void checkPendingPatches() {
        File[] pendingPatches = patchesDir.listFiles(pathname ->
                pathname.exists() && pathname.getName().endsWith(".pending"));
        if (pendingPatches == null || pendingPatches.length == 0) {
            return;
        }

        final String dataCache = systemContext.getProperty("org.osgi.framework.storage");

        for (File pending : pendingPatches) {
            try {
                Pending what = Pending.valueOf(FileUtils.readFileToString(pending, "UTF-8"));
                final String prefix = what == Pending.ROLLUP_INSTALLATION ? "install" : "rollback";

                String name = pending.getName().replaceFirst("\\.pending$", "");
                if (isStandaloneChild()) {
                    if (name.endsWith("." + System.getProperty("karaf.name") + ".patch")) {
                        name = name.replaceFirst("\\." + System.getProperty("karaf.name"), "");
                    } else {
                        continue;
                    }
                }
                File patchFile = new File(pending.getParentFile(), name);
                if (!patchFile.isFile()) {
                    Activator.log(LogService.LOG_INFO, "Ignoring patch result file: " + patchFile.getName());
                    continue;
                }
                PatchData patchData = PatchData.load(new FileInputStream(patchFile));
                Patch patch = loadPatch(new PatchDetailsRequest(patchData.getId()));

                String dataFilesName = patchData.getId() + ".datafiles";
                if (isStandaloneChild()) {
                    dataFilesName = patchData.getId() + "." + System.getProperty("karaf.name") + ".datafiles";
                }
                final File dataFilesBackupDir = new File(pending.getParentFile(), dataFilesName);
                final Properties backupProperties = new Properties();
                FileInputStream inStream = new FileInputStream(new File(dataFilesBackupDir, "backup-" + prefix + ".properties"));
                backupProperties.load(inStream);
                Utils.closeQuietly(inStream);

                // 1. we should have very few currently installed bundles (only from etc/startup.properties)
                //    and none of them is ACTIVE now, because we (patch-management) are at SL=2
                //    maybe one of those bundles has data directory to restore?
                for (Bundle b : systemContext.getBundles()) {
                    if (b.getSymbolicName() != null) {
                        String key = String.format("%s$$%s", stripSymbolicName(b.getSymbolicName()), b.getVersion().toString());
                        if (backupProperties.containsKey(key)) {
                            String backupDirName = backupProperties.getProperty(key);
                            File backupDir = new File(dataFilesBackupDir, prefix + "/" + backupDirName + "/data");
                            restoreDataDirectory(dataCache, b, backupDir);
                            // we no longer want to restore this dir
                            backupProperties.remove(key);
                        }
                    }
                }

                // 2. We can however have more bundle data backups - we'll restore them after each bundle
                //    is INSTALLED and we'll use listener for this
                BundleListener bundleListener = new SynchronousBundleListener() {
                    @Override
                    public void bundleChanged(BundleEvent event) {
                        Bundle b = event.getBundle();
                        if (event.getType() == BundleEvent.INSTALLED && b.getSymbolicName() != null) {
                            String key = String.format("%s$$%s", stripSymbolicName(b.getSymbolicName()), b.getVersion().toString());
                            if (backupProperties.containsKey(key)) {
                                String backupDirName = backupProperties.getProperty(key);
                                File backupDir = new File(dataFilesBackupDir, prefix + "/" + backupDirName + "/data");
                                restoreDataDirectory(dataCache, b, backupDir);
                            }
                        }
                    }
                };
                systemContext.addBundleListener(bundleListener);
                pendingPatchesListeners.put(patchData.getId(), bundleListener);
            } catch (Exception e) {
                Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
            }
        }
    }

    @Override
    public boolean isStandaloneChild() {
        return env == EnvType.STANDALONE_CHILD;
    }

    @Override
    public void delete(Patch patch) throws PatchException {
        try {
            awaitInitialization();
        } catch (InterruptedException e) {
            throw new PatchException("Patch management system is not ready yet");
        }

        Git fork = null;
        try {
            Git mainRepository = gitPatchRepository.findOrCreateMainGitRepository();
            // prepare single fork for all the below operations
            fork = gitPatchRepository.cloneRepository(mainRepository, true);

            // we don't actually have to delete baselines for root and child containers - even if deleted patch
            // will be added again, baselines won't change even if added again.
            // and there could be problems if patched root container was used to create child container -
            // from the point of view of the child there's no patch installed.

            // the only thing we need to do (assuming the patch:update command itself checked that the patch
            // isn't actually installed) is to delete patch branch

            String patchBranch = "patch-" + patch.getPatchData().getId();
            fork.branchDelete().setBranchNames(patchBranch).call();

            fork.push()
                    .setRefSpecs(new RefSpec()
                            .setSource(null)
                            .setDestination("refs/heads/" + patchBranch))
                    .call();

            if (patch.getPatchData().getPatchDirectory() != null) {
                FileUtils.deleteDirectory(patch.getPatchData().getPatchDirectory());
            }
            FileUtils.deleteQuietly(new File(patch.getPatchData().getPatchLocation(), patch.getPatchData().getId() + ".datafiles"));
            FileUtils.deleteQuietly(new File(patch.getPatchData().getPatchLocation(), patch.getPatchData().getId() + ".patch"));
            FileUtils.deleteQuietly(new File(patch.getPatchData().getPatchLocation(), patch.getPatchData().getId() + ".patch.result"));
            FileUtils.deleteQuietly(new File(patch.getPatchData().getPatchLocation(), patch.getPatchData().getId() + ".patch.result.html"));

            mainRepository.gc().call();
        } catch (IOException | GitAPIException e) {
            throw new PatchException(e.getMessage(), e);
        } finally {
            if (fork != null) {
                gitPatchRepository.closeRepository(fork, true);
            }
        }
    }

    /**
     * If <code>backupDir</code> exists, restore bundle data from this location and place in Felix bundle cache
     * @param dataCache data cache location (by default: <code>${karaf.home}/data/cache</code>)
     * @param bundle
     * @param backupDir
     */
    private void restoreDataDirectory(String dataCache, Bundle bundle, File backupDir) {
        if (backupDir.isDirectory()) {
            Activator.log2(LogService.LOG_INFO, String.format("Restoring data directory for bundle %s", bundle.toString()));
            File bundleDataDir = new File(dataCache, "bundle" + bundle.getBundleId() + "/data");
            try {
                FileUtils.copyDirectory(backupDir, bundleDataDir);
            } catch (IOException e) {
                Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
            }
        }
    }

    /**
     * Moves patch files, descriptors and results into managed location
     * @param systemBundleData
     */
    private void movePatchData(File systemBundleData) throws IOException {
        FileUtils.copyDirectory(systemBundleData, patchesDir);
        FileUtils.deleteDirectory(systemBundleData);
    }

}

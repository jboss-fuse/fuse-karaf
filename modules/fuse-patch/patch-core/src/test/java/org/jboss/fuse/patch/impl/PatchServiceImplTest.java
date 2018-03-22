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
package org.jboss.fuse.patch.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.management.Artifact;
import org.jboss.fuse.patch.management.BundleUpdate;
import org.jboss.fuse.patch.management.Patch;
import org.jboss.fuse.patch.management.PatchData;
import org.jboss.fuse.patch.management.PatchException;
import org.jboss.fuse.patch.management.PatchKind;
import org.jboss.fuse.patch.management.PatchManagement;
import org.jboss.fuse.patch.management.PatchResult;
import org.jboss.fuse.patch.management.Utils;
import org.jboss.fuse.patch.management.impl.GitPatchManagementServiceImpl;
import org.jboss.fuse.patch.management.impl.GitPatchRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.Version;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.component.ComponentContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.jboss.fuse.patch.impl.PatchTestSupport.getDirectoryForResource;
import static org.jboss.fuse.patch.management.Utils.stripSymbolicName;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PatchServiceImplTest {

    File baseDir;

    File karaf;
    File storage;
    File bundlev131;
    File bundlev132;
    File bundlev140;
    File bundlev200;
    File patch132;
    File patch140;
    File patch200;
    private String oldName;

    @Before
    public void setUp() throws Exception {
        baseDir = getDirectoryForResource("log4j.properties");

        URL.setURLStreamHandlerFactory(new CustomBundleURLStreamHandlerFactory());
        generateData();
        oldName = System.setProperty("karaf.name", "x");
    }

    @After
    public void tearDown() throws Exception {
        Field field = URL.class.getDeclaredField("factory");
        field.setAccessible(true);
        field.set(null, null);
        if (oldName != null) {
            System.setProperty("karaf.name", oldName);
        }
    }

    @Test
    public void testLoadWithoutRanges() throws IOException, GitAPIException {
        BundleContext bundleContext = mock(BundleContext.class);
        ComponentContext componentContext = mock(ComponentContext.class);
        Bundle sysBundle = mock(Bundle.class);
        BundleContext sysBundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        Bundle bundle2 = mock(Bundle.class);
        FrameworkWiring wiring = mock(FrameworkWiring.class);
        GitPatchRepository repository = mock(GitPatchRepository.class);

        //
        // Create a new service, download a patch
        //
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(storage.toString());
        when(repository.getManagedPatch(anyString())).thenReturn(null);
        when(repository.findOrCreateMainGitRepository()).thenReturn(null);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.home"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.base"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.name"))
                .thenReturn("root");
        when(sysBundleContext.getProperty("karaf.instances"))
                .thenReturn(karaf.getCanonicalPath() + "/instances");
        when(sysBundleContext.getProperty("karaf.data")).thenReturn(karaf.getCanonicalPath() + "/data");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");

        PatchManagement pm = new GitPatchManagementServiceImpl(bundleContext);
        ((GitPatchManagementServiceImpl) pm).setGitPatchRepository(repository);

        PatchServiceImpl service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);

        PatchData pd = PatchData.load(getClass().getClassLoader().getResourceAsStream("test1.patch"));
        assertEquals(2, pd.getBundles().size());
        assertTrue(pd.getRequirements().isEmpty());
    }

    @Test
    public void testLoadWithRanges() throws IOException {
        PatchServiceImpl service = createMockServiceImpl();

        PatchData pd = PatchData.load(getClass().getClassLoader().getResourceAsStream("test2.patch"));
        assertEquals(2, pd.getBundles().size());
        assertEquals("[1.0.0,2.0.0)", pd.getVersionRange("mvn:io.fabric8.test/test1/1.0.0"));
        assertNull(pd.getVersionRange("mvn:io.fabric8.test/test2/1.0.0"));
        assertTrue(pd.getRequirements().isEmpty());
    }

    @Test
    public void testLoadWithPrereqs() throws IOException {
        PatchServiceImpl service = createMockServiceImpl();

        PatchData pd = PatchData.load(getClass().getClassLoader().getResourceAsStream("test-with-prereq.patch"));
        assertEquals(2, pd.getBundles().size());
        assertEquals(1, pd.getRequirements().size());
        assertTrue(pd.getRequirements().contains("prereq1"));
        assertNull(pd.getVersionRange("mvn:io.fabric8.test/test2/1.0.0"));
    }

    @Test
    public void testCheckPrerequisitesMissing() throws IOException {
        PatchServiceImpl service = createMockServiceImpl(getDirectoryForResource("prereq/patch1.patch"));

        Patch patch = service.getPatch("patch1");
        assertNotNull(patch);
        try {
            service.checkPrerequisites(patch);
            fail("Patch will missing prerequisites should not pass check");
        } catch (PatchException e) {
            assertTrue(e.getMessage().toLowerCase().contains("required patch 'prereq1' is missing"));
        }
    }

    @Test
    public void testCheckPrerequisitesNotInstalled() throws IOException {
        PatchServiceImpl service = createMockServiceImpl(getDirectoryForResource("prereq/patch2.patch"));

        Patch patch = service.getPatch("patch2");
        assertNotNull(patch);
        try {
            service.checkPrerequisites(patch);
            fail("Patch will prerequisites that are not yet installed should not pass check");
        } catch (PatchException e) {
            assertTrue(e.getMessage().toLowerCase().contains("required patch 'prereq2' is not installed"));
        }
    }

    @Test
    public void testCheckPrerequisitesSatisfied() throws IOException {
        PatchServiceImpl service = createMockServiceImpl(getDirectoryForResource("prereq/patch3.patch"));

        Patch patch = service.getPatch("patch3");
        assertNotNull(patch);
        // this should not throw a PatchException
        service.checkPrerequisites(patch);
    }

    @Test
    public void testCheckPrerequisitesMultiplePatches() throws IOException {
        PatchServiceImpl service = createMockServiceImpl(getDirectoryForResource("prereq/patch1.patch"));

        Collection<Patch> patches = new LinkedList<Patch>();
        patches.add(service.getPatch("patch3"));
        // this should not throw a PatchException
        service.checkPrerequisites(patches);

        patches.add(service.getPatch("patch2"));
        try {
            service.checkPrerequisites(patches);
            fail("Should not pass check if one of the patches is missing a requirement");
        } catch (PatchException e) {
            // graciously do nothing, this is OK
        }

    }

    /*
     * Create a mock patch service implementation with access to the generated data directory
     */
    private PatchServiceImpl createMockServiceImpl() throws IOException {
        return createMockServiceImpl(storage);
    }

    /*
     * Create a mock patch service implementation with a provided patch storage location
     */
    private PatchServiceImpl createMockServiceImpl(File patches) throws IOException {
        ComponentContext componentContext = mock(ComponentContext.class);
        BundleContext bundleContext = mock(BundleContext.class);
        Bundle sysBundle = mock(Bundle.class);
        BundleContext sysBundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        GitPatchRepository repository = mock(GitPatchRepository.class);

        //
        // Create a new service, download a patch
        //
        when(bundle.getVersion()).thenReturn(new Version(1, 2, 0));
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(patches.toString());
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.data")).thenReturn(patches.getParent() + "/data");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");
        try {
            when(repository.getManagedPatch(anyString())).thenReturn(null);
            when(repository.findOrCreateMainGitRepository()).thenReturn(null);
            when(sysBundleContext.getProperty("karaf.home"))
                    .thenReturn(karaf.getCanonicalPath());
            when(sysBundleContext.getProperty("karaf.base"))
                    .thenReturn(karaf.getCanonicalPath());
            when(sysBundleContext.getProperty("karaf.name"))
                    .thenReturn("root");
            when(sysBundleContext.getProperty("karaf.instances"))
                    .thenReturn(karaf.getCanonicalPath() + "/instances");
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        PatchManagement pm = new GitPatchManagementServiceImpl(bundleContext);
        ((GitPatchManagementServiceImpl) pm).setGitPatchRepository(repository);

        PatchServiceImpl service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);
        return service;
    }

    private void setField(PatchServiceImpl service, String fieldName, Object value) {
        Field f = null;
        try {
            f = service.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testPatch() throws Exception {
        ComponentContext componentContext = mock(ComponentContext.class);
        BundleContext bundleContext = mock(BundleContext.class);
        Bundle sysBundle = mock(Bundle.class);
        BundleContext sysBundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        Bundle bundle2 = mock(Bundle.class);
        FrameworkWiring wiring = mock(FrameworkWiring.class);
        GitPatchRepository repository = mock(GitPatchRepository.class);

        //
        // Create a new service, download a patch
        //
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(storage.toString());
        when(repository.getManagedPatch(anyString())).thenReturn(null);
        when(repository.findOrCreateMainGitRepository()).thenReturn(null);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.home"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.base"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.name"))
                .thenReturn("root");
        when(sysBundleContext.getProperty("karaf.instances"))
                .thenReturn(karaf.getCanonicalPath() + "/instances");
        when(sysBundleContext.getProperty("karaf.data")).thenReturn(karaf.getCanonicalPath() + "/data");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");

        PatchManagement pm = mockManagementService(bundleContext);
        ((GitPatchManagementServiceImpl) pm).setGitPatchRepository(repository);

        PatchServiceImpl service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);

        try {
            service.download(new URL("file:" + storage + "/temp/f00.zip"));
            fail("Should have thrown exception on non existent patch file.");
        } catch (Exception ignored) {
        }

        Iterable<Patch> patches = service.download(patch132.toURI().toURL());
        assertNotNull(patches);
        Iterator<Patch> it = patches.iterator();
        assertTrue(it.hasNext());
        Patch patch = it.next();
        assertNotNull(patch);
        assertEquals("patch-1.3.2", patch.getPatchData().getId());
        assertNotNull(patch.getPatchData().getBundles());
        assertEquals(1, patch.getPatchData().getBundles().size());
        Iterator<String> itb = patch.getPatchData().getBundles().iterator();
        assertEquals("mvn:foo/my-bsn/1.3.2", itb.next());
        assertNull(patch.getResult());

        //
        // Simulate the patch
        //

        when(sysBundleContext.getBundles()).thenReturn(new Bundle[] { bundle });
        when(sysBundleContext.getServiceReference("io.fabric8.api.FabricService")).thenReturn(null);
        when(bundle.getSymbolicName()).thenReturn("my-bsn");
        when(bundle.getVersion()).thenReturn(new Version("1.3.1"));
        when(bundle.getLocation()).thenReturn("location");
        when(bundle.getBundleId()).thenReturn(123L);
        BundleStartLevel bsl = mock(BundleStartLevel.class);
        when(bsl.getStartLevel()).thenReturn(30);
        when(bundle.adapt(BundleStartLevel.class)).thenReturn(bsl);
        when(bundle.getState()).thenReturn(1);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");

        PatchResult result = service.install(patch, true);
        assertNotNull(result);
        assertTrue(result.isSimulation());

        //
        // Recreate a new service and verify the downloaded patch is still available
        //

        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(storage.toString());
        when(sysBundleContext.getProperty("karaf.home"))
                .thenReturn(karaf.toString());
        when(sysBundleContext.getProperty("karaf.base"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.name"))
                .thenReturn("root");
        when(sysBundleContext.getProperty("karaf.instances"))
                .thenReturn(karaf.getCanonicalPath() + "/instances");
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");
        when(repository.getManagedPatch(anyString())).thenReturn(null);
        when(repository.findOrCreateMainGitRepository()).thenReturn(null);

        service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);

        patches = service.getPatches();
        assertNotNull(patches);
        it = patches.iterator();
        assertTrue(it.hasNext());
        patch = it.next();
        assertNotNull(patch);
        assertEquals("patch-1.3.2", patch.getPatchData().getId());
        assertNotNull(patch.getPatchData().getBundles());
        assertEquals(1, patch.getPatchData().getBundles().size());
        itb = patch.getPatchData().getBundles().iterator();
        assertEquals("mvn:foo/my-bsn/1.3.2", itb.next());
        assertNull(patch.getResult());

        //
        // Install the patch
        //

        when(sysBundleContext.getBundles()).thenReturn(new Bundle[] { bundle });
        when(sysBundleContext.getServiceReference("io.fabric8.api.FabricService")).thenReturn(null);
        when(bundle.getSymbolicName()).thenReturn("my-bsn");
        when(bundle.getVersion()).thenReturn(new Version("1.3.1"));
        when(bundle.getLocation()).thenReturn("location");
        when(bundle.getHeaders()).thenReturn(new Hashtable<String, String>());
        when(bundle.getBundleId()).thenReturn(123L);
        bundle.update(any(InputStream.class));
        when(sysBundleContext.getBundles()).thenReturn(new Bundle[] { bundle });
        when(bundle.getState()).thenReturn(Bundle.INSTALLED);
        when(bundle.getRegisteredServices()).thenReturn(null);
        when(bundle.adapt(BundleStartLevel.class)).thenReturn(bsl);
        when(bsl.getStartLevel()).thenReturn(30);
        when(sysBundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.adapt(FrameworkWiring.class)).thenReturn(wiring);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        bundle.start();
        doAnswer(invocationOnMock -> {
            ((FrameworkListener) invocationOnMock.getArgument(1)).frameworkEvent(null);
            return invocationOnMock.getMock();
        }).when(wiring).refreshBundles(any(), any(FrameworkListener.class));

        result = service.install(patch, false);
        assertNotNull(result);
        assertSame(result, patch.getResult());
        assertFalse(patch.getResult().isSimulation());

        //
        // Recreate a new service and verify the downloaded patch is still available and installed
        //

        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(repository.getManagedPatch(anyString())).thenReturn(null);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(storage.toString());
        when(sysBundleContext.getProperty("karaf.home"))
                .thenReturn(karaf.toString());
        when(sysBundleContext.getProperty("karaf.base"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.name"))
                .thenReturn("root");
        when(sysBundleContext.getProperty("karaf.instances"))
                .thenReturn(karaf.getCanonicalPath() + "/instances");
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");

        service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);

        patches = service.getPatches();
        assertNotNull(patches);
        it = patches.iterator();
        assertTrue(it.hasNext());
        patch = it.next();
        assertNotNull(patch);
        assertEquals("patch-1.3.2", patch.getPatchData().getId());
        assertNotNull(patch.getPatchData().getBundles());
        assertEquals(1, patch.getPatchData().getBundles().size());
        itb = patch.getPatchData().getBundles().iterator();
        assertEquals("mvn:foo/my-bsn/1.3.2", itb.next());
        assertNotNull(patch.getResult());
    }

    private GitPatchManagementServiceImpl mockManagementService(final BundleContext bundleContext) throws IOException {
        return new GitPatchManagementServiceImpl(bundleContext) {
            @Override
            public Patch trackPatch(PatchData patchData) throws PatchException {
                return new Patch(patchData, null);
            }

            @Override
            public String beginInstallation(PatchKind kind) {
                this.pendingTransactionsTypes.put("tx", kind);
                this.pendingTransactions.put("tx", null);
                return "tx";
            }

            @Override
            public void install(String transaction, Patch patch, List<BundleUpdate> bundleUpdatesInThisPatch) {
            }

            @Override
            public void rollbackInstallation(String transaction) {
            }

            @Override
            public void commitInstallation(String transaction) {
            }

        };
    }

    @Test
    public void testPatchWithVersionRanges() throws Exception {
        ComponentContext componentContext = mock(ComponentContext.class);
        BundleContext bundleContext = mock(BundleContext.class);
        Bundle sysBundle = mock(Bundle.class);
        BundleContext sysBundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        Bundle bundle2 = mock(Bundle.class);
        FrameworkWiring wiring = mock(FrameworkWiring.class);
        GitPatchRepository repository = mock(GitPatchRepository.class);

        //
        // Create a new service, download a patch
        //
        when(componentContext.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle(0)).thenReturn(sysBundle);
        when(sysBundle.getBundleContext()).thenReturn(sysBundleContext);
        when(sysBundleContext.getProperty(PatchService.PATCH_LOCATION))
                .thenReturn(storage.toString());
        when(repository.getManagedPatch(anyString())).thenReturn(null);
        when(repository.findOrCreateMainGitRepository()).thenReturn(null);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");
        when(sysBundleContext.getProperty("karaf.home"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.base"))
                .thenReturn(karaf.getCanonicalPath());
        when(sysBundleContext.getProperty("karaf.name"))
                .thenReturn("root");
        when(sysBundleContext.getProperty("karaf.instances"))
                .thenReturn(karaf.getCanonicalPath() + "/instances");
        when(sysBundleContext.getProperty("karaf.data")).thenReturn(karaf.getCanonicalPath() + "/data");
        when(sysBundleContext.getProperty("karaf.etc")).thenReturn(karaf.getCanonicalPath() + "/etc");

        PatchManagement pm = mockManagementService(bundleContext);
        ((GitPatchManagementServiceImpl) pm).setGitPatchRepository(repository);

        PatchServiceImpl service = new PatchServiceImpl();
        setField(service, "patchManagement", pm);
        service.activate(componentContext);
        Iterable<Patch> patches = service.download(patch140.toURI().toURL());
        assertNotNull(patches);
        Iterator<Patch> it = patches.iterator();
        assertTrue(it.hasNext());
        Patch patch = it.next();
        assertNotNull(patch);
        assertEquals("patch-1.4.0", patch.getPatchData().getId());
        assertNotNull(patch.getPatchData().getBundles());
        assertEquals(1, patch.getPatchData().getBundles().size());
        Iterator<String> itb = patch.getPatchData().getBundles().iterator();
        assertEquals("mvn:foo/my-bsn/1.4.0", itb.next());
        assertNull(patch.getResult());

        //
        // Simulate the patch
        //

        when(sysBundleContext.getBundles()).thenReturn(new Bundle[] { bundle });
        when(sysBundleContext.getServiceReference("io.fabric8.api.FabricService")).thenReturn(null);
        when(bundle.getSymbolicName()).thenReturn("my-bsn");
        when(bundle.getVersion()).thenReturn(new Version("1.3.1"));
        when(bundle.getLocation()).thenReturn("location");
        when(bundle.getBundleId()).thenReturn(123L);
        BundleStartLevel bsl = mock(BundleStartLevel.class);
        when(bsl.getStartLevel()).thenReturn(30);
        when(bundle.adapt(BundleStartLevel.class)).thenReturn(bsl);
        when(bundle.getState()).thenReturn(1);
        when(sysBundleContext.getProperty("karaf.default.repository")).thenReturn("system");

        PatchResult result = service.install(patch, true);
        assertNotNull(result);
        assertEquals(1, result.getBundleUpdates().size());
        assertTrue(result.isSimulation());
    }

    @Test
    public void testVersionHistory() {
        // the same bundle has been patched twice
        Patch patch1 = new Patch(new PatchData("patch1", "First patch", null, null, null, null, null), null);
        patch1.setResult(new PatchResult(patch1.getPatchData(), true, System.currentTimeMillis(), new LinkedList<org.jboss.fuse.patch.management.BundleUpdate>(), null, null));
        patch1.getResult().getBundleUpdates().add(new BundleUpdate("my-bsn", "1.1.0", "mvn:groupId/my-bsn/1.1.0",
                "1.0.0", "mvn:groupId/my-bsn/1.0.0"));
        Patch patch2 = new Patch(new PatchData("patch2", "Second patch", null, null, null, null, null), null);
        patch2.setResult(new PatchResult(patch1.getPatchData(), true, System.currentTimeMillis(), new LinkedList<org.jboss.fuse.patch.management.BundleUpdate>(), null, null));
        patch2.getResult().getBundleUpdates().add(new BundleUpdate("my-bsn;directive1=true", "1.2.0", "mvn:groupId/my-bsn/1.2.0",
                "1.1.0", "mvn:groupId/my-bsn/1.1.0"));
        Map<String, Patch> patches = new HashMap<String, Patch>();
        patches.put("patch1", patch1);
        patches.put("patch2", patch2);

        // the version history should return the correct URL, even when bundle.getLocation() does not
        PatchServiceImpl.BundleVersionHistory history = new PatchServiceImpl.BundleVersionHistory(patches);
        assertEquals("Should return version from patch result instead of the original location",
                "mvn:groupId/my-bsn/1.2.0",
                history.getLocation(createMockBundle("my-bsn", "1.2.0", "mvn:groupId/my-bsn/1.0.0")));
        assertEquals("Should return version from patch result instead of the original location",
                "mvn:groupId/my-bsn/1.1.0",
                history.getLocation(createMockBundle("my-bsn", "1.1.0", "mvn:groupId/my-bsn/1.0.0")));
        assertEquals("Should return original bundle location if no maching version is found in the history",
                "mvn:groupId/my-bsn/1.0.0",
                history.getLocation(createMockBundle("my-bsn", "1.0.0", "mvn:groupId/my-bsn/1.0.0")));
        assertEquals("Should return original bundle location if no maching version is found in the history",
                "mvn:groupId/my-bsn/0.9.0",
                history.getLocation(createMockBundle("my-bsn", "0.9.0", "mvn:groupId/my-bsn/0.9.0")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void bundleUpdatesInPatch() throws Exception {
        BundleContext context = mock(BundleContext.class);

        Bundle bundle0 = mock(Bundle.class);
        when(bundle0.getBundleContext()).thenReturn(context);

        when(context.getProperty("karaf.home")).thenReturn("target/bundleUpdatesInPatch");
        when(context.getProperty("karaf.base")).thenReturn("target/bundleUpdatesInPatch");
        when(context.getProperty("karaf.data")).thenReturn("target/bundleUpdatesInPatch/data");
        when(context.getProperty("karaf.etc")).thenReturn("target/bundleUpdatesInPatch/etc");
        when(context.getProperty("karaf.name")).thenReturn("root");
        when(context.getProperty("karaf.instances")).thenReturn("instances");
        when(context.getProperty("karaf.default.repository")).thenReturn("system");
        when(context.getProperty("fuse.patch.location")).thenReturn(null);
        when(context.getBundle(0)).thenReturn(bundle0);

        PatchServiceImpl service = new PatchServiceImpl();
        Method m = service.getClass().getDeclaredMethod("bundleUpdatesInPatch",
                Patch.class, Bundle[].class, Map.class, PatchServiceImpl.BundleVersionHistory.class,
                Map.class, PatchKind.class, Map.class, List.class);
        m.setAccessible(true);

        Field f = service.getClass().getDeclaredField("helper");
        f.setAccessible(true);
        f.set(service, new OSGiPatchHelper(new File("target/bundleUpdatesInPatch"), context) {
            @Override
            public String[] getBundleIdentity(String url) throws IOException {
                Artifact a = Utils.mvnurlToArtifact(url, false);
                return a == null ? null : new String[] { a.getArtifactId(), a.getVersion() };
            }
        });

        PatchData pd = new PatchData("patch-x");
        // for these two, bundle.getLocation() will return matching location
        pd.getBundles().add("mvn:io.fabric8/pax-romana/1.0.1");
        pd.getBundles().add("mvn:io.fabric8/pax-hellenica/1.0.1/jar");
        // for these two, bundle.getLocation() will return non-matching location
        pd.getBundles().add("mvn:io.fabric8/pax-bohemia/1.0.1");
        pd.getBundles().add("mvn:io.fabric8/pax-pomerania/1.0.1/jar");
        // for these two, bundle.getLocation() will return matching location
        pd.getBundles().add("mvn:io.fabric8/pax-avaria/1.0.1/jar/uber");
        pd.getBundles().add("mvn:io.fabric8/pax-mazovia/1.0.1//uber");
        // for these two, bundle.getLocation() will return non-matching location
        pd.getBundles().add("mvn:io.fabric8/pax-novgorod/1.0.1/jar/uber");
        pd.getBundles().add("mvn:io.fabric8/pax-castile/1.0.1//uber");

        f = pd.getClass().getDeclaredField("versionRanges");
        f.setAccessible(true);
        f.set(pd, new HashMap<>());

        Patch patch = new Patch(pd, null);

        Bundle[] bundles = new Bundle[8];
        bundles[0] = bundle("mvn:io.fabric8/pax-romana/1.0.0");
        bundles[1] = bundle("mvn:io.fabric8/pax-hellenica/1.0.0/jar");
        bundles[2] = bundle("mvn:io.fabric8/pax-bohemia/1.0.0/jar");
        bundles[3] = bundle("mvn:io.fabric8/pax-pomerania/1.0.0");
        bundles[4] = bundle("mvn:io.fabric8/pax-avaria/1.0.0/jar/uber");
        bundles[5] = bundle("mvn:io.fabric8/pax-mazovia/1.0.0//uber");
        bundles[6] = bundle("mvn:io.fabric8/pax-novgorod/1.0.0//uber");
        bundles[7] = bundle("mvn:io.fabric8/pax-castile/1.0.0/jar/uber");

        Object _list = m.invoke(service,
                patch, bundles, new HashMap<>(), new PatchServiceImpl.BundleVersionHistory(new HashMap<String, Patch>()),
                new HashMap<>(), PatchKind.NON_ROLLUP, new HashMap<>(), null);
        List<BundleUpdate> list = (List<BundleUpdate>) _list;

        assertThat(list.size(), equalTo(8));
        assertThat(list.get(0).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-romana/1.0.0"));
        assertThat(list.get(1).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-hellenica/1.0.0/jar"));
        assertThat(list.get(2).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-bohemia/1.0.0/jar"));
        assertThat(list.get(3).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-pomerania/1.0.0"));
        assertThat(list.get(4).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-avaria/1.0.0/jar/uber"));
        assertThat(list.get(5).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-mazovia/1.0.0//uber"));
        assertThat(list.get(6).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-novgorod/1.0.0//uber"));
        assertThat(list.get(7).getPreviousLocation(), equalTo("mvn:io.fabric8/pax-castile/1.0.0/jar/uber"));
        assertThat(list.get(0).getNewLocation(), equalTo("mvn:io.fabric8/pax-romana/1.0.1"));
        assertThat(list.get(1).getNewLocation(), equalTo("mvn:io.fabric8/pax-hellenica/1.0.1/jar"));
        assertThat(list.get(2).getNewLocation(), equalTo("mvn:io.fabric8/pax-bohemia/1.0.1"));
        assertThat(list.get(3).getNewLocation(), equalTo("mvn:io.fabric8/pax-pomerania/1.0.1/jar"));
        assertThat(list.get(4).getNewLocation(), equalTo("mvn:io.fabric8/pax-avaria/1.0.1/jar/uber"));
        assertThat(list.get(5).getNewLocation(), equalTo("mvn:io.fabric8/pax-mazovia/1.0.1//uber"));
        assertThat(list.get(6).getNewLocation(), equalTo("mvn:io.fabric8/pax-novgorod/1.0.1/jar/uber"));
        assertThat(list.get(7).getNewLocation(), equalTo("mvn:io.fabric8/pax-castile/1.0.1//uber"));

        // ---

        Repository repository = mock(Repository.class);
        File tmp = new File("target/bundleUpdatesInPatch/" + UUID.randomUUID().toString());
        tmp.mkdirs();
        File startupProperties = new File(tmp, "etc/startup.properties");
        FileUtils.copyFile(new File("src/test/resources/uber-startup.properties"), startupProperties);
        when(repository.getWorkTree()).thenReturn(tmp);
        Git fork = mock(Git.class);
        when(fork.getRepository()).thenReturn(repository);

        GitPatchManagementServiceImpl gitPatchManagementService = new GitPatchManagementServiceImpl(context);

        m = gitPatchManagementService.getClass().getDeclaredMethod("updateFileReferences",
                Git.class, PatchData.class, List.class);
        m.setAccessible(true);
        m.invoke(gitPatchManagementService, fork, pd, list);

        try (FileReader reader = new FileReader(startupProperties)) {
            Properties startup = new Properties();
            startup.load(reader);
            assertTrue(startup.containsKey("io/fabric8/pax-romana/1.0.1/pax-romana-1.0.1.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-hellenica/1.0.1/pax-hellenica-1.0.1.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-bohemia/1.0.1/pax-bohemia-1.0.1.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-pomerania/1.0.1/pax-pomerania-1.0.1.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-avaria/1.0.1/pax-avaria-1.0.1-uber.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-mazovia/1.0.1/pax-mazovia-1.0.1-uber.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-novgorod/1.0.1/pax-novgorod-1.0.1-uber.jar"));
            assertTrue(startup.containsKey("io/fabric8/pax-castile/1.0.1/pax-castile-1.0.1-uber.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-romana/1.0.0/pax-romana-1.0.0.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-hellenica/1.0.0/pax-hellenica-1.0.0.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-bohemia/1.0.0/pax-bohemia-1.0.0.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-pomerania/1.0.0/pax-pomerania-1.0.0.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-avaria/1.0.0/pax-avaria-1.0.0-uber.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-mazovia/1.0.0/pax-mazovia-1.0.0-uber.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-novgorod/1.0.0/pax-novgorod-1.0.0-uber.jar"));
            assertFalse(startup.containsKey("io/fabric8/pax-castile/1.0.0/pax-castile-1.0.0-uber.jar"));
        }
    }

    /**
     * Helper method for {@link #bundleUpdatesInPatch()} test
     * @param location
     * @return
     */
    private Bundle bundle(String location) {
        BundleStartLevel bsl = mock(BundleStartLevel.class);
        when(bsl.getStartLevel()).thenReturn(42);

        Bundle b = mock(Bundle.class);
        Artifact a = Utils.mvnurlToArtifact(location, false);
        when(b.getSymbolicName()).thenReturn(a == null ? null : a.getArtifactId());
        when(b.getVersion()).thenReturn(a == null ? null : new Version(a.getVersion()));
        when(b.getLocation()).thenReturn(location);
        when(b.adapt(BundleStartLevel.class)).thenReturn(bsl);
        when(b.getState()).thenReturn(Bundle.ACTIVE);

        return b;
    }

    private Bundle createMockBundle(String bsn, String version, String location) {
        Bundle result = mock(Bundle.class);
        when(result.getSymbolicName()).thenReturn(bsn);
        when(result.getVersion()).thenReturn(Version.parseVersion(version));
        when(result.getLocation()).thenReturn(location);
        return result;
    }

    private void generateData() throws Exception {
        karaf = new File(baseDir, "karaf");
        PatchTestSupport.delete(karaf);
        karaf.mkdirs();
        new File(karaf, "etc").mkdir();
        new File(karaf, "etc/startup.properties").createNewFile();
        System.setProperty("karaf.base", karaf.getAbsolutePath());
        System.setProperty("karaf.home", karaf.getAbsolutePath());
        System.setProperty("karaf.name", "root");

        storage = new File(baseDir, "storage");
        PatchTestSupport.delete(storage);
        storage.mkdirs();

        bundlev131 = createBundle("my-bsn", "1.3.1");
        bundlev132 = createBundle("my-bsn;directive1:=true; directve2:=1000", "1.3.2");
        bundlev140 = createBundle("my-bsn", "1.4.0");
        bundlev200 = createBundle("my-bsn", "2.0.0");

        patch132 = createPatch("patch-1.3.2", bundlev132, "mvn:foo/my-bsn/1.3.2");
        patch140 = createPatch("patch-1.4.0", bundlev140, "mvn:foo/my-bsn/1.4.0", "[1.3.0,1.5.0)");
        patch200 = createPatch("patch-2.0.0", bundlev140, "mvn:foo/my-bsn/2.0.0");

        createPatch("patch-with-prereq2", bundlev132, "mvn:foo/my-bsn/1.3.2", null, "prereq2");
    }

    private File createPatch(String id, File bundle, String mvnUrl) throws Exception {
        return createPatch(id, bundle, mvnUrl, null);
    }

    private File createPatch(String id, File bundle, String mvnUrl, String range) throws Exception {
        return createPatch(id, bundle, mvnUrl, range, null);
    }

    private File createPatch(String id, File bundle, String mvnUrl, String range, String requirement) throws Exception {
        File patchFile = new File(storage, "temp/" + id + ".zip");
        File pd = new File(storage, "temp/" + id + "/" + id + ".patch");
        pd.getParentFile().mkdirs();
        Properties props = new Properties();
        props.put("id", id);
        props.put("bundle.count", "1");
        props.put("bundle.0", mvnUrl);
        if (range != null) {
            props.put("bundle.0.range", range);
        }
        if (requirement != null) {
            props.put("requirement.count", "1");
            props.put("requirement.O", requirement);
        }
        FileOutputStream fos = new FileOutputStream(pd);
        props.store(fos, null);
        fos.close();
        Artifact a = Utils.mvnurlToArtifact(mvnUrl, true);
        File bf = new File(storage, "temp/" + id + "/repository/" + (a == null ? "" : a.getPath()));
        bf.getParentFile().mkdirs();
        IOUtils.copy(new FileInputStream(bundle), new FileOutputStream(bf));
        fos = new FileOutputStream(patchFile);
        jarDir(pd.getParentFile(), fos);
        fos.close();
        return patchFile;
    }

    private File createBundle(String bundleSymbolicName, String version) throws Exception {
        File jar = new File(storage, "temp/" + stripSymbolicName(bundleSymbolicName) + "-" + version + ".jar");
        File man = new File(storage, "temp/" + stripSymbolicName(bundleSymbolicName) + "-" + version + "/META-INF/MANIFEST.MF");
        man.getParentFile().mkdirs();
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue("Bundle-ManifestVersion", "2");
        mf.getMainAttributes().putValue("Bundle-SymbolicName", bundleSymbolicName);
        mf.getMainAttributes().putValue("Bundle-Version", version);
        FileOutputStream fos = new FileOutputStream(man);
        mf.write(fos);
        fos.close();
        fos = new FileOutputStream(jar);
        jarDir(man.getParentFile().getParentFile(), fos);
        fos.close();
        return jar;
    }

    @SafeVarargs
    private final <T> Set<T> asSet(T... objects) {
        HashSet<T> set = new HashSet<T>();
        Collections.addAll(set, objects);
        return set;
    }

    private URL getZippedTestDir(String name) throws IOException {
        File f2 = new File(baseDir, name + ".jar");
        OutputStream os = new FileOutputStream(f2);
        jarDir(new File(baseDir, name), os);
        os.close();
        return f2.toURI().toURL();
    }

    public static void jarDir(File directory, OutputStream os) throws IOException {
        // create a ZipOutputStream to zip the data to
        JarOutputStream zos = new JarOutputStream(os);
        zos.setLevel(Deflater.NO_COMPRESSION);
        String path = "";
        File manFile = new File(directory, JarFile.MANIFEST_NAME);
        if (manFile.exists()) {
            byte[] readBuffer = new byte[8192];
            try (FileInputStream fis = new FileInputStream(manFile)) {
                ZipEntry anEntry = new ZipEntry(JarFile.MANIFEST_NAME);
                zos.putNextEntry(anEntry);
                int bytesIn = fis.read(readBuffer);
                while (bytesIn != -1) {
                    zos.write(readBuffer, 0, bytesIn);
                    bytesIn = fis.read(readBuffer);
                }
            }
            zos.closeEntry();
        }
        zipDir(directory, zos, path, Collections.singleton(JarFile.MANIFEST_NAME));
        // close the stream
        zos.close();
    }

    public static void zipDir(File directory, ZipOutputStream zos, String path, Set/* <String> */ exclusions) throws IOException {
        // get a listing of the directory content
        File[] dirList = directory.listFiles();
        byte[] readBuffer = new byte[8192];
        int bytesIn = 0;
        // loop through dirList, and zip the files
        if (dirList != null) {
            for (File f : dirList) {
                if (f.isDirectory()) {
                    String prefix = path + f.getName() + "/";
                    zos.putNextEntry(new ZipEntry(prefix));
                    zipDir(f, zos, prefix, exclusions);
                    continue;
                }
                String entry = path + f.getName();
                if (!exclusions.contains(entry)) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        ZipEntry anEntry = new ZipEntry(entry);
                        zos.putNextEntry(anEntry);
                        bytesIn = fis.read(readBuffer);
                        while (bytesIn != -1) {
                            zos.write(readBuffer, 0, bytesIn);
                            bytesIn = fis.read(readBuffer);
                        }
                    }
                }
            }
        }
    }

    public class CustomBundleURLStreamHandlerFactory implements
            URLStreamHandlerFactory {

        private static final String MVN_URI_PREFIX = "mvn";

        public URLStreamHandler createURLStreamHandler(String protocol) {
            if (protocol.equals(MVN_URI_PREFIX)) {
                return new MvnHandler();
            } else {
                return null;
            }
        }
    }

    public class MvnHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            if (u.toString().equals("mvn:foo/my-bsn/1.3.1")) {
                return bundlev131.toURI().toURL().openConnection();
            }
            if (u.toString().equals("mvn:foo/my-bsn/1.3.2")) {
                return bundlev132.toURI().toURL().openConnection();
            }
            if (u.toString().equals("mvn:foo/my-bsn/1.4.0")) {
                return bundlev140.toURI().toURL().openConnection();
            }
            if (u.toString().equals("mvn:foo/my-bsn/2.0.0")) {
                return bundlev200.toURI().toURL().openConnection();
            }
            throw new IllegalArgumentException(u.toString());
        }
    }

}

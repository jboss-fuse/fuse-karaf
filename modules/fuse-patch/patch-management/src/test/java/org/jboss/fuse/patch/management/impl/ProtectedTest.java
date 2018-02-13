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
package org.jboss.fuse.patch.management.impl;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.fuse.patch.management.BundleUpdate;
import org.jboss.fuse.patch.management.Utils;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtectedTest {

    private File gitDir;
    private Git git = null;
    private BundleContext context;

    @Before
    public void init() throws IOException, GitAPIException {
        gitDir = new File("target/ProtectedTest");
        Utils.mkdirs(gitDir);
        FileUtils.deleteDirectory(gitDir);
        git = Git.init().setDirectory(this.gitDir).setGitDir(new File(this.gitDir, ".git")).call();
        git.commit()
                .setMessage("init")
                .setAuthor("me", "my@email").call();

        context = mock(BundleContext.class);
        Bundle b0 = mock(Bundle.class);
        when(context.getBundle(0)).thenReturn(b0);
        when(b0.getBundleContext()).thenReturn(context);
        when(context.getProperty("karaf.home")).thenReturn("target/ProtectedTest-karaf");
        when(context.getProperty("karaf.base")).thenReturn("target/ProtectedTest-karaf");
        when(context.getProperty("karaf.data")).thenReturn("target/ProtectedTest-karaf/data");
    }

    @Test
    public void updateBinInstanceReferences() throws IOException {
        File binInstance = new File(git.getRepository().getWorkTree(), "bin/instance");
        FileUtils.copyFile(new File("src/test/resources/files/bin/instance"), binInstance);
        List<BundleUpdate> bundleUpdates = new LinkedList<>();
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.karaf.instance/org.apache.karaf.instance.core/4.2.0.redhat-700001")
                .to("mvn:org.apache.karaf.instance/org.apache.karaf.instance.core/4.2.0.redhat-700002"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.karaf.shell/org.apache.karaf.shell.core/4.2.0.redhat-700001")
                .to("mvn:org.apache.karaf.shell/org.apache.karaf.shell.core/4.2.0.redhat-700002"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.jline/jline/3.5.2")
                .to("mvn:org.jline/jline/3.5.3"));

        Map<String, String> updates = Utils.collectLocationUpdates(bundleUpdates);
        new GitPatchManagementServiceImpl(context).updateReferences(git, "bin/instance", "system/", updates, false);

        String expected = FileUtils.readFileToString(new File("src/test/resources/files/bin/instance.updated"), "UTF-8");
        String changed = FileUtils.readFileToString(binInstance, "UTF-8");
        assertThat(changed, equalTo(expected));
    }

    @Test
    public void updateBinInstanceBatReferences() throws IOException {
        File binInstance = new File(git.getRepository().getWorkTree(), "bin/instance.bat");
        FileUtils.copyFile(new File("src/test/resources/files/bin/instance.bat"), binInstance);
        List<BundleUpdate> bundleUpdates = new LinkedList<>();
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.karaf.instance/org.apache.karaf.instance.core/4.2.0.redhat-700001")
                .to("mvn:org.apache.karaf.instance/org.apache.karaf.instance.core/4.2.0.redhat-700002"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.karaf.shell/org.apache.karaf.shell.core/4.2.0.redhat-700001")
                .to("mvn:org.apache.karaf.shell/org.apache.karaf.shell.core/4.2.0.redhat-700002"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.jline/jline/3.5.2")
                .to("mvn:org.jline/jline/3.5.3"));

        Map<String, String> updates = Utils.collectLocationUpdates(bundleUpdates);
        new GitPatchManagementServiceImpl(context).updateReferences(git, "bin/instance.bat", "system/", updates, true);

        String expected = FileUtils.readFileToString(new File("src/test/resources/files/bin/instance.bat.updated"), "UTF-8");
        expected = expected.replaceAll("\\r\\n", "\n");
        String changed = FileUtils.readFileToString(binInstance, "UTF-8");
        changed = changed.replaceAll("\\r\\n", "\n");
        assertThat(changed, equalTo(expected));
    }

    @Test
    public void updateEtcConfigReferences() throws IOException {
        File etcConfig = new File(git.getRepository().getWorkTree(), "etc/config.properties");
        FileUtils.copyFile(new File("src/test/resources/files/etc/config.properties"), etcConfig);
        List<BundleUpdate> bundleUpdates = new LinkedList<>();
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.eclipse.platform/org.eclipse.osgi/3.12.50")
                .to("mvn:org.eclipse.platform/org.eclipse.osgi/3.12.51"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.felix/org.apache.felix.framework/5.6.10")
                .to("mvn:org.apache.felix/org.apache.felix.framework/5.6.11"));

        Map<String, String> updates = Utils.collectLocationUpdates(bundleUpdates);
        new GitPatchManagementServiceImpl(context).updateReferences(git, "etc/config.properties", "", updates, false);

        String expected = FileUtils.readFileToString(new File("src/test/resources/files/etc/config.properties.updated"), "UTF-8");
        String changed = FileUtils.readFileToString(etcConfig, "UTF-8");
        assertThat(changed, equalTo(expected));
    }

    @Test
    public void updateEtcStartupReferences() throws IOException {
        File etcConfig = new File(git.getRepository().getWorkTree(), "etc/startup.properties");
        FileUtils.copyFile(new File("src/test/resources/files/etc/startup.properties"), etcConfig);
        List<BundleUpdate> bundleUpdates = new LinkedList<>();
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.fusesource.jansi/jansi/1.16")
                .to("mvn:org.fusesource.jansi/jansi/1.17"));
        bundleUpdates.add(BundleUpdate
                .from("mvn:org.apache.felix/org.apache.felix.configadmin/1.8.16")
                .to("mvn:org.apache.felix/org.apache.felix.configadmin/1.8.17"));

        Map<String, String> updates = Utils.collectLocationUpdates(bundleUpdates);
        new GitPatchManagementServiceImpl(context).updateReferences(git, "etc/startup.properties", "", updates, false);

        String expected = FileUtils.readFileToString(new File("src/test/resources/files/etc/startup.properties.updated"), "UTF-8");
        String changed = FileUtils.readFileToString(etcConfig, "UTF-8");
        assertThat(changed, equalTo(expected));
    }

}

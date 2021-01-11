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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.commands.model.FuseVersion;
import org.jboss.fuse.patch.management.Patch;
import org.ops4j.pax.url.mvn.MavenResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Command(scope = "patch", name = "find", description = "Find patches available in Maven repositories")
public class FindCommand extends PatchCommandSupport {

    public static final Logger LOG = LoggerFactory.getLogger(FindCommand.class);

    @Reference
    protected MavenResolver resolver;

    @Reference
    protected Session session;

    @Option(name = "--add", description = "Automatically add a patch using patch:add command")
    boolean add = false;

    @Override
    protected void doExecute(PatchService service) throws Exception {
        // we just use pax-url-aether with org.ops4j.pax.url.mvn PID configuration, so we don't have to launch
        // entire Maven machiner (for now)

        // resolveMetadata() creates temporary file at Files.createTempFile("mvn-", ".tmp"), so we have to remember
        // to delete it
        File metadataFile = null;
        try {
            metadataFile = resolver.resolveMetadata("org.jboss.redhat-fuse", "fuse-karaf-patch-repository", "maven-metadata.xml", "");
            if (metadataFile == null) {
                System.out.println("No patch available in any remote repository (see `maven:repository-list` for a list of configured repositories)");
                return;
            }

            Properties props = new Properties();
            try (FileReader reader = new FileReader(new File(new File(System.getProperty("karaf.etc")), "version.properties"))) {
                props.load(reader);
            } catch (FileNotFoundException e) {
                LOG.error("Can't find etc/version.properties file!", e);
                return;
            }

            // Having maven-metadata.xml we have to parse it like in org.jboss.redhat-fuse:patch-maven-plugin's
            // org.jboss.fuse.mvnplugins.patch.SecureDependencyManagement#findLatestMetadataVersion()
            FuseVersion bomVersion = new FuseVersion(props.getProperty("version"));

            Map<String, Versioning> metadata = new TreeMap<>();
            try (FileReader reader = new FileReader(metadataFile)) {
                Metadata md = new MetadataXpp3Reader().read(reader);
                Versioning v = md.getVersioning();
                if (v != null) {
                    // we don't care about /metadata/versioning/release, because it may be for newly deployed
                    // metadata for older version of Fuse
                    metadata.put(v.getLastUpdated(), v);
                }
            } catch (IOException | XmlPullParserException e) {
                LOG.warn("Problem parsing Maven Metadata {}: {}", metadataFile, e.getMessage(), e);
            }

            Set<ComparableVersion> versions = new TreeSet<>(Comparator.reverseOrder());
            // iterate from oldest to newest metadata, where newer overwrite older versions
            for (Versioning versioning : metadata.values()) {
                for (String version : versioning.getVersions()) {
                    FuseVersion metadataVersion = new FuseVersion(version);
                    if (bomVersion.getMajor() != metadataVersion.getMajor()
                            || bomVersion.getMinor() != metadataVersion.getMinor()) {
                        LOG.info("Skipping metadata {}", version);
                        continue;
                    }

                    LOG.info("Found metadata {}", version);
                    versions.add(new ComparableVersion(version));
                }
            }

            String latestPatchRepositoryVersion = versions.size() == 0 ? null : versions.iterator().next().toString();
            if (latestPatchRepositoryVersion == null) {
                System.out.println("Can't discover latest remote patch version in remote Maven metadata." +
                        " Please check log file for more details.");
                return;
            }

            String url = String.format("mvn:org.jboss.redhat-fuse/fuse-karaf-patch-repository/%s/zip", latestPatchRepositoryVersion);

            System.out.println("Found new remote patch at " + url);

            if (!add) {
                System.out.println("You can add the patch using \"patch:add " + url + "\" command, or simply use \"patch:find --add\" option.");
            } else {
                System.out.println("Invoking \"patch:add " + url + "\" command");
                System.out.println();
                Iterable<Patch> patches = service.download(new URL(url));
                display(service.getPatches(), false);

                // maybe the added patch contained new patching mechanism?
                System.out.println();
                session.execute("patch:update --simulation --verbose");
            }
        } finally {
            if (metadataFile != null) {
                metadataFile.delete();
            }
        }
    }

}

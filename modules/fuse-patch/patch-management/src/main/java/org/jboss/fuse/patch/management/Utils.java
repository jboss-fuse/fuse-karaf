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
package org.jboss.fuse.patch.management;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.apache.commons.io.FileUtils;
import org.apache.felix.utils.version.VersionCleaner;
import org.jboss.fuse.patch.management.impl.Activator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;

public class Utils {

    private static final Pattern SYMBOLIC_NAME_PATTERN = Pattern.compile("([^;: ]+)(.*)");
    private static final Pattern KARAF_PACKAGE_VERSION = Pattern.compile(".+;version=\"([^\"]+)\"");

    private Utils() {
    }

    /**
     * Converts numeric UNIX permissions to a set of {@link PosixFilePermission}
     * @param file
     * @param unixMode
     * @return
     */
    public static Set<PosixFilePermission> getPermissionsFromUnixMode(File file, int unixMode) {
        String numeric = Integer.toOctalString(unixMode);
        if (numeric.length() > 3) {
            numeric = numeric.substring(numeric.length() - 3);
        }
        if (unixMode == 0) {
            return PosixFilePermissions.fromString(file.isDirectory() ? "rwxrwxr-x" : "rw-rw-r--");
        }

        Set<PosixFilePermission> result = new HashSet<>();
        int shortMode = Integer.parseInt(numeric, 8);
        if ((shortMode & 0400) == 0400)
            result.add(PosixFilePermission.OWNER_READ);
//        if ((shortMode & 0200) == 0200)
        // it was tricky ;)
        result.add(PosixFilePermission.OWNER_WRITE);
        if ((shortMode & 0100) == 0100)
            result.add(PosixFilePermission.OWNER_EXECUTE);
        if ((shortMode & 0040) == 0040)
            result.add(PosixFilePermission.GROUP_READ);
        if ((shortMode & 0020) == 0020)
            result.add(PosixFilePermission.GROUP_WRITE);
        if ((shortMode & 0010) == 0010)
            result.add(PosixFilePermission.GROUP_EXECUTE);
        if ((shortMode & 0004) == 0004)
            result.add(PosixFilePermission.OTHERS_READ);
        if ((shortMode & 0002) == 0002)
            result.add(PosixFilePermission.OTHERS_WRITE);
        if ((shortMode & 0001) == 0001)
            result.add(PosixFilePermission.OTHERS_EXECUTE);

        return result;
    }

    /**
     * Converts a set of {@link PosixFilePermission} to numeric UNIX permissions
     * @param permissions
     * @return
     */
    public static int getUnixModeFromPermissions(File file, Set<PosixFilePermission> permissions) {
        if (permissions == null) {
            return file.isDirectory() ? 0775 : 0664;
        } else {
            int result = 00;
            if (permissions.contains(PosixFilePermission.OWNER_READ)) {
                result |= 0400;
            }
            if (permissions.contains(PosixFilePermission.OWNER_WRITE)) {
                result |= 0200;
            }
            if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
                result |= 0100;
            }
            if (permissions.contains(PosixFilePermission.GROUP_READ)) {
                result |= 0040;
            }
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)) {
                result |= 0020;
            }
            if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
                result |= 0010;
            }
            if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
                result |= 0004;
            }
            if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                result |= 0002;
            }
            if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                result |= 0001;
            }
            return result;
        }
    }

    /**
     * Retrieves location of fileinstall-managed "deploy" directory, where bundles can be dropped
     * @return
     */
    public static File getDeployDir(File karafHome) throws IOException {
        String deployDir = null;
        File fileinstallCfg = new File(System.getProperty("karaf.etc"), "org.apache.felix.fileinstall-deploy.cfg");
        if (fileinstallCfg.exists() && fileinstallCfg.isFile()) {
            Properties props = new Properties();
            FileInputStream stream = new FileInputStream(fileinstallCfg);
            props.load(stream);
            deployDir = props.getProperty("felix.fileinstall.dir");
            if (deployDir.contains("${karaf.home}")) {
                deployDir = deployDir.replace("${karaf.home}", System.getProperty("karaf.home"));
            } else if (deployDir.contains("${karaf.base}")) {
                deployDir = deployDir.replace("${karaf.base}", System.getProperty("karaf.base"));
            }
            closeQuietly(stream);
        } else {
            deployDir = karafHome.getAbsolutePath() + "/deploy";
        }
        return new File(deployDir);
    }

    /**
     * Returns location of system repository - by default <code>${karaf.home}/system</code>.
     * @param karafHome
     * @param systemContext
     * @return
     */
    public static File getSystemRepository(File karafHome, BundleContext systemContext) {
        return new File(karafHome, systemContext.getProperty("karaf.default.repository"));
    }

    /**
     * Finds relative path between two files
     * @param f1
     * @param f2
     * @return
     */
    public static String relative(File f1, File f2) {
        Path p1 = f1.toPath();
        Path p2 = f2.toPath();
        return p1.relativize(p2).toString();
    }

    /**
     * Converts file paths relative to <code>${karaf.default.repository}</code> to <code>mvn:</code> URIs
     * @param path
     * @return
     */
    public static String pathToMvnurl(String path) {
        String[] p = path.split("/");
        if (p.length >= 4 && p[p.length-1].startsWith(p[p.length-3] + "-" + p[p.length-2])) {
            String artifactId = p[p.length-3];
            String version = p[p.length-2];
            String classifier;
            String type;
            String artifactIdVersion = artifactId + "-" + version;
            StringBuilder sb = new StringBuilder();
            if (p[p.length-1].charAt(artifactIdVersion.length()) == '-') {
                classifier = p[p.length-1].substring(artifactIdVersion.length() + 1, p[p.length-1].lastIndexOf('.'));
            } else {
                classifier = null;
            }
            type = p[p.length-1].substring(p[p.length-1].lastIndexOf('.') + 1);
            sb.append("mvn:");
            for (int j = 0; j < p.length - 3; j++) {
                if (j > 0) {
                    sb.append('.');
                }
                sb.append(p[j]);
            }
            sb.append('/').append(artifactId).append('/').append(version);
            if (!"jar".equals(type) || classifier != null) {
                sb.append('/');
                if (!"jar".equals(type)) {
                    sb.append(type);
                } else if (classifier != null) {
                    sb.append(type);
                }
                if (classifier != null) {
                    sb.append('/').append(classifier);
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Converts <code>mvn:</code> URIs to file paths relative to <code>${karaf.default.repository}</code>
     * @param url
     */
    public static String mvnurlToPath(String url) {
        Artifact artifact = Utils.mvnurlToArtifact(url, true);
        if (artifact == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String[] group = artifact.getGroupId().split("\\.");
        for (String g : group) {
            sb.append('/').append(g);
        }
        sb.append('/').append(artifact.getArtifactId());
        sb.append('/').append(artifact.getVersion());
        sb.append('/').append(artifact.getArtifactId()).append("-").append(artifact.getVersion());
        if (artifact.getClassifier() != null) {
            sb.append('-').append(artifact.getClassifier());
        }
        sb.append('.').append(artifact.getType() == null || "".equals(artifact.getType().trim()) ? "jar" : artifact.getType());

        return sb.toString().substring(1);
    }

    public static Artifact mvnurlToArtifact(String resourceLocation, boolean skipNonMavenProtocols) {
        resourceLocation = resourceLocation.replace("\r\n", "").replace("\n", "").replace(" ", "").replace("\t", "");
        final int index = resourceLocation.indexOf("mvn:");
        if (index < 0) {
            if (skipNonMavenProtocols) {
                return null;
            }
            throw new IllegalArgumentException("Resource URL is not a maven URL: " + resourceLocation);
        } else {
            resourceLocation = resourceLocation.substring(index + "mvn:".length());
        }
        // Truncate the URL when a '#', a '?' or a '$' is encountered
        final int index1 = resourceLocation.indexOf('?');
        final int index2 = resourceLocation.indexOf('#');
        int endIndex = -1;
        if (index1 > 0) {
            if (index2 > 0) {
                endIndex = Math.min(index1, index2);
            } else {
                endIndex = index1;
            }
        } else if (index2 > 0) {
            endIndex = index2;
        }
        if (endIndex >= 0) {
            resourceLocation = resourceLocation.substring(0, endIndex);
        }
        final int index3 = resourceLocation.indexOf('$');
        if (index3 > 0) {
            resourceLocation = resourceLocation.substring(0, index3);
        }

        String[] parts = resourceLocation.split("/");
        if (parts.length > 2) {
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];
            String type = "jar";
            String classifier = null;
            if (parts.length > 3) {
                type = parts[3];
                if (parts.length > 4) {
                    classifier = parts[4];
                    if ("".equals(type)) {
                        type = "jar";
                    }
                }
            }
            return new Artifact(groupId, artifactId, version, type, classifier);
        }
        throw new IllegalArgumentException("Bad maven url: " + resourceLocation);
    }


    /**
     * Strips symbolic name from directives.
     * @param symbolicName
     * @return
     */
    public static String stripSymbolicName(String symbolicName) {
        Matcher m = SYMBOLIC_NAME_PATTERN.matcher(symbolicName);
        if (m.matches() && m.groupCount() >= 1) {
            return m.group(1);
        } else {
            return symbolicName;
        }
    }

    /**
     * Feature versions may not have 4 positions. Let's make them canonical
     * @param version
     * @return
     */
    public static Version getOsgiVersion(String version) {
        if (version == null || "".equals(version.trim())) {
            return Version.emptyVersion;
        }
        return new Version(VersionCleaner.clean(version));
    }

    /**
     * Feature versions may not have 4 positions. Let's make them canonical
     * @param version
     * @return
     */
    private static Version getFeatureVersion(String version) {
        if (version == null || "".equals(version.trim())) {
            return Version.emptyVersion;
        }
        String[] vt = version.split("\\.");
        String[] nvt = new String[4];
        int[] v123 = new int[] { 0, 0, 0 };
        String v4 = null;

        // let's assume we don't parse versions like 1.3-fuse.3
        if (vt.length < 4) {
            try {
                int _v = Integer.parseInt(vt[vt.length - 1]);
                for (int i=0; i<vt.length; i++) {
                    v123[i] = Integer.parseInt(vt[i]);
                }
            } catch (NumberFormatException e) {
                for (int i=0; i<vt.length-1; i++) {
                    v123[i] = Integer.parseInt(vt[i]);
                }
                v4 = vt[vt.length - 1];
            }
        } else {
            for (int i=0; i<3; i++) {
                v123[i] = Integer.parseInt(vt[i]);
            }
            v4 = vt[vt.length - 1];
        }

        return new Version(v123[0], v123[1], v123[2], v4);
    }

    /**
     * Iterates over {@link BundleUpdate bundle updates} and returns a mapping of old filesystem location
     * to new one. All locations are relative to <code>${karaf.default.repository}</code>
     * @param bundleUpdatesInThisPatch
     * @return
     */
    public static Map<String,String> collectLocationUpdates(List<BundleUpdate> bundleUpdatesInThisPatch) {
        LinkedHashMap<String, String> locationUpdates = new LinkedHashMap<>();
        if (bundleUpdatesInThisPatch != null) {
            for (BundleUpdate update : bundleUpdatesInThisPatch) {
                if (update.getPreviousLocation() != null && update.getNewLocation() != null) {
                    String l1 = update.getPreviousLocation();
                    String l2 = update.getNewLocation();
                    if (l1.contains("org/ops4j/pax/url/pax-url-aether")) {
                        l1 = l1.substring(l1.indexOf("org/ops4j/pax/url/pax-url-aether"));
                        l2 = l2.substring(l2.indexOf("org/ops4j/pax/url/pax-url-aether"));
                        locationUpdates.put(l1, l2);
                    } else {
                        String path1 = Utils.mvnurlToPath(l1);
                        String path2 = Utils.mvnurlToPath(l2);
                        if (path1 != null && path2 != null) {
                            locationUpdates.put(path1, path2);
                        }
                        // we have to handle non-canonical mvn: URIs too, like mvn:g/a/v//uber vs. mvn:g/a/v/jar/uber
                        // or mvn:g/a/v vs. mvn:g/a/v/jar (where "jar" is not necessary)
                        mapMvnURIFlavours(locationUpdates, l1, l2);
                    }
                }
            }
        }

        return locationUpdates;
    }

    /**
     * Prepare different mappings for {@code mvn:} URIs. Initial URIs are in the form {@code mvn:g/a/v[/type[/classifier]]}
     * while {@code type}, if equal to {@code jar} may be optional
     * @param locationUpdates explicitly {@link LinkedHashMap}, so we can ensure order
     * @param uri1
     * @param uri2
     */
    private static void mapMvnURIFlavours(LinkedHashMap<String, String> locationUpdates, String uri1, String uri2) {
        Artifact a1 = Utils.mvnurlToArtifact(uri1, true);
        Artifact a2 = Utils.mvnurlToArtifact(uri2, true);
        if (a1 == null || a2 == null) {
            return;
        }

        // we need only uri2 to be canonical (e.g., not "mvn:g/a/v//c" or "mvn:g/a/v/jar")
        String curi1 = a1.getCanonicalUri();
        String curi2 = a2.getCanonicalUri();
        String curi2prop = curi2.replaceFirst("^mvn\\\\*:", "mvn\\\\:");

        if (a1.getType().equals("jar")) {
            if (a1.getClassifier() == null) {
                locationUpdates.put(curi1 + "/jar", curi2);
                locationUpdates.put((curi1 + "/jar").replaceFirst("^mvn\\\\*:", "mvn\\\\:"), curi2prop);
            } else {
                locationUpdates.put(curi1.replace("/jar/", "//"), curi2);
                locationUpdates.put((curi1.replace("/jar/", "//")).replaceFirst("^mvn\\\\*:", "mvn\\\\:"), curi2prop);
            }
        }

        // we put possible mvn:g/a/v1 -> mvn:g/a/v2 at the end, to ensure that
        // mvn:g/a/v1/jar -> mvn:g/a/v2 is added first
        locationUpdates.put(curi1, curi2);
        locationUpdates.put(curi1.replaceFirst("^mvn\\\\*:", "mvn\\\\:"), curi2prop);
    }

    /**
     * Updates version of exported karaf packages inside <code>etc/config.properties</code>
     * @param configProperties
     * @param newVersion
     * @param packages
     */
    public static void updateKarafPackageVersion(File configProperties, String newVersion, String ... packages) {
        BufferedReader reader = null;
        StringWriter sw = new StringWriter();
        try {
            reader = new BufferedReader(new FileReader(configProperties));
            String line = null;
            while ((line = reader.readLine()) != null) {
                for (String pkg : packages) {
                    Matcher matcher = KARAF_PACKAGE_VERSION.matcher(line);
                    if (line.contains(pkg + ";version=") && matcher.find()) {
                        line = line.substring(0, matcher.start(1)) +
                                newVersion +
                                line.substring(matcher.end(1));
                    }
                }
                sw.append(line).append("\n");
            }
            closeQuietly(reader);
            FileUtils.write(configProperties, sw.toString(), "UTF-8");
        } catch (Exception e) {
            Activator.log(LogService.LOG_ERROR, null, e.getMessage(), e, true);
        } finally {
            closeQuietly(reader);
        }
    }

    /**
     * Compute a checksum for the given stream
     *
     * @param is the input stream
     * @return a checksum identifying any change
     */
    public static long checksum(InputStream is) throws IOException {
        try {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[8192];
            int l;
            while ((l = is.read(buffer)) > 0) {
                crc.update(buffer, 0, l);
            }
            return crc.getValue();
        } finally {
            closeQuietly(is);
        }
    }

    /**
     * <p>Tries to extract {@link Version} from a name like <code>some-name-version.extension</code>.</p>
     * <p>This may be quite complicated, for example <code>jboss-fuse-6.1.1.redhat-459-hf26.patch</code>. In this case
     * the version should be <code>new Version(6, 1, 1, "redhat-459-hf26")</code>.</p>
     * @param name
     * @return
     */
    public static Version findVersionInName(String name) {
        Version result = Version.emptyVersion;

        String[] segments = name.split("-");
        if (segments.length < 2) {
            return result;
        }

        int possibleVersionSegment = 0;
        for (int i = 1; i < segments.length; i++) {
            if (segments[i].length() > 0 && Character.isDigit(segments[i].charAt(0))) {
                possibleVersionSegment = i;
                break;
            }
        }
        if (possibleVersionSegment > 0) {
            StringBuilder sb = null;
            for (int i = possibleVersionSegment; i < segments.length; i++) {
                if (sb == null) {
                    sb = new StringBuilder();
                } else {
                    sb.append('-');
                }
                sb.append(segments[i]);
            }
            if (sb != null) {
                try {
                    result = Version.parseVersion(sb.toString());
                } catch (IllegalArgumentException ignore) {
                }
            }
        }

        return result;
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * {@link File#mkdirs()} with actual return value checking
     * @param directory
     * @throws IOException
     */
    public static void mkdirs(File directory) throws IOException {
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Can't create directory " + directory);
        }
    }

}

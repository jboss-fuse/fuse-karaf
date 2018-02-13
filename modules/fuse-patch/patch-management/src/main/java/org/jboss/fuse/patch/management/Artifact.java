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

/**
 * Yet another implementation of Maven artifact components (groupId, artifactId, version, type, classifier)
 */
public class Artifact {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String type;
    private final String classifier;

    public Artifact(String groupId, String artifactId, String version, String type, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.classifier = classifier;
    }

    public static boolean isSameButVersion(Artifact a1, Artifact a2) {
        return a1.getGroupId().equals(a2.getGroupId())
                && a1.getArtifactId().equals(a2.getArtifactId())
                && a1.hasClassifier() == a2.hasClassifier()
                && (!a1.hasClassifier() || a1.getClassifier().equals(a2.getClassifier()))
                && a1.getType().equals(a2.getType());
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public boolean hasClassifier() {
        return classifier != null;
    }

    public String getPath() {
        return groupId.replace('.', '/')
                + '/'
                + artifactId
                + '/'
                + version
                + '/'
                + artifactId
                + (classifier != null ? "-" + classifier : "")
                + '-'
                + version
                + '.'
                + type;
    }

    public String getCanonicalUri() {
        StringBuilder sb = new StringBuilder("mvn:");
        sb.append(groupId)
                .append("/")
                .append(artifactId)
                .append("/")
                .append(version);
        if (!"jar".equals(type) || classifier != null) {
            sb.append("/").append(type);
            if (classifier != null) {
                sb.append("/").append(classifier);
            }
        }
        return sb.toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId)
                .append(":")
                .append(artifactId)
                .append(":")
                .append(version);
        if (!"jar".equals(type) || classifier != null) {
            sb.append(":").append(type);
            if (classifier != null) {
                sb.append(":").append(classifier);
            }
        }
        return sb.toString();
    }

}

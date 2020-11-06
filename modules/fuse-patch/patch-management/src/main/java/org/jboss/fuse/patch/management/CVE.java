/*
 * Copyright 2005-2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.patch.management;

/**
 * The same class is inside jboss-fuse/redhat-fuse/fuse-tooling/patch-maven-plugin, but here we use a subset
 * of original information, because the changes are installed using patch descriptor, not CVE metadata.
 */
public class CVE {

    private String id;
    private String description;
    private String cveLink;
    private String bzLink;

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCveLink() {
        return cveLink;
    }

    public void setCveLink(String cveLink) {
        this.cveLink = cveLink;
    }

    public String getBzLink() {
        return bzLink;
    }

    public void setBzLink(String bzLink) {
        this.bzLink = bzLink;
    }

    @Override
    public String toString() {
        String cveLink = this.cveLink != null && !this.cveLink.trim().equals("") ? this.cveLink : null;
        String bzLink = this.bzLink != null && !this.bzLink.trim().equals("") ? this.bzLink : null;
        if (cveLink != null && bzLink != null) {
            return id + ": " + description + " (" + cveLink + ", " + bzLink + ")";
        } else if (cveLink != null) {
            return id + ": " + description + " (" + cveLink + ")";
        } else if (bzLink != null) {
            return id + ": " + description + " (" + bzLink + ")";
        } else {
            return id + ": " + description;
        }
    }

}

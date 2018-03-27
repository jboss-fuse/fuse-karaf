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

public enum EnvType {

    /** Standalone Fuse/Karaf */
    STANDALONE("baseline-%s"),
    /** Fuse/Karaf child container (<code>instance:create</code>) */
    STANDALONE_CHILD("baseline-child-%s"),

    /** Openshift? JClouds? Other? SSH? */
    UNKNOWN(null);

    private String baselineTagFormat;

    EnvType(String baselineTagFormat) {
        this.baselineTagFormat = baselineTagFormat;
    }

    public String getBaselineTagFormat() {
        return baselineTagFormat;
    }

}

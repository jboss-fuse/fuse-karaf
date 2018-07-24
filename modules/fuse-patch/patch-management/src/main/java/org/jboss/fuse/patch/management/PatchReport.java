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

import java.util.Date;

/**
 * Report generated on finished {@link PatchResult}. It'll be cached later, so no further changes to patch result
 * should be made.
 */
public class PatchReport {

    private String id;
    private Date timestamp;
    private boolean rollup;
    private long updatedBundles;
    private long updatedFeatures;
    private long removedFeatures;
    private long overridenFeatures;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRollup() {
        return rollup;
    }

    public void setRollup(boolean rollup) {
        this.rollup = rollup;
    }

    public long getUpdatedBundles() {
        return updatedBundles;
    }

    public void setUpdatedBundles(long updatedBundles) {
        this.updatedBundles = updatedBundles;
    }

    public long getUpdatedFeatures() {
        return updatedFeatures;
    }

    public void setUpdatedFeatures(long updatedFeatures) {
        this.updatedFeatures = updatedFeatures;
    }

    public long getRemovedFeatures() {
        return removedFeatures;
    }

    public void setRemovedFeatures(long removedFeatures) {
        this.removedFeatures = removedFeatures;
    }

    public long getOverridenFeatures() {
        return overridenFeatures;
    }

    public void setOverridenFeatures(long overridenFeatures) {
        this.overridenFeatures = overridenFeatures;
    }

}

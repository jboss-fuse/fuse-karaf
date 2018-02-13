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

import org.jboss.fuse.patch.management.EnvService;
import org.jboss.fuse.patch.management.EnvType;
import org.osgi.framework.BundleContext;

public class DefaultEnvService implements EnvService {

    private final BundleContext systemContext;
    private final File karafHome;
    private final File karafBase;

    public DefaultEnvService(BundleContext systemContext, File karafHome, File karafBase) {
        this.systemContext = systemContext;
        this.karafHome = karafHome;
        this.karafBase = karafBase;
    }

    @Override
    public EnvType determineEnvironmentType() throws IOException {
        if (Boolean.getBoolean("patching.disabled")) {
            return EnvType.UNKNOWN;
        }

        return isChild(systemContext) ? EnvType.STANDALONE_CHILD : EnvType.STANDALONE;
    }

    /**
     * Using some String manipulation it returns whether we have Karaf child container
     * @param systemContext
     * @return
     */
    private boolean isChild(BundleContext systemContext) {
        String karafName = systemContext.getProperty("karaf.name");
        String karafInstances = systemContext.getProperty("karaf.instances");
        String karafHome = systemContext.getProperty("karaf.home");
        String karafBase = systemContext.getProperty("karaf.base");
        return !karafBase.equals(karafHome)
                && (karafInstances + File.separatorChar + karafName).equals(karafBase);
    }

}

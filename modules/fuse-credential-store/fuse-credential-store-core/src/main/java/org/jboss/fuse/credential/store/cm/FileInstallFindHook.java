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
package org.jboss.fuse.credential.store.cm;

import java.util.Collection;
import java.util.Iterator;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.FindHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileInstallFindHook implements FindHook {

    public static final Logger LOG = LoggerFactory.getLogger(FileInstallFindHook.class);

    /**
     * Hides original {@link org.osgi.service.cm.ConfigurationAdmin} from felix.fileinstall bundle and wrapping
     * one from other bundles.
     * @param context
     * @param name
     * @param filter
     * @param allServices
     * @param references
     */
    @Override
    public void find(BundleContext context, String name, String filter, boolean allServices, Collection<ServiceReference<?>> references) {
        boolean fileinstall = false;
        if (context != null) {
            Bundle b = context.getBundle();
            if (b != null) {
                String sn = b.getSymbolicName();
                if (sn != null) {
                    fileinstall = "org.apache.felix.fileinstall".equals(sn);
                }
            }
        }

        for (Iterator<ServiceReference<?>> iterator = references.iterator(); iterator.hasNext();) {
            ServiceReference<?> reference = iterator.next();
            String pid = (String) reference.getProperty(Constants.SERVICE_PID);
            String kind = (String) reference.getProperty("kind");
            boolean elytron = "elytron".equals(kind);
            if (elytron || "org.apache.felix.cm.ConfigurationAdmin".equals(pid)) {
                // hide wrapper from all but fileinstall and present wrapper only to fileinstall bundle
                if ((elytron && !fileinstall) || (!elytron && fileinstall)) {
                    iterator.remove();
                }
            }
        }
    }

}

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
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileInstallEventListenerHook implements EventListenerHook {

    public static final Logger LOG = LoggerFactory.getLogger(FileInstallEventListenerHook.class);

    /**
     * Filters out events related to original {@link org.osgi.service.cm.ConfigurationAdmin} for felix.fileinstall
     * bundle and events related to wrapping {@link org.osgi.service.cm.ConfigurationAdmin} for other bundles.
     * @param event
     * @param listeners
     */
    @Override
    public void event(ServiceEvent event, Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners) {
        Object oc = event.getServiceReference().getProperty("objectClass");
        if (oc instanceof String[] && ((String[]) oc)[0].equals("org.osgi.service.cm.ConfigurationAdmin")) {
            for (Iterator<Map.Entry<BundleContext, Collection<ListenerHook.ListenerInfo>>> iterator = listeners.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<BundleContext, Collection<ListenerHook.ListenerInfo>> entry = iterator.next();
                BundleContext bc = entry.getKey();
                boolean elytron = "elytron".equals(event.getServiceReference().getProperty("kind"));
                if (!elytron && "org.apache.felix.fileinstall".equals(bc.getBundle().getSymbolicName())) {
                    entry.getValue().removeIf(li -> li.getFilter() != null && li.getFilter().contains("objectClass=org.osgi.service.cm.ConfigurationAdmin"));
                } else if (elytron) {
                    iterator.remove();
                }
            }
        }
    }

}

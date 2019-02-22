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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jboss.fuse.credential.store.cm.EncryptingPersistenceManager.ENCRYPTED_PREFIX;

/**
 * A wrapper around {@link ConfigurationAdmin} service that never returns decrypted properties, when using
 * {@link EncryptingPersistenceManager}.
 * Such service is registered with low {@code service.rank} but it's the only one available to {@code org.apache.felix.fileinstall}
 * bundle.
 */
public class ConfigurationAdminWrapper implements ConfigurationAdmin {

    public static final Logger LOG = LoggerFactory.getLogger(ConfigurationAdminWrapper.class);

    private ConfigurationAdmin delegate;

    public ConfigurationAdminWrapper(ConfigurationAdmin delegate) {
        this.delegate = delegate;
    }

    @Override
    public Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        return new ConfigurationWrapper(delegate.createFactoryConfiguration(factoryPid));
    }

    @Override
    public Configuration createFactoryConfiguration(String factoryPid, String location) throws IOException {
        return new ConfigurationWrapper(delegate.createFactoryConfiguration(factoryPid, location));
    }

    @Override
    public Configuration getConfiguration(String pid, String location) throws IOException {
        return new ConfigurationWrapper(delegate.getConfiguration(pid, location));
    }

    @Override
    public Configuration getConfiguration(String pid) throws IOException {
        return new ConfigurationWrapper(delegate.getConfiguration(pid));
    }

    @Override
    public Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException {
        Configuration[] configurations = delegate.listConfigurations(filter);
        if (configurations == null) {
            return null;
        }
        Configuration[] wrappers = new Configuration[configurations.length];
        int idx = 0;
        for (Configuration c : configurations) {
            wrappers[idx++] = new ConfigurationWrapper(c);
        }

        return wrappers;
    }

    /**
     * This configuration wrapper hides dereferenced credential store aliases
     */
    public static class ConfigurationWrapper implements Configuration {

        private Configuration delegate;

        public ConfigurationWrapper(Configuration delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getPid() {
            return delegate.getPid();
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            Dictionary<String, Object> original = delegate.getProperties();
            if (original == null) {
                return null;
            }
            Dictionary<String, Object> filtered = new Hashtable<>();
            Set<String> ignored = new HashSet<>();
            for (Enumeration<String> e = original.keys(); e.hasMoreElements();) {
                String k = e.nextElement();
                Object v = original.get(k);
                if (k.startsWith(ENCRYPTED_PREFIX)) {
                    String k2 = k.substring(ENCRYPTED_PREFIX.length());
                    // either overwrite already put decrypted property or ignore later instance
                    filtered.put(k2, v);
                    ignored.add(k2);
                } else {
                    if (!ignored.contains(k)) {
                        filtered.put(k, v);
                    }
                }
            }
            return filtered;
        }

        @Override
        public void update(Dictionary<String, ?> properties) throws IOException {
            delegate.update(properties);
        }

        @Override
        public void delete() throws IOException {
            delegate.delete();
        }

        @Override
        public String getFactoryPid() {
            return delegate.getFactoryPid();
        }

        @Override
        public void update() throws IOException {
            delegate.update();
        }

        @Override
        public void setBundleLocation(String location) {
            delegate.setBundleLocation(location);
        }

        @Override
        public String getBundleLocation() {
            return delegate.getBundleLocation();
        }

        @Override
        public long getChangeCount() {
            return delegate.getChangeCount();
        }
    }

}

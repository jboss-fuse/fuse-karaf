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
import java.security.GeneralSecurityException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.felix.cm.PersistenceManager;
import org.jboss.fuse.credential.store.CredentialStoreService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PersistenceManager} that performs encryption/decryption of properties before storing them using normal
 * {@link org.apache.felix.cm.file.FilePersistenceManager}
 */
public class EncryptingPersistenceManager implements PersistenceManager {

    static final String ENCRYPTED_PREFIX = "_encrypted.";

    private static Logger log = LoggerFactory.getLogger(EncryptingPersistenceManager.class);
    private final CredentialStoreService credentialStoreService;

    private BundleContext context;
    private PersistenceManager filePm;
    private ServiceRegistration<PersistenceManager> elytronPmRegistration;

    private ServiceRegistration<ConfigurationAdmin> cmWrapperRegistration;

    public EncryptingPersistenceManager(BundleContext context, CredentialStoreService credentialStoreService) {
        this.context = context;
        this.credentialStoreService = credentialStoreService;
    }

    /**
     * When real {@link PersistenceManager} is available, set it here and register this class as "elytron"
     * {@link PersistenceManager}
     * @param filePm
     * @param fileinstallBundle
     */
    public void bindFilePersistenceManager(PersistenceManager filePm, Bundle fileinstallBundle) {
        this.filePm = filePm;
        // we can't use fileinstall's context, becuase this bundle is stopped
        final BundleContext bc = context;

        if (elytronPmRegistration == null) {
            if (cmWrapperRegistration != null) {
                cmWrapperRegistration.unregister();
                cmWrapperRegistration = null;
            }

            // we'll wait for ConfigurationAdmin (the real one) to get registered
            // if we use service tracker and then close it, ConfigurationAdmin would be disposed.
            final AtomicReference<ConfigurationAdmin> cmr = new AtomicReference<>();
            final CountDownLatch l = new CountDownLatch(1);
            @SuppressWarnings("unchecked")
            ServiceListener sl = event -> {
                if (event.getType() == ServiceEvent.REGISTERED) {
                    Object oc = event.getServiceReference().getProperty("objectClass");
                    if (oc instanceof String[] && ((String[]) oc)[0].equals("org.osgi.service.cm.ConfigurationAdmin")) {
                        if (!"elytron".equals(event.getServiceReference().getProperty("kind"))) {
                            cmr.compareAndSet(null, bc.getService((ServiceReference<ConfigurationAdmin>)event.getServiceReference()));
                            l.countDown();
                        }
                    }
                }
            };
            bc.addServiceListener(sl);

            try {
                // register PersistenceManager - it'll lead to registration of ConfigurationAdmin ...
                Dictionary<String, Object> properties = new Hashtable<>();
                properties.put(PersistenceManager.PROPERTY_NAME, "elytron");
                log.info("Registering encrypting CM PersistenceManager");
                elytronPmRegistration = this.context.registerService(PersistenceManager.class, this, properties);

                // ... which we should get in the listener
                l.await(5, TimeUnit.SECONDS);
                ConfigurationAdmin cm = cmr.get();

                if (cm != null) {
                    properties = new Hashtable<>();
                    // register ConfigurationAdmin wrapper that should hide original ConfigurationAdmin from felix.fileinstall
                    properties.put(Constants.SERVICE_PID, "org.apache.felix.cm.ConfigurationAdmin");
                    properties.put(Constants.SERVICE_RANKING, -1000);
                    properties.put("kind", "elytron");
                    log.info("Registering encrypting ConfigurationAdmin wrapper");
                    cmWrapperRegistration = context.registerService(ConfigurationAdmin.class, new ConfigurationAdminWrapper(cm), properties);
                } else {
                    log.warn("Can't access ConfigurationAdmin reference");
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                elytronPmRegistration = null;
            } finally {
                if (fileinstallBundle != null) {
                    try {
                        fileinstallBundle.start(Bundle.START_TRANSIENT);
                    } catch (BundleException e) {
                        log.error("Can't start felix.fileinstall bundle: " + e.getMessage(), e);
                    }
                }
                bc.removeServiceListener(sl);
            }
        }
    }

    /**
     * When real {@link PersistenceManager} stops being available, unregister this class as OSGi service for
     * {@link PersistenceManager}.
     */
    public void unbindFilePersistenceManager() {
        if (cmWrapperRegistration != null) {
            log.info("Unregistering ConfigurationAdmin wrapper");
            cmWrapperRegistration.unregister();
            cmWrapperRegistration = null;
        }
        if (elytronPmRegistration != null) {
            log.info("Unregistering encrypting CM PersistenceManager");
            elytronPmRegistration.unregister();
            elytronPmRegistration = null;
        }
        this.filePm = null;
    }

    @Override
    public boolean exists(String pid) {
        if (filePm != null) {
            return filePm.exists(pid);
        }
        return false;
    }

    @Override
    public Dictionary load(String pid) throws IOException {
        if (filePm != null) {
            return decrypt(filePm.load(pid));
        }
        return null;
    }

    @Override
    public Enumeration getDictionaries() throws IOException {
        if (filePm != null) {
            Enumeration dictionaries = filePm.getDictionaries();
            Vector<Dictionary> decrypted = new Vector<>();
            while (dictionaries.hasMoreElements()) {
                Dictionary next = (Dictionary) dictionaries.nextElement();
                if (next != null) {
                    decrypted.add(decrypt(next));
                }
            }
            return decrypted.elements();
        }
        return null;
    }

    @Override
    public void store(String pid, Dictionary properties) throws IOException {
        if (filePm != null) {
            filePm.store(pid, encrypt(properties));
        }
    }

    @Override
    public void delete(String pid) throws IOException {
        if (filePm != null) {
            filePm.delete(pid);
        }
    }

    /**
     * Called during property storage. In addition to delegating to original file persistence manager, it'll
     * actually decrypt (dereference) credential store aliases. What is passed down to file PM is set of properties
     * excluding the decrypted ones.
     * @param originalProperties
     * @return
     */
    @SuppressWarnings("unchecked")
    public Dictionary encrypt(Dictionary originalProperties) {
        if (originalProperties == null) {
            return null;
        }

        Hashtable<Object, Object> storedProperties = new Hashtable<>();
        Hashtable<Object, Object> additionalProperties = new Hashtable<>();

        for (Enumeration<?> e = originalProperties.keys(); e.hasMoreElements();) {
            Object k = e.nextElement();
            Object v = originalProperties.get(k);

            if (k instanceof String && ((String) k).startsWith(ENCRYPTED_PREFIX)) {
                // will be stored normally and not touched in original properties
                // actually it should not happen, because at runtime, we never have such properties
                storedProperties.put(k, v);
            } else if (!(v instanceof String && v.toString().startsWith("CS:"))) {
                // normal property
                storedProperties.put(k, v);
            } else {
                // decrypt inside passed properties, not the stored ones
                String value = (String) v;       // CS:xxx
                String alias = value.substring(3); // xxx

                try {
                    if (credentialStoreService.aliasExists(alias)) {
                        String secret = credentialStoreService.retrievePassword(alias);
                        if (secret != null) {
                            // replace with decrypted, so runtime has access to real values!
                            // like `config:property-list` command
                            // the trick is that felix.fileinstall should not access clear text values, so
                            // we're using some hacky OSGi hooks to not store plain passwords in ${karaf.etc}
                            additionalProperties.put(k, secret);
                            // add marking property - mostly for fileinstall
                            additionalProperties.put(ENCRYPTED_PREFIX + k, value);
                            // persist only encrypted
                            storedProperties.put(ENCRYPTED_PREFIX + k, value);
                        }
                    } else {
                        log.warn("Alias {} doesn't exist in credential store, skipping", value);
                        storedProperties.put(k, value);
                    }
                } catch (GeneralSecurityException ex) {
                    log.error("Problem decrypting property with reference \"" + value + ": " + ex.getMessage(), ex);
                    storedProperties.put(k, value);
                }
            }
        }

        for (Map.Entry<Object, Object> e : additionalProperties.entrySet()) {
            originalProperties.put(e.getKey(), e.getValue());
        }

        return storedProperties;
    }

    public Dictionary decrypt(Dictionary originalProperties) {
        if (originalProperties == null) {
            return null;
        }

        Dictionary<Object, Object> loadedProperties = new Hashtable<>();

        for (Enumeration<?> e = originalProperties.keys(); e.hasMoreElements();) {
            Object k = e.nextElement();
            Object v = originalProperties.get(k);

            if (k instanceof String && ((String) k).startsWith(ENCRYPTED_PREFIX)
                    && v instanceof String && ((String) v).startsWith("CS:")) {
                String key = ((String) k).substring(ENCRYPTED_PREFIX.length());
                String value = (String) v;                 // CS:xxx
                String alias = value.substring(3); // xxx

                try {
                    if (credentialStoreService.aliasExists(alias)) {
                        String secret = credentialStoreService.retrievePassword(alias);
                        if (secret != null) {
                            loadedProperties.put(key, secret);
                        }
                    } else {
                        log.warn("Alias {} doesn't exist in credential store, skipping", alias);
                        loadedProperties.put(k, v);
                    }
                } catch (GeneralSecurityException ex) {
                    log.error("Problem decrypting property with reference \"" + value + ": " + ex.getMessage(), ex);
                    loadedProperties.put(k, value);
                }
            } else {
                loadedProperties.put(k, v);
            }
        }

        return loadedProperties;
    }

    /**
     * Unregister CM wrapper
     */
    public void cleanup() {
        if (cmWrapperRegistration != null) {
            cmWrapperRegistration.unregister();
        }
    }

}

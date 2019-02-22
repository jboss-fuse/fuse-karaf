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
package org.jboss.fuse.credential.store.impl;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.felix.cm.PersistenceManager;
import org.apache.felix.cm.file.FilePersistenceManager;
import org.jboss.fuse.credential.store.CredentialStoreConfiguration;
import org.jboss.fuse.credential.store.CredentialStoreService;
import org.jboss.fuse.credential.store.cm.EncryptingPersistenceManager;
import org.jboss.fuse.credential.store.cm.FileInstallEventListenerHook;
import org.jboss.fuse.credential.store.cm.FileInstallFindHook;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.credential.store.CredentialStore;
import org.wildfly.security.credential.store.CredentialStoreException;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordSpec;

public class CredentialStoreServiceImpl implements CredentialStoreService {

    public static final Logger LOG = LoggerFactory.getLogger(CredentialStoreServiceImpl.class);

    // Elytron classes are private packaged, so it's better to use it explicitly than via Security.addProvider()
    private Provider elytronProvider;

    private CredentialStore credentialStore;

    private BundleContext context;
    private Bundle fileinstallBundle;

    // special PersistenceManager that delegates to default one using encryption/decryption on the fly
    private EncryptingPersistenceManager encryptingPm;

    // FindHook and EventListenerHook to hide original ConfigurationAdmin from fileinstall
    private ServiceRegistration<FindHook> findHookRegistration;
    private ServiceRegistration<EventListenerHook> eventLlistenerHookRegistration;

    // If we can't configure/register elytron persistence manager, we have to register the default one ("file")
    // under "elytron" name (as configured in etc/config.properties)
    private ServiceRegistration<PersistenceManager> filePmRegistration;

    // tracker for original/default/"file" PersistenceManager
    private ServiceTracker<PersistenceManager, PersistenceManager> filePmTracker;

    private boolean servicesRegistered = false;

    public CredentialStoreServiceImpl(BundleContext context, Provider elytronProvider) {
        this.context = context;
        this.elytronProvider = elytronProvider;
    }

    @Override
    public boolean validate() {
        if (credentialStore == null) {
            if (Activator.getConfig() != null) {
                if (!Activator.getConfig().isDiscoveryPerformed()) {
                    System.out.println("Configuration was not loaded and Credential Store is not available");
                    return false;
                }
                if (!Activator.getConfig().discover()) {
                    System.out.println("Configuration was not found and Credential Store is not available.");
                    return false;
                }
                System.out.println("Credential Store was not loaded properly. Please consult logs for more details.");
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean available() {
        return credentialStore != null;
    }

    @Override
    public void useCredentialStoreFromConfig(CredentialStoreConfiguration config) throws IOException, GeneralSecurityException {
        // will create actual file
        credentialStore = loadCredentialStore(config);
        registerOSGiServices();
    }

    @Override
    public Set<String> aliases() throws CredentialStoreException {
        return credentialStore.getAliases();
    }

    @Override
    public String retrievePassword(String alias) throws CredentialStoreException {
        PasswordCredential credential = credentialStore.retrieve(alias, PasswordCredential.class);
        return credential == null ? null : new String(credential.getPassword().castAs(ClearPassword.class).getPassword());
    }

    @Override
    public boolean aliasExists(String alias) throws CredentialStoreException {
        return credentialStore.exists(alias, Credential.class);
    }

    @Override
    public void addAlias(String alias, String secret) throws InvalidKeySpecException, CredentialStoreException, NoSuchAlgorithmException {
        final PasswordFactory passwordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR, elytronProvider);
        final Password password = passwordFactory.generatePassword(new ClearPasswordSpec(secret.toCharArray()));

        credentialStore.store(alias, new PasswordCredential(password));
        credentialStore.flush();
    }

    @Override
    public void removeAlias(String alias) throws CredentialStoreException {
        credentialStore.remove(alias, Credential.class);
        credentialStore.flush();
    }

    @Override
    public Provider getElytronProvider() {
        return elytronProvider;
    }

    @Override
    public void cleanup(boolean restoreOriginalPm) {
        if (filePmTracker != null) {
            filePmTracker.close();
            filePmTracker = null;
        }

        if (filePmRegistration != null) {
            filePmRegistration.unregister();
            filePmRegistration = null;
        }

        if (findHookRegistration != null) {
            findHookRegistration.unregister();
            findHookRegistration = null;
        }

        if (eventLlistenerHookRegistration != null) {
            eventLlistenerHookRegistration.unregister();
            eventLlistenerHookRegistration = null;
        }

        if (encryptingPm != null) {
            encryptingPm.cleanup();
            encryptingPm = null;
        }

        servicesRegistered = false;

        // re-register original "file" persistence manager
        if (restoreOriginalPm && "elytron".equals(context.getProperty("felix.cm.pm"))) {
            registerFallbackPersistenceManager();
        }
    }

    public MaskedPasswordData generateMaskedPassword(String algorithm, char[] password, char[] ivc, int iterationCount, byte[] salt)
            throws Exception {
        // ic, iv, salt
        MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec
                = new MaskedPasswordAlgorithmSpec(ivc, iterationCount, salt);

        PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, elytronProvider);
        // ic, iv, salt, password.
        // Elytron's PasswordFactory can use it to generate masked passwords
        EncryptablePasswordSpec encryptablePasswordSpec = new EncryptablePasswordSpec(password, maskedPasswordAlgorithmSpec);

        // encrypted master password
        final MaskedPassword maskedPassword =
                passwordFactory.generatePassword(encryptablePasswordSpec)
                        .castAs(MaskedPassword.class);

        return new MaskedPasswordData() {
            @Override
            public byte[] getSecret() {
                return maskedPassword.getMaskedPasswordBytes();
            }

            @Override
            public AlgorithmParameterSpec getSpec() {
                return maskedPassword.getParameterSpec();
            }
        };
    }

    /**
     * <p>This methods ensures that:<ul>
     *     <li>Fileinstall won't ever write dereferenced secrets to etc/*.files by registering OSGi hooks and
     *     wrapper around {@link org.osgi.service.cm.ConfigurationAdmin} that hides decrypted properties</li>
     *     <li>{@link org.osgi.service.cm.ConfigurationAdmin} gets access to {@code elytron} {@link PersistenceManager}
     *     that performs encryption/decryption ((de)referencing) of aliases</li>
     * </ul></p>
     */
    private void registerOSGiServices() {
        // fileinstall interaction
        // "A solution is to transiently stop the target bundle before the hook is registered and then transiently
        // started it again, if the bundle is active. It is usually not advised to start/stop other bundles but this
        // seems to be the only reliable solution." -- OSGi Core R6, 55.3.1 Proxying

        if (!servicesRegistered && FrameworkUtil.getBundle(Activator.class) != null) {
            // we're in OSGi

            String felixCmPm = context.getProperty("felix.cm.pm");
            if (!"elytron".equals(felixCmPm)) {
                LOG.info("felix.cm.pm property from ${karaf.etc}/config.properties is not \"elytron\" - ConfigurationAdmin"
                        + " properties won't be integrated with credential store.");
                return;
            }

            for (Bundle bundle : context.getBundles()) {
                if ("org.apache.felix.fileinstall".equals(bundle.getSymbolicName())) {
                    fileinstallBundle = bundle;
                    break;
                }
            }

            try {
                // create EncryptingPersistenceManager which is registered/unregistered whenever original file PersistenceManager
                // is available or not
                encryptingPm = new EncryptingPersistenceManager(this.context, this);

                // stop fileinstall, because we're going to proxy ConfigurationAdmin for him
                if (fileinstallBundle != null) {
                    // we will start it again after registration of wrapping ConfigurationAdmin
                    fileinstallBundle.stop(Bundle.STOP_TRANSIENT);
                }

                // unregister "elytron" PM if registered as fallback when felix.pm.cm was set, but configuration
                // was not discovered - this will unregister ConfigurationAdmin as well!
                if (filePmRegistration != null) {
                    filePmRegistration.unregister();
                    filePmRegistration = null;
                }

                // before we register ConfigurationAdmin wrapper, we already need the hooks, so no bundle
                // will grab 2 references to ConfigurationAdmin
                findHookRegistration = this.context.registerService(FindHook.class, new FileInstallFindHook(), null);
                eventLlistenerHookRegistration = this.context.registerService(EventListenerHook.class, new FileInstallEventListenerHook(), null);

                // install configadmin persistence manager after finding default ("file") PersistenceManager
                Filter filePmFilter = this.context.createFilter(String.format("(&(objectClass=%s)(%s=%s))",
                        PersistenceManager.class.getName(),
                        PersistenceManager.PROPERTY_NAME, FilePersistenceManager.DEFAULT_PERSISTENCE_MANAGER_NAME));
                filePmTracker = new ServiceTracker<>(this.context, filePmFilter, new PmTrackerCustomizer(fileinstallBundle));
                filePmTracker.open();

                servicesRegistered = true;
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                cleanup(true);
                servicesRegistered = false;
            }
        }
    }

    /**
     * Activator may register original "file" {@link PersistenceManager} under "elytron" name to satisfy
     * {@link org.osgi.service.cm.ConfigurationAdmin} when felix.cm.pm is configured
     */
    void registerFallbackPersistenceManager() {
        try {
            // install configadmin persistence manager after finding default ("file") PersistenceManager
            Filter filePmFilter = this.context.createFilter(String.format("(&(objectClass=%s)(%s=%s))",
                    PersistenceManager.class.getName(),
                    PersistenceManager.PROPERTY_NAME, FilePersistenceManager.DEFAULT_PERSISTENCE_MANAGER_NAME));
            ServiceTracker<PersistenceManager, PersistenceManager> filePmTracker =
                    new ServiceTracker<>(this.context, filePmFilter, null);
            filePmTracker.open();
            PersistenceManager filePm = null;
            try {
                filePm = filePmTracker.waitForService(TimeUnit.SECONDS.toMillis(5));
                if (filePm == null) {
                    LOG.error("Elytron configuration is not available and there's no default file persistence manager for ConfigurationAdmin service."
                            + " Please check etc/config.properties and \"felix.cm.pm\" property");
                    return;
                }
            } finally {
                filePmTracker.close();
            }

            LOG.info("Registering non-encrypting file Persistence Manager for ConfigurationAdmin service.");

            // register the same service with different properties
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(PersistenceManager.PROPERTY_NAME, "elytron");
            properties.put(Constants.SERVICE_RANKING, -1000);
            filePmRegistration = context.registerService(PersistenceManager.class, filePm, properties);
        } catch (InvalidSyntaxException e) {
            LOG.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Loads {@link CredentialStore} after successful discovery of configuration.
     * @return
     */
    public CredentialStore loadCredentialStore(CredentialStoreConfiguration config) throws IOException, GeneralSecurityException {
        // when starting Fuse, discover() has to be called, but credential-store:create command may also
        // prepare configuration so it is ready to generate CredentialStore
        if (!config.isDiscoveryPerformed() && config.getKey() == null) {
            throw new IllegalStateException("Configuration was not loaded");
        }

        CredentialStore cs = CredentialStore.getInstance(KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE,
                elytronProvider);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("create", config.getKey() == null ? "false" : "true");
        parameters.put("keyStoreType", "PKCS12");
        parameters.put("location", config.getLocation().getCanonicalPath());

        // side effect (I know, I know, ...) of this call is setting protectionParameters to byte array
        config.getEncodedProtectionParameters();

        AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance(config.getProtectionAlgorithmName(),
                elytronProvider);
        algorithmParameters.init(config.getProtectionParameters());
        MaskedPasswordAlgorithmSpec maskedPasswordAlgorithmSpec
                = algorithmParameters.getParameterSpec(MaskedPasswordAlgorithmSpec.class);

        // specification of all that's needed to create MaskedPassword
        MaskedPasswordSpec spec = new MaskedPasswordSpec(maskedPasswordAlgorithmSpec.getInitialKeyMaterial(),
                maskedPasswordAlgorithmSpec.getIterationCount(),
                maskedPasswordAlgorithmSpec.getSalt(),
                config.getKey());

        PasswordFactory passwordFactory = PasswordFactory.getInstance(config.getProtectionAlgorithmName(),
                elytronProvider);
        MaskedPassword maskedPassword = passwordFactory.generatePassword(spec).castAs(MaskedPassword.class);

        // masked -> clear text
        PasswordFactory ctPasswordFactory = PasswordFactory.getInstance(ClearPassword.ALGORITHM_CLEAR,
                elytronProvider);
        ClearPasswordSpec clearPasswordSpec = passwordFactory.getKeySpec(maskedPassword, ClearPasswordSpec.class);
        Password ctPassword = ctPasswordFactory.generatePassword(clearPasswordSpec);

        // org.wildfly.security.credential.source.CredentialSource is needed to initialize CredentialStore
        CredentialSource source = IdentityCredentials.NONE.withCredential(new PasswordCredential(ctPassword));

        CredentialStore.CredentialSourceProtectionParameter csProtection
                = new CredentialStore.CredentialSourceProtectionParameter(source);
        // will create or load credential store
        cs.initialize(parameters, csProtection);
        cs.flush();

        return cs;
    }

    /**
     * Tracks default {@link PersistenceManager} and registers wrapping PM that handles encryption
     */
    private class PmTrackerCustomizer implements ServiceTrackerCustomizer<PersistenceManager, PersistenceManager> {

        private final Bundle fileinstallBundle;

        public PmTrackerCustomizer(Bundle fileinstallBundle) {
            this.fileinstallBundle = fileinstallBundle;
        }

        @Override
        public PersistenceManager addingService(ServiceReference<PersistenceManager> reference) {
            PersistenceManager filePm = context.getService(reference);
            if (filePm != null && encryptingPm != null) {
                encryptingPm.bindFilePersistenceManager(filePm, fileinstallBundle);
            } else {
                LOG.warn("\"file\" PersistenceManager is not available. Can't register encrypting wrapper for ConfigurationAdmin.");
            }
            return null;
        }

        @Override
        public void modifiedService(ServiceReference<PersistenceManager> reference, PersistenceManager service) {
            PersistenceManager filePm = context.getService(reference);
            if (filePm != null && encryptingPm != null) {
                encryptingPm.bindFilePersistenceManager(filePm, fileinstallBundle);
            } else {
                LOG.warn("\"file\" PersistenceManager is not available. Can't register encrypting wrapper for ConfigurationAdmin.");
            }
        }

        @Override
        public void removedService(ServiceReference<PersistenceManager> reference, PersistenceManager service) {
            if (encryptingPm != null) {
                encryptingPm.unbindFilePersistenceManager();
            }
        }
    }

}

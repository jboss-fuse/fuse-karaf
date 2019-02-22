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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Proxy;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jboss.fuse.credential.store.CredentialStoreConfiguration;
import org.jboss.fuse.credential.store.CredentialStoreHelper;
import org.jboss.fuse.credential.store.CredentialStoreService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.WildFlyElytronProvider;

/**
 * <p>Standard OSGI {@link BundleActivator}: sets up the Credential Store from the environment variables or
 * system properties and replaces the system properties with the values stored within it.</p>
 *
 * <p>On startup, replaces the {@link RuntimeMXBean} to hide the clear text values from viewing through JMX.</p>
 *
 * <p>When stopping, restores the original {@link RuntimeMXBean} and original system property values.</p>
 */
public final class Activator implements BundleActivator,
        ServiceTrackerCustomizer<MBeanServer, MBeanServer> {

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private static final String SENSITIVE_VALUE_REPLACEMENT = "<sensitive>";

    // configuration may tell us why Credential Store was not loaded (due to missing or invalid configuration)
    private static CredentialStoreConfiguration config;

    private static Provider elytronProvider;

    private BundleContext context;

    private ObjectName runtimeBeanName;
    private RuntimeMXBean originalRuntimeBean;

    private ServiceReference<MBeanServer> mbeanServerReference;
    private ServiceTracker<MBeanServer, MBeanServer> mbeanServerTracker;

    // actual service used by commands. Commands need it, even if it may not operate on existing credential store
    private CredentialStoreServiceImpl credentialStoreService;
    private ServiceRegistration<CredentialStoreService> credentialStoreServiceRegistration;

    // a map of all initially available system properties that were replaced by decrypted versions
    private final Map<String, String> replacedProperties = new HashMap<>();

    public static Provider getElytronProvider() {
        return elytronProvider;
    }

    public static CredentialStoreConfiguration getConfig() {
        return config;
    }

    /**
     * If there are any Credential store references as values in the system properties, adds
     * {@link WildFlyElytronProvider} to {@link java.security.Security} providers, replaces those values with the values from the
     * Credential store and installs the JMX filter to prevent the clear text value leakage.
     *
     * @param context
     *            OSGI bundle context
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        elytronProvider = new WildFlyElytronProvider();

        // for test purposes
        boolean osgi = FrameworkUtil.getBundle(getClass()) != null;
        boolean elytronCm = "elytron".equals(context.getProperty("felix.cm.pm"));

        credentialStoreService = new CredentialStoreServiceImpl(this.context, elytronProvider);

        // register facade for credential store
        if (osgi) {
            credentialStoreServiceRegistration = this.context.registerService(
                    CredentialStoreService.class, credentialStoreService, null);
        }

        // discover configuration at bundle start (so usually very early)
        config = new CredentialStoreConfiguration(credentialStoreService);
        if (!config.discover()) {
            LOG.info("Credential Store configuration not found. System properties and configuration encryption not supported.");

            // however, we have to register "elytron" persistence manager, because that's what's inside etc/config.properties
            // otherwise configadmin won't be available at all!
            if (osgi && elytronCm) {
                credentialStoreService.registerFallbackPersistenceManager();
            }

            return;
        }

        try {
            credentialStoreService.useCredentialStoreFromConfig(config);
        } catch (final Exception e) {
            final String message = e.getMessage();
            LOG.error("Unable to initialize credential store, system properties and configuration encryption not supported: " + message, e);

            if (osgi && elytronCm) {
                credentialStoreService.registerFallbackPersistenceManager();
            }

            return;
        }

        LOG.info("Processing system properties...");

        final Properties properties = System.getProperties();

        @SuppressWarnings("unchecked")
        final Hashtable<String, String> propertiesAsStringEntries = (Hashtable) properties;

        for (final Entry<String, String> property : propertiesAsStringEntries.entrySet()) {
            final String key = property.getKey();
            final String value = property.getValue();

            if (replaced(credentialStoreService, key, value)) {
                replacedProperties.put(key, value);
            }
        }

        runtimeBeanName = ObjectName.getInstance("java.lang", "type", "Runtime");

        if (!replacedProperties.isEmpty()) {
            mbeanServerTracker = new ServiceTracker<>(context, MBeanServer.class, this);
            mbeanServerTracker.open();
        }

        LOG.info("Processing system properties - done");
    }

    /**
     * Removes the addedd {@link WildFlyElytronProvider} and restores the original {@link RuntimeMXBean} and original
     * system property values, possibly containing Credential store references for values.
     *
     * @param context
     *            OSGI bundle context
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        restoreRuntimeMBean();

        if (!replacedProperties.isEmpty()) {
            // restore original value references
            replacedProperties.forEach(System::setProperty);
            replacedProperties.clear();
        }

        if (mbeanServerTracker != null) {
            mbeanServerTracker.close();
        }

        if (credentialStoreService != null) {
            credentialStoreService.cleanup(false);
        }

        cleanupRegistrations();

        config = null;
    }

    /**
     * Unregister all registered services.
     */
    private void cleanupRegistrations() {
        if (credentialStoreServiceRegistration != null) {
            credentialStoreServiceRegistration.unregister();
        }
    }

    /**
     * Using the {@link MBeanServer} from the OSGI {@link BundleContext} finds the {@link RuntimeMXBean} and replaces it
     * with a {@link Proxy} that filters access to replaced system property values from the Credential store. Values
     * will be presented as {@link #SENSITIVE_VALUE_REPLACEMENT} instead of them being in the clear.
     *
     * @param context
     *            OSGI bundle context
     * @throws JMException
     */
    private void installFilteringRuntimeBean(final BundleContext context, final MBeanServer mbeanServer) throws JMException {
        originalRuntimeBean = ManagementFactory.getRuntimeMXBean();

        final Object proxy = Proxy.newProxyInstance(runtimeBeanName.getClass().getClassLoader(),
                new Class[] {RuntimeMXBean.class}, (proxy1, method, args) -> {
                    final Object result = method.invoke(originalRuntimeBean, args);

                    // we map the values obtained through RuntimeMXBean::getSystemProperties in order to replace the
                    // values with SENSITIVE_VALUE_REPLACEMENT
                    if ("getSystemProperties".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        final Map<Object, Object> originalValues = (Map) result;

                        final Map<Object, Object> copy = new HashMap<>(originalValues);
                        for (final String replacedProperty : replacedProperties.keySet()) {
                            copy.put(replacedProperty, SENSITIVE_VALUE_REPLACEMENT);
                        }

                        return copy;
                    }

                    return result;
                });

        mbeanServer.unregisterMBean(runtimeBeanName);
        mbeanServer.registerMBean(proxy, runtimeBeanName);
    }

    private void restoreRuntimeMBean() throws JMException {
        // if we've replaced the RuntimeMXBean
        if (originalRuntimeBean != null && mbeanServerReference != null) {
            final MBeanServer mbeanServer = context.getService(mbeanServerReference);

            // and the MBeanServer is still around
            if (mbeanServer != null) {
                // remove our proxy
                mbeanServer.unregisterMBean(runtimeBeanName);
                // and restore the original
                mbeanServer.registerMBean(originalRuntimeBean, runtimeBeanName);
            }
        }
    }

    /**
     * Replaces any value that is given in Credential Store reference format with the value from the Credential Store by
     * using {@link System#setProperty(String, String)}.
     *
     * @param credentialStoreService
     *            {@link org.wildfly.security.credential.store.CredentialStore} containing the secret values
     * @param key
     *            property key
     * @param value
     *            property value, expected to be in Credential store reference format
     * @return true if any replacement was done
     */
    boolean replaced(final CredentialStoreService credentialStoreService, final String key, final String value) {
        if (!CredentialStoreHelper.couldBeCredentialStoreAlias(value)) {
            return false;
        }

        final String alias = CredentialStoreHelper.toCredentialStoreAlias(value);

        try {
            String secret = credentialStoreService.retrievePassword(alias);
            if (secret == null) {
                return false;
            }

            System.setProperty(key, secret);

            return true;
        } catch (final GeneralSecurityException e) {
            return false;
        }
    }

    @Override
    public MBeanServer addingService(ServiceReference<MBeanServer> serviceReference) {
        mbeanServerReference = serviceReference;
        MBeanServer server = context.getService(serviceReference);
        try {
            installFilteringRuntimeBean(context, server);
        } catch (JMException e) {
            LOG.error(e.getMessage(), e);
        }
        return server;
    }

    @Override
    public void modifiedService(ServiceReference<MBeanServer> serviceReference, MBeanServer mBeanServer) {
        mbeanServerReference = serviceReference;
    }

    @Override
    public void removedService(ServiceReference<MBeanServer> serviceReference, MBeanServer mBeanServer) {
        mbeanServerReference = null;
    }

}

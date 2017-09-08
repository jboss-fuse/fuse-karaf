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
package org.jboss.fuse.credential.store.karaf.util;

import java.lang.ref.WeakReference;
import java.security.Provider;
import java.security.Security;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.wildfly.security.WildFlyElytronProvider;

/**
 * Utility class that deals with {@link Security} {@link Provider}s.
 */
public final class ProviderHelper {

    public static final String WILDFLY_PROVIDER = WildFlyElytronProvider.class.getName();

    private static final Map<String, WeakReference<Provider>> PROVIDERS_CACHE = new ConcurrentHashMap<>();

    private ProviderHelper() {
        // utility class
    }

    public static Provider provider(final String classOrName) {
        final Provider installedProvider = Security.getProvider(classOrName);

        if (installedProvider != null) {
            return installedProvider;
        }

        final WeakReference<Provider> provider = PROVIDERS_CACHE.compute(classOrName,
                (c, v) -> (v == null) || (v.get() == null) ? instantiateProvider(c) : v);

        return provider.get();
    }

    private static WeakReference<Provider> instantiateProvider(final String className) {
        try {
            @SuppressWarnings("unchecked")
            final Class<Provider> providerClass = (Class<Provider>) Class.forName(className);

            if (!Provider.class.isAssignableFrom(providerClass)) {
                throw new IllegalArgumentException(
                        "The specified class: `" + className + "` is derived from java.security.Provider");
            }

            final Provider provider = providerClass.newInstance();

            return new WeakReference<>(provider);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Unable to load or instantiate the specified provider class `" + className + "`", e);
        }
    }
}

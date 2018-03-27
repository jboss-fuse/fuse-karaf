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
package org.eclipse.jgit.nls;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The purpose of this class is to provide NLS (National Language Support)
 * configurable per thread.
 *
 * <p>
 * The {@link #setLocale(Locale)} method is used to configure locale for the
 * calling thread. The locale setting is thread inheritable. This means that a
 * child thread will have the same locale setting as its creator thread until it
 * changes it explicitly.
 *
 * <p>
 * Example of usage:
 *
 * <pre>
 * NLS.setLocale(Locale.GERMAN);
 * TransportText t = NLS.getBundleFor(TransportText.class);
 * </pre>
 */
public final class NLS {

    /**
     * The root locale constant. It is defined here because the Locale.ROOT is
     * not defined in Java 5
     */
    public static final Locale ROOT_LOCALE = new Locale("", "", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    // static field instead of thread local
    private static NLS local = new NLS(Locale.getDefault());

    private final Locale locale;
    private final ConcurrentHashMap<Class, TranslationBundle> map = new ConcurrentHashMap<>();

    // Artificial inner class, so we have empty, harmless NLS$1.class in the JAR
    private Runnable artificialDollarOneRunnable = new Runnable() {
        @Override
        public void run() {
        }
    };

    private NLS(Locale locale) {
        this.locale = locale;
    }

    /**
     * Sets the locale for the calling thread.
     * <p>
     * The {@link #getBundleFor(Class)} method will honor this setting if if it
     * is supported by the provided resource bundle property files. Otherwise,
     * it will use a fall back locale as described in the
     * {@link TranslationBundle}
     *
     * @param locale
     *            the preferred locale
     */
    public static void setLocale(Locale locale) {
        local = new NLS(locale);
    }

    /**
     * Sets the JVM default locale as the locale for the calling thread.
     * <p>
     * Semantically this is equivalent to <code>NLS.setLocale(Locale.getDefault())</code>.
     */
    public static void useJVMDefaultLocale() {
        local = new NLS(Locale.getDefault());
    }

    /**
     * Returns an instance of the translation bundle of the required type. All
     * public String fields of the bundle instance will get their values
     * injected as described in the
     * {@link TranslationBundle}.
     *
     * @param type
     *            required bundle type
     * @return an instance of the required bundle type
     * @exception TranslationBundleLoadingException
     *                see
     *                {@link TranslationBundleLoadingException}
     * @exception TranslationStringMissingException
     *                see
     *                {@link TranslationStringMissingException}
     */
    public static <T extends TranslationBundle> T getBundleFor(Class<T> type) {
        return local.get(type);
    }

    @SuppressWarnings("unchecked")
    private <T extends TranslationBundle> T get(Class<T> type) {
        TranslationBundle bundle = map.get(type);
        if (bundle == null) {
            bundle = GlobalBundleCache.lookupBundle(locale, type);
            // There is a small opportunity for a race, which we may
            // lose. Accept defeat and return the winner's instance.
            TranslationBundle old = map.putIfAbsent(type, bundle);
            if (old != null) {
                bundle = old;
            }
        }
        return (T) bundle;
    }

}

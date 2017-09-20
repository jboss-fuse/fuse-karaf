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
package org.jboss.fuse.credential.store.karaf;

import org.jboss.fuse.credential.store.karaf.util.ProtectionType;
import org.jboss.fuse.credential.store.karaf.util.ProviderHelper;
import org.wildfly.security.credential.store.impl.KeyStoreCredentialStore;

/**
 * Default values taken if not specified otherwise.
 */
public final class Defaults {

    public static final String CREDENTIAL_STORE_ALGORITHM = KeyStoreCredentialStore.KEY_STORE_CREDENTIAL_STORE;

    public static final ProtectionType CREDENTIAL_TYPE = ProtectionType.masked;

    public static final String PROVIDER = ProviderHelper.WILDFLY_PROVIDER;

    private Defaults() {
        // constant holder
    }

}

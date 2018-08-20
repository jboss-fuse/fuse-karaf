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
package org.jboss.fuse.credential.store.karaf.command.completers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.wildfly.security.password.interfaces.MaskedPassword;

/**
 * A {@link Completer} that auto completes algorithms used to protect the credential store.
 */
@Service
public class CredentialStoreAlgorithmCompletionSupport implements Completer {

    /**
     * Instead o discovering {@link MaskedPassword} algorithms, we just list the ones we'd like to support
     */
    private static final String[] EXPLICITLY_SUPPORTED_ALGORITHMS = new String[] {
            // MD5-DES is kept for simplicity (for i in {1..ic}; do md5(pwd+iv); done) - not recommended
            MaskedPassword.ALGORITHM_MASKED_MD5_DES,
            MaskedPassword.ALGORITHM_MASKED_MD5_3DES,
            MaskedPassword.ALGORITHM_MASKED_SHA1_DES_EDE,
            // it's better to use 3DES than RCx
//            MaskedPassword.ALGORITHM_MASKED_SHA1_RC2_40,
//            MaskedPassword.ALGORITHM_MASKED_SHA1_RC2_128,
//            MaskedPassword.ALGORITHM_MASKED_SHA1_RC4_40,
//            MaskedPassword.ALGORITHM_MASKED_SHA1_RC4_128,
            // after https://issues.jboss.org/browse/ELY-867 is fixed, uncomment the below algorithms
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA1_AES_128,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA224_AES_128,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA256_AES_128,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA384_AES_128,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA512_AES_128,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA1_AES_256,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA224_AES_256,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA256_AES_256,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA384_AES_256,
//            MaskedPassword.ALGORITHM_MASKED_HMAC_SHA512_AES_256,
    };

    private static Set<String> supportedAlgorithms = new HashSet<>(Arrays.asList(EXPLICITLY_SUPPORTED_ALGORITHMS));

    public static boolean isSupported(String algorithm) {
        return supportedAlgorithms.contains(algorithm);
    }

    @Override
    public int complete(final Session session, final CommandLine commandLine, final List<String> candidates) {
        return new StringsCompleter(EXPLICITLY_SUPPORTED_ALGORITHMS).complete(session, commandLine, candidates);
    }

}

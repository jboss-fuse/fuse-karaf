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
package org.jboss.fuse.credential.store.karaf.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.password.interfaces.MaskedPassword;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class SecurityTest {

    public static Logger LOG = LoggerFactory.getLogger(SecurityTest.class);

    // see https://issues.jboss.org/browse/ELY-867
    @Test
    public void pbe() throws Exception {
        String pbeName = MaskedPassword.getPBEName(MaskedPassword.ALGORITHM_MASKED_HMAC_SHA512_AES_256);

        SecretKeyFactory scf = SecretKeyFactory.getInstance(pbeName);
        Cipher cipher = Cipher.getInstance(pbeName);

        PBEParameterSpec cipherSpec = new PBEParameterSpec("12345678".getBytes(), 123, new IvParameterSpec("87654321abcdefgh".getBytes()));
        PBEKeySpec keySpec = new PBEKeySpec("very-secret-initial-material".toCharArray());
        SecretKey cipherKey = scf.generateSecret(keySpec);
        cipher.init(Cipher.ENCRYPT_MODE, cipherKey, cipherSpec);
        byte[] encrypted = cipher.doFinal("passw0rd".getBytes());

        cipher.init(Cipher.DECRYPT_MODE, cipherKey, cipherSpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        assertThat(new String(decrypted), equalTo("passw0rd"));
    }

}

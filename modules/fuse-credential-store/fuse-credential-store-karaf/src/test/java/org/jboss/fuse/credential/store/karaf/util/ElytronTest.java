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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.security.AlgorithmParameters;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.LinkedList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.keystore.PasswordEntry;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.MaskedPassword;
import org.wildfly.security.password.interfaces.SaltedSimpleDigestPassword;
import org.wildfly.security.password.interfaces.SimpleDigestPassword;
import org.wildfly.security.password.interfaces.UnixMD5CryptPassword;
import org.wildfly.security.password.interfaces.UnixSHACryptPassword;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.DigestPasswordSpec;
import org.wildfly.security.password.spec.HashPasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedHashPasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.MaskedPasswordSpec;
import org.wildfly.security.password.spec.PasswordSpec;
import org.wildfly.security.password.spec.SaltedHashPasswordSpec;
import org.wildfly.security.password.spec.SaltedPasswordAlgorithmSpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ElytronTest {

    public static Logger LOG = LoggerFactory.getLogger(ElytronTest.class);

    private static Provider provider;

    @BeforeClass
    public static void init() {
//        Security.addProvider(new WildFlyElytronProvider());
//        provider = Security.getProvider("WildFlyElytron");
        provider = new WildFlyElytronProvider();
    }

    @Test
    public void elytronPasswordFactories() throws Exception {
        // for each type of password we may obtain java.security.spec.AlgorithmParameterSpec instance
        // by calling org.wildfly.security.password.Password.getParameterSpec()
        // org.wildfly.security.password.interfaces.RawPassword contains algorithm and plain password data
        // org.wildfly.security.password.impl.AbstractPasswordImpl contains password data and actual implementation
        // AbstractPasswordImpl.getKeySpec(Class<?> c) returns java.security.spec.KeySpec

        // Generally java.security.spec.KeySpec = java.security.spec.AlgorithmParameterSpec + actual sensitive key
        // for example org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec:
        //  - username
        //  - realm
        // and org.wildfly.security.password.spec.DigestPasswordSpec:
        //  - username
        //  - realm
        //  - digest
        // the obvious fact is that there's org.wildfly.security.password.spec.ClearPasswordSpec, but there's no
        // org.wildfly.security.password.spec.ClearPasswordAlgorithmSpec

        // most passwords can be created using ClearPasswordSpec - missing additional parameters (like salt or ic)
        // will be defaulted
        // it's not that easy to use the "real" PasswordSpec, as creating the private data requires algorithms
        // which are private to e.g., UnixSHACryptPasswordImpl

        {
            String algorithm = ClearPassword.ALGORITHM_CLEAR;

            PasswordSpec spec = new ClearPasswordSpec("passw0rd".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            ClearPassword password = (ClearPassword) passwordFactory.generatePassword(spec);

            try {
                AlgorithmParameters params = AlgorithmParameters.getInstance(algorithm, provider);
                fail("There is no instance of java.security.AlgorithmParameters for \"clear\" algorithm");
            } catch (NoSuchAlgorithmException expected) {
            }

            // algorithm parameters
            assertNull(password.getParameterSpec());

            // key spec
            ClearPasswordSpec pspec = passwordFactory.getKeySpec(password, ClearPasswordSpec.class);

            assertArrayEquals(((ClearPasswordSpec)spec).getEncodedPassword(), pspec.getEncodedPassword());
            LOG.info("{} password: {}", algorithm, new String(password.getPassword()));
        }

        {
            String algorithm = UnixMD5CryptPassword.ALGORITHM_CRYPT_MD5;

            PasswordSpec spec = new ClearPasswordSpec("passw0rd".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            UnixMD5CryptPassword password = (UnixMD5CryptPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertArrayEquals(((SaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(SaltedPasswordAlgorithmSpec.class);
            assertArrayEquals(((SaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=   8 prim: OCTET STRING      [HEX DUMP]:F54DFDF818625240

            // key spec
            SaltedHashPasswordSpec pspec = passwordFactory.getKeySpec(password, SaltedHashPasswordSpec.class);

            assertArrayEquals(pspec.getSalt(), password.getSalt());
            assertArrayEquals(pspec.getHash(), password.getHash());
            LOG.info("{} salt: {}", algorithm, Hex.encodeHexString(password.getSalt()));
            LOG.info("{} hash: {}", algorithm, Hex.encodeHexString(password.getHash()));
        }

        {
            String algorithm = UnixSHACryptPassword.ALGORITHM_CRYPT_SHA_512;

            PasswordSpec spec = new ClearPasswordSpec("pa5sw04d".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            UnixSHACryptPassword password = (UnixSHACryptPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertArrayEquals(((IteratedSaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((IteratedSaltedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(IteratedSaltedPasswordAlgorithmSpec.class);
            assertArrayEquals(((IteratedSaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((IteratedSaltedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=  22 cons: SEQUENCE
            //     2:d=1  hl=2 l=   2 prim:  INTEGER           :1388
            //     6:d=1  hl=2 l=  16 prim:  OCTET STRING      [HEX DUMP]:929E3D8A3D01901428C43CCF7125C32B

            // key spec
            IteratedSaltedHashPasswordSpec pspec = passwordFactory.getKeySpec(password, IteratedSaltedHashPasswordSpec.class);

            assertArrayEquals(pspec.getSalt(), password.getSalt());
            assertEquals(pspec.getIterationCount(), password.getIterationCount());
            assertArrayEquals(pspec.getHash(), password.getHash());
            LOG.info("{} salt: {}", algorithm, Hex.encodeHexString(password.getSalt()));
            LOG.info("{} ic: {}", algorithm, password.getIterationCount());
            LOG.info("{} hash: {}", algorithm, Hex.encodeHexString(password.getHash()));
        }

        {
            // this time with "real" PasswordSpec instead of ClearPasswordSpec which can't be used
            String algorithm = DigestPassword.ALGORITHM_DIGEST_MD5;

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update("user-name:realm-name:passw0rd".getBytes());
            byte[] digest = md5.digest();

            PasswordSpec spec = new DigestPasswordSpec("user-name", "realm-name", digest);
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            DigestPassword password = (DigestPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertEquals(((DigestPasswordAlgorithmSpec)parameterSpec).getUsername(), password.getUsername());
            assertEquals(((DigestPasswordAlgorithmSpec)parameterSpec).getRealm(), password.getRealm());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(DigestPasswordAlgorithmSpec.class);
            assertEquals(((DigestPasswordAlgorithmSpec)parameterSpec).getUsername(), password.getUsername());
            assertEquals(((DigestPasswordAlgorithmSpec)parameterSpec).getRealm(), password.getRealm());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=  23 cons: SEQUENCE
            //     2:d=1  hl=2 l=   9 prim:  OCTET STRING      :user-name
            //    13:d=1  hl=2 l=  10 prim:  OCTET STRING      :realm-name

            // key spec
            DigestPasswordSpec pspec = passwordFactory.getKeySpec(password, DigestPasswordSpec.class);

            assertEquals(pspec.getUsername(), password.getUsername());
            assertEquals(pspec.getRealm(), password.getRealm());
            assertArrayEquals(pspec.getDigest(), password.getDigest());
            LOG.info("{} username: {}", algorithm, password.getUsername());
            LOG.info("{} realm: {}", algorithm, password.getRealm());
            LOG.info("{} digest: {}", algorithm, Hex.encodeHexString(password.getDigest()));
        }

        {
            String algorithm = SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_MD5;

            PasswordSpec spec = new ClearPasswordSpec("passw0rd".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            SimpleDigestPassword password = (SimpleDigestPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            assertNull(password.getParameterSpec());

            // key spec
            HashPasswordSpec pspec = passwordFactory.getKeySpec(password, HashPasswordSpec.class);

            assertArrayEquals(pspec.getDigest(), password.getDigest());
            LOG.info("{} digest: {}", algorithm, Hex.encodeHexString(password.getDigest()));
        }

        {
            String algorithm = SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_MD5;

            PasswordSpec spec = new ClearPasswordSpec("passw0rd".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            SaltedSimpleDigestPassword password = (SaltedSimpleDigestPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertArrayEquals(((SaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(SaltedPasswordAlgorithmSpec.class);
            assertArrayEquals(((SaltedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=  12 prim: OCTET STRING      [HEX DUMP]:7A6A6B53A7FD1C5675884526

            // key spec
            SaltedHashPasswordSpec pspec = passwordFactory.getKeySpec(password, SaltedHashPasswordSpec.class);

            assertArrayEquals(pspec.getSalt(), password.getSalt());
            assertArrayEquals(pspec.getHash(), password.getDigest());
            LOG.info("{} salt: {}", algorithm, Hex.encodeHexString(password.getSalt()));
            LOG.info("{} digest: {}", algorithm, Hex.encodeHexString(password.getDigest()));
        }

        // passwords with masked algorithms may be created with these specs:
        //  - ClearPasswordSpec
        //  - MaskedPasswordSpec - already masked password (using PBE algorithms)
        // ClearPasswordSpec is converted to MaskedPasswordSpec using:
        //  - iv: org.wildfly.security.password.impl.MaskedPasswordImpl.DEFAULT_PBE_KEY = "somearbitrarycrazystringthatdoesnotmatter"
        //  - ic: org.wildfly.security.password.impl.MaskedPasswordImpl.DEFAULT_ITERATION_COUNT = 1000
        //  - salt: random byte[8]
        //  algorithm is changed using org.wildfly.security.password.interfaces.MaskedPassword.getPBEName()
        //  e.g., ALGORITHM_MASKED_HMAC_SHA512_AES_256 -> PBEWithHmacSHA512AndAES_256
        //  new javax.crypto.spec.PBEParameterSpec(salt, ic)
        //  new javax.crypto.spec.PBEKeySpec(iv)
        // in MaskedPasswordSpec, iv, ic, salt and masked password have to be provided explicitly
        // https://issues.jboss.org/browse/ELY-867 - can't use PBE algorithms that require IV
        //
        // with MD5-DES it's easy to decrypt the encrypted password simply by using bash, for example, having:
        //  - iv: somearbitrarycrazystringthatdoesnotmatter
        //  - ic: 1000
        //  - salt: B14384AAFA20E972
        // we can derive the password using:
        // $ v1=`echo -n somearbitrarycrazystringthatdoesnotmatter|xxd -p -c 64`
        // $ v2=B14384AAFA20E972
        // $ v=$v1$v2
        // $ for ic in {1..1000}; do v=`echo -n $v|xxd -p -r|openssl dgst -md5 -binary|xxd -p -c 64`; done; echo $v
        // 8fa8b0e09159881f6899aecae641160f
        //
        // now the password is split to produce:
        //  - iv:  6899aecae641160f
        //  - key: 8fa8b0e09159881f
        //
        // having encrypted value (base64 of it) "Sf6sYy7gNpygs311zcQh8Q=="
        // $ echo -n Sf6sYy7gNpygs311zcQh8Q==|base64 -d|openssl des -d -iv 6899aecae641160f -K 8fa8b0e09159881f
        // my password
        //
        // other PBE algorithms are not that obvious as "ic times a digest over combination of iv and salt"

        Field spi = Cipher.class.getDeclaredField("spi");
        spi.setAccessible(true);

        List<Cipher> pbeCiphers = new LinkedList<>();
        pbeCiphers.add(Cipher.getInstance("PBEWithMD5ANDdes"));
        pbeCiphers.add(Cipher.getInstance("PBEWithMD5ANDtripledes"));
        pbeCiphers.add(Cipher.getInstance("PBEWithMD5ANDtripledes"));
        pbeCiphers.add(Cipher.getInstance("PBEWithMD5AndTRIPLEDES"));
        pbeCiphers.add(Cipher.getInstance("PBEwithSHA1AndDESede"));
        pbeCiphers.add(Cipher.getInstance("PBEwithSHA1AndDESede"));
        pbeCiphers.add(Cipher.getInstance("PBEwithSHA1AndRC2_40"));
        pbeCiphers.add(Cipher.getInstance("PBEwithSHA1Andrc2_40"));
        pbeCiphers.add(Cipher.getInstance("PBEWithSHA1AndRC2_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithSHA1andRC2_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithSHA1AndRC4_40"));
        pbeCiphers.add(Cipher.getInstance("PBEWithsha1AndRC4_40"));
        pbeCiphers.add(Cipher.getInstance("PBEWithSHA1AndRC4_128"));
        pbeCiphers.add(Cipher.getInstance("pbeWithSHA1AndRC4_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA1AndAES_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA224AndAES_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA256AndAES_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA384AndAES_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA512AndAES_128"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA1AndAES_256"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA224AndAES_256"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA256AndAES_256"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA384AndAES_256"));
        pbeCiphers.add(Cipher.getInstance("PBEWithHmacSHA512AndAES_256"));
        for (Cipher pbe : pbeCiphers) {
            pbe.getProvider();
            LOG.info("{}: spi: {}", pbe.getAlgorithm(), spi.get(pbe));
        }

        // PBE ciphers which use com.sun.crypto.provider.PBES2Core spi require javax.crypto.spec.IvParameterSpec
        // thus can't be used without fixing https://issues.jboss.org/browse/ELY-867

        // after removing ciphers that reuse the same SPI (duplicate names), we're left with:
        //
        //  constant                                | Elytron ID                    | JCE ID
        // -----------------------------------------+-------------------------------+------------------------
        //  ALGORITHM_MASKED_MD5_DES                | masked-MD5-DES                | PBEWithMD5ANDdes
        //  ALGORITHM_MASKED_MD5_3DES               | masked-MD5-3DES               | PBEWithMD5ANDtripledes
        //  ALGORITHM_MASKED_SHA1_DES_EDE           | masked-SHA1-DES-EDE           | PBEwithSHA1AndDESede
        //  ALGORITHM_MASKED_SHA1_RC2_40            | masked-SHA1-RC2-40            | PBEwithSHA1AndRC2_40
        //  ALGORITHM_MASKED_SHA1_RC2_128           | masked-SHA1-RC2-128           | PBEWithSHA1AndRC2_128
        //  ALGORITHM_MASKED_SHA1_RC4_40            | masked-SHA1-RC4-40            | PBEWithSHA1AndRC4_40
        //  ALGORITHM_MASKED_SHA1_RC4_128           | masked-SHA1-RC4-128           | PBEWithSHA1AndRC4_128

        {
            String algorithm = MaskedPassword.ALGORITHM_MASKED_HMAC_SHA512_AES_256;

            PasswordSpec spec = new ClearPasswordSpec("passw0rd".toCharArray());
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            MaskedPassword password = (MaskedPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(MaskedPasswordAlgorithmSpec.class);
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=  57 cons: SEQUENCE
            //     2:d=1  hl=2 l=  41 prim:  OCTET STRING      :somearbitrarycrazystringthatdoesnotmatter
            //    45:d=1  hl=2 l=   2 prim:  INTEGER           :03E8
            //    49:d=1  hl=2 l=   8 prim:  OCTET STRING      [HEX DUMP]:199CA0EA9388372F

            // key spec
            MaskedPasswordSpec pspec = passwordFactory.getKeySpec(password, MaskedPasswordSpec.class);

            assertArrayEquals(pspec.getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(pspec.getSalt(), password.getSalt());
            assertEquals(pspec.getIterationCount(), password.getIterationCount());
            assertArrayEquals(pspec.getMaskedPasswordBytes(), password.getMaskedPasswordBytes());
            LOG.info("{} initial material: {}", algorithm, new String(password.getInitialKeyMaterial()));
            LOG.info("{} salt: {}", algorithm, Hex.encodeHexString(password.getSalt()));
            LOG.info("{} ic: {}", algorithm, password.getIterationCount());
            LOG.info("{} masked password: {}", algorithm, Hex.encodeHexString(password.getMaskedPasswordBytes()));

            // clear key spec - masked passwords can do that
            // though not with each algorithm: https://issues.jboss.org/browse/ELY-867
//            ClearPasswordSpec cspec = passwordFactory.getKeySpec(password, ClearPasswordSpec.class);
//            LOG.info("{} clear password: {}", algorithm, new String(cspec.getEncodedPassword()));
        }

        {
            // this time we'll make our own masked password spec
            // https://tools.ietf.org/html/rfc2898 - PKCS #5: Password-Based Cryptography Specification Version 2.0
            String algorithm = MaskedPassword.ALGORITHM_MASKED_SHA1_DES_EDE;

            // creating masked password manually
            String pbeName = MaskedPassword.getPBEName(algorithm);
            SecretKeyFactory scf = SecretKeyFactory.getInstance(pbeName);
            Cipher cipher = Cipher.getInstance(pbeName);
            PBEParameterSpec cipherSpec = new PBEParameterSpec("12345678".getBytes(), 123);
            PBEKeySpec keySpec = new PBEKeySpec("very-secret-initial-material".toCharArray());
            SecretKey cipherKey = scf.generateSecret(keySpec);
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey, cipherSpec);
            byte[] passwordBytes = cipher.doFinal("passw0rd".getBytes());

            PasswordSpec spec = new MaskedPasswordSpec("very-secret-initial-material".toCharArray(), 123, "12345678".getBytes(), passwordBytes);
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm, provider);
            MaskedPassword password = (MaskedPassword) passwordFactory.generatePassword(spec);

            // algorithm parameters
            AlgorithmParameterSpec parameterSpec = password.getParameterSpec();
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            // algorithm parameters via java.security.AlgorithmParameters - allows to obtain encoded representation
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(algorithm, provider);
            parameters.init(parameterSpec);
            parameterSpec = parameters.getParameterSpec(MaskedPasswordAlgorithmSpec.class);
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getSalt(), password.getSalt());
            assertEquals(((MaskedPasswordAlgorithmSpec)parameterSpec).getIterationCount(), password.getIterationCount());

            LOG.info("{} parameters encoded: {}", algorithm, Hex.encodeHexString(parameters.getEncoded()));
            // $ xclip -o | xxd -p -r | openssl asn1parse -inform der -i
            //     0:d=0  hl=2 l=  43 cons: SEQUENCE
            //     2:d=1  hl=2 l=  28 prim:  OCTET STRING      :very-secret-initial-material
            //    32:d=1  hl=2 l=   1 prim:  INTEGER           :7B
            //    35:d=1  hl=2 l=   8 prim:  OCTET STRING      :12345678

            // key spec
            MaskedPasswordSpec pspec = passwordFactory.getKeySpec(password, MaskedPasswordSpec.class);

            assertArrayEquals(pspec.getInitialKeyMaterial(), password.getInitialKeyMaterial());
            assertArrayEquals(pspec.getSalt(), password.getSalt());
            assertEquals(pspec.getIterationCount(), password.getIterationCount());
            assertArrayEquals(pspec.getMaskedPasswordBytes(), password.getMaskedPasswordBytes());
            LOG.info("{} initial material: {}", algorithm, new String(password.getInitialKeyMaterial()));
            LOG.info("{} salt: {}", algorithm, Hex.encodeHexString(password.getSalt()));
            LOG.info("{} ic: {}", algorithm, password.getIterationCount());
            LOG.info("{} masked password: {}", algorithm, Hex.encodeHexString(password.getMaskedPasswordBytes()));

            // clear key spec - masked passwords can do that
            ClearPasswordSpec cspec = passwordFactory.getKeySpec(password, ClearPasswordSpec.class);
            LOG.info("{} clear password: {}", algorithm, new String(cspec.getEncodedPassword()));
        }
    }

    @Test
    public void passwordFileKeyStore() throws Exception {
        IOUtils.write("", new FileOutputStream("target/passwords.txt"), "UTF-8");
        assertFalse(FileUtils.readFileToString(new File("target/passwords.txt"), "UTF-8").contains("alias"));

        KeyStore ks = KeyStore.getInstance("PasswordFile", provider);
        ks.load(new FileInputStream("target/passwords.txt"), null);

        Password password = PasswordFactory.getInstance(UnixSHACryptPassword.ALGORITHM_CRYPT_SHA_512, provider)
                .generatePassword(new ClearPasswordSpec("passw0rd".toCharArray()));
        PasswordEntry entry = new PasswordEntry(password);
        ks.setEntry("alias", entry, null);

        ks.store(new FileOutputStream("target/passwords.txt"), "secret".toCharArray());

        assertTrue(FileUtils.readFileToString(new File("target/passwords.txt"), "UTF-8").contains("alias"));
    }

}

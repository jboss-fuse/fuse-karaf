package org.jboss.fuse.jasypt.commands;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.commons.CommonUtils;
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.EnvironmentStringPBEConfig;
import org.jasypt.intf.service.JasyptStatelessService;
import org.jasypt.iv.ByteArrayFixedIvGenerator;
import org.jasypt.iv.NoIvGenerator;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JasypDecryptionTest {

    public static final Logger LOG = LoggerFactory.getLogger(JasypDecryptionTest.class);

    String encrypted = "pu2ZP0VTgZ83xdHEKdSTTSGekkaMmvtZMenjA5NWrRc=";
    String password = "secret";
    String algorithm = "PBEWITHHMACSHA256ANDAES_256";
    Boolean expectedIsUseIV;

    @Test
    public void decryptFrom73() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        JasyptStatelessService service = new JasyptStatelessService();

//        String clear = service.decrypt(
//                encrypted,
//                password, null, null,
//                algorithm, null, null,
//                Integer.toString(StandardPBEByteEncryptor.DEFAULT_KEY_OBTENTION_ITERATIONS), null, null,
//                null, null, null,
//                null, null, null,
//                null, null, null,
//                CommonUtils.STRING_OUTPUT_TYPE_BASE64, null, null,
//                false ? RandomIvGenerator.class.getName() : ByteArrayFixedIvGenerator.class.getName(),
//                null, null
//        );

        EnvironmentStringPBEConfig config = new EnvironmentStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations(StandardPBEByteEncryptor.DEFAULT_KEY_OBTENTION_ITERATIONS);
        config.setStringOutputType(CommonUtils.STRING_OUTPUT_TYPE_BASE64);
        config.setIvGenerator(new ByteArrayFixedIvGenerator(new byte[16]));
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setConfig(config);
        String clear = encryptor.decrypt(encrypted);

        System.out.println("Algorithm used: " + algorithm);
        System.out.println("Decrypted data: " + clear);
    }

}

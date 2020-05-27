package org.jboss.fuse.jasypt.commands;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.karaf.shell.api.console.Session;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.registry.AlgorithmRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.fuse.jasypt.commands.Helpers.ALGORITHMS_THAT_REQUIRE_IV;
import static org.jboss.fuse.jasypt.commands.Helpers.isIVNeeded;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class JasypEncriptionCommandTest {

    public static final Logger LOG = LoggerFactory.getLogger(JasypEncriptionCommandTest.class);


    String stringToEncrypt = "A password-cracker walks into a bar. Orders a beer. Then a Beer. Then a BEER. beer. b33r. BeeR. Be3r. bEeR. bE3R. BeEr";
    String password = "s0m3R@nD0mP@ssW0rD";
    String algorithm;
    Boolean expectedIsUseIV;

    public JasypEncriptionCommandTest(String algorithm, Boolean expectedIsUseIV) {
        this.algorithm = algorithm;
        this.expectedIsUseIV = expectedIsUseIV;
        Security.addProvider(new BouncyCastleProvider());
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        Security.addProvider(new BouncyCastleProvider());
        List<Object[]> parameters = new ArrayList<>();

        for (Object algo :  AlgorithmRegistry.getAllPBEAlgorithms()) {
            String algorithm = (String) algo;
            parameters.add(new Object[]{algorithm, ALGORITHMS_THAT_REQUIRE_IV.contains(algorithm.toUpperCase())});
        }
        return parameters;
    }

    @Test
    public void shouldUseIVOnlyWhenNeeded() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        testEncryptionAndDecryption(expectedIsUseIV);
    }

    @Test
    public void shouldAlwaysUseIV() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        testEncryptionAndDecryption(true);

    }

    private void testEncryptionAndDecryption(boolean useIV) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        LOG.info("Testing Algorithm: '{}', requires IV: {}, is using IV: {}", algorithm, isIVNeeded(algorithm), useIV);

        // Create a ByteArrayOutputStream so that we can get the output
        // from the call to print
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Change System.out to point out to our stream
        System.setOut(new PrintStream(baos));

        Session session = mock(Session.class);
        when(session.readLine(anyString(), anyChar())).thenReturn("The quick brown fox jumped over the lazy dog");
        final Decrypt decryptCommand = new Decrypt();
        final Encrypt encryptCommand = new Encrypt();

        // Testing Encryption.

        encryptCommand.session = session;
        encryptCommand.input = stringToEncrypt;
        encryptCommand.password = password;
        encryptCommand.algorithm = algorithm;
        encryptCommand.useIVGenerator = useIV;
        encryptCommand.execute();
        String output = baos.toString();
        String actualEncriptedString = getEncryptedStringFromOutput(output).trim();
        String actualAlgorithm = getAlgorithmFromOutput(output).trim();

        assertThat(actualAlgorithm).isEqualToIgnoringCase(this.algorithm);
        assertThat(encryptCommand.useIVGenerator).isEqualTo(useIV);
        LOG.debug("actual Algorithm: '{}', is using IV: {}", actualAlgorithm, encryptCommand.useIVGenerator);


        // Testing Decryption:

        baos = new ByteArrayOutputStream();
        // Change System.out to point out to our stream
        System.setOut(new PrintStream(baos));

        decryptCommand.session = session;
        decryptCommand.input = actualEncriptedString;
        decryptCommand.password = this.password;
        decryptCommand.algorithm = this.algorithm;
        decryptCommand.useIVGenerator = useIV;
        decryptCommand.execute();

        output = baos.toString();
        String actualDecriptedString = getDecryptedStringFromOutput(output).trim();
        actualAlgorithm = getAlgorithmFromOutput(output).trim();

        assertThat(actualDecriptedString).isEqualTo(stringToEncrypt);
        assertThat(actualAlgorithm).isEqualToIgnoringCase(this.algorithm);
        assertThat(decryptCommand.useIVGenerator).isEqualTo(useIV);
    }


    private String getEncryptedStringFromOutput(String output) {
        return getStringFromOutput("Encrypted data: ", output);
    }

    private String getDecryptedStringFromOutput(String output) {
        return getStringFromOutput("Decrypted data: ", output);
    }

    private String getAlgorithmFromOutput(String output) {
        return getStringFromOutput("Algorithm used: ", output);
    }

    private String getStringFromOutput(String description, String output) {
        int startIndex = output.indexOf(description) + description.length();
        int endIndex = output.indexOf("\n", startIndex);
        if (endIndex == -1) {
            endIndex = output.length();
        }
        String data = output.substring(startIndex, endIndex);
        return Optional.ofNullable(data).orElse("");
    }
}

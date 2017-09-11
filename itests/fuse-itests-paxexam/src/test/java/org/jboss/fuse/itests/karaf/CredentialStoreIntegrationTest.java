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
package org.jboss.fuse.itests.karaf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import javax.inject.Inject;

import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class CredentialStoreIntegrationTest extends FuseKarafTestSupport {

    @Inject
    protected SessionFactory sessionFactory;

    private Session session;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @Configuration
    public Option[] configuration() {
        MavenUrlReference karafStandardFeature = maven()
                .groupId("org.apache.karaf.features").artifactId("standard")
                .type("xml").classifier("features").versionAsInProject();

        return combine(
                configurationMinimal(),
//                KarafDistributionOption.debugConfiguration("9999", true),
                KarafDistributionOption.replaceConfigurationFile("credential.store", new File("target/test-classes/credential.store")),
                environment("CREDENTIAL_STORE_PROTECTION_ALGORITHM=masked-MD5-DES"),
                environment("CREDENTIAL_STORE_PROTECTION_PARAMS=MDkEKXNvbWVhcmJpdHJhcnljcmF6eXN0cmluZ3RoYXRkb2Vzbm90bWF0dGVyAgID6AQIQt//5Ifg0x8="),
                environment("CREDENTIAL_STORE_PROTECTION=9KjAtKnaEnb3hgj+67wrS85IHABrZXBgG2gShcQ9kEGl4zjV9TLfyEwxBJ6836dI"),
                environment("CREDENTIAL_STORE_ATTR_location=credential.store"),
                vmOption("-Dprop=CS:key"),
                KarafDistributionOption.features(karafStandardFeature, "management"),
                mavenBundle("org.jboss.fuse.modules", "fuse-credential-store-karaf").versionAsInProject()
        );
    }

    @Before
    public void createSession() {
        PrintStream out = new PrintStream(output, true);
        session = sessionFactory.create(new ByteArrayInputStream(new byte[0]), out, out);
    }

    @Before
    public void resetOutput() {
        output.reset();
    }

    @Test
    public void shouldBeAbleToCreateCredentialStoreFromCommand() throws Exception {
        session.execute("credential-store:create -a location=new.store -k password=\"super secret\" -k algorithm=masked-MD5-DES");

        assertTrue("new.store should exist", new File("new.store").isFile());
    }

    @Test
    public void shouldListCredentialStoreContentFromCommand() throws Exception {
        session.execute("credential-store:list");

        assertTrue(new String(output.toByteArray()).contains("CS:key"));
    }

    @Test
    public void shouldProvideSystemProperties() {
        assertThat(System.getProperty("prop"), equalTo("this is a password"));
    }

    @Test
    public void shouldRemoveFromCredentialStoreFromCommand() throws Exception {
        session.execute("credential-store:list");

        assertTrue(new String(output.toByteArray()).contains("CS:key"));

        output.reset();

        session.execute("credential-store:remove -a key");

        session.execute("credential-store:list");

        assertFalse(new String(output.toByteArray()).contains("CS:key"));
    }

    @Test
    public void shouldStoreInCredentialStoreFromCommand() throws Exception {
        session.execute("credential-store:list");

        assertTrue(new String(output.toByteArray()).contains("CS:key"));

        output.reset();

        session.execute("credential-store:store -a attribute2 -s secret");

        assertTrue(new String(output.toByteArray())
                .contains("Value stored in the credential store to reference it use: CS:attribute2"));

        output.reset();

        session.execute("credential-store:list");

        assertTrue(new String(output.toByteArray()).contains("CS:attribute2"));
    }

}

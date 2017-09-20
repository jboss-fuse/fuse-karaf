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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.OptionUtils.combine;

public class CredentialStoreJMXIntegrationTest extends FuseKarafTestSupport {

    @Inject
    private ConfigurationAdmin cm;

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
                KarafDistributionOption.features(karafStandardFeature, "management")
        );
    }

    @Test
    public void shouldNotAllowJmxAccessToUnauthenticatedPrincipals() throws Exception {
        org.osgi.service.cm.Configuration cfg = cm.getConfiguration("org.apache.karaf.management");
        final String jmxUrl = (String) cfg.getProperties().get("serviceUrl");

        final JMXServiceURL karafViaJmx = new JMXServiceURL(jmxUrl);

        final Map<String, Object> environment = new HashMap<>();
        final String[] credentials = { "admin", "admin" };
        environment.put(JMXConnector.CREDENTIALS, credentials);

        final JMXConnector connector = JMXConnectorFactory.connect(karafViaJmx, environment);

        final MBeanServerConnection connection = connector.getMBeanServerConnection();

        final RuntimeMXBean runtimeBean = ManagementFactory.newPlatformMXBeanProxy(connection, "java.lang:type=Runtime",
                RuntimeMXBean.class);

        // TODO - better wait the OSGi way...
        Thread.sleep(2000);

        final Map<String, String> systemProperties = runtimeBean.getSystemProperties();

        assertThat(systemProperties.get("prop"), equalTo("<sensitive>"));
    }

}

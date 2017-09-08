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
import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MinimalDistroIntegrationTest {

    public static Logger LOG = LoggerFactory.getLogger(MinimalDistroIntegrationTest.class);

    // @Inject is handled by org.ops4j.pax.exam.inject.internal.ServiceInjector.injectField()
    @Inject
    private BundleContext context;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void beforeEach() {
        LOG.info("========== Running {}.{}() ==========", getClass().getName(), testName.getMethodName());
    }

    @After
    public void afterEach() {
        LOG.info("========== Finished {}.{}() ==========", getClass().getName(), testName.getMethodName());
    }

    protected Option[] baseConfiguration() {
        MavenUrlReference fuseMinimalDistro = maven()
                .groupId("org.jboss.fuse").artifactId("jboss-fuse-karaf-minimal")
                .type("zip").versionAsInProject();
        MavenUrlReference karafStandardFeature = maven()
                .groupId("org.jboss.fuse").artifactId("fuse-karaf-framework")
                .type("xml").classifier("features").versionAsInProject();

        return combine(
                new Option[] {
                        karafDistributionConfiguration().frameworkUrl(fuseMinimalDistro)
                                .unpackDirectory(new File("target/paxexam")),
                        keepRuntimeFolder(),
                        configureConsole().ignoreLocalConsole(),
                        editConfigurationFilePut("etc/branding.properties", "welcome", ""),
                        editConfigurationFilePut("etc/branding-ssh.properties", "welcome", ""),

                        // feature exam/4.11.0 uses:
                        // <bundle dependency="true">mvn:org.apache.geronimo.specs/geronimo-atinject_1.0_spec/1.0</bundle>
                        // we'll override it
                        mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.javax-inject").versionAsInProject()
                },

                // logging configuration
                editConfigurationFilePut("etc/org.ops4j.pax.logging.cfg", new File("src/test/resources/etc/org.ops4j.pax.logging.cfg"))
        );
    }

    @Configuration
    public Option[] configuration() {
        return combine(baseConfiguration());
    }

    @Test
    public void runMinimalFuse7DistroIntegrationTest() {
        LOG.info("Fuse 7 Minimal is running");
        LOG.debug("Test bundle symbolic name {}/{}", context.getBundle().getSymbolicName(), context.getBundle().getVersion());
    }

}

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
package org.jboss.fuse.patch.itests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import javax.inject.Inject;

import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.api.console.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Integration tests for patching bundles.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class PatchBundlesCommandIntegrationTest extends AbstractPatchIntegrationTest {

    // Time-out for installing/rolling back patches
    private static final long TIMEOUT = 30 * 1000;

    @Inject
    protected SessionFactory sessionFactory;

    private Session session;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @Before
    public void createPatchZipFiles() throws IOException {
        super.createPatchZipFiles();
    }

    @Before
    public void createSession() {
        PrintStream out = new PrintStream(output, true);
        session = sessionFactory.create(new ByteArrayInputStream(new byte[0]), out, out);
    }

    // Install a patch and wait for installation to complete
    protected void install(String name) throws Exception {
        session.execute(String.format("patch:install %s", name));
        await(name, true);
    }

    // Rollback a patch and wait for rollback to complete
    protected void rollback(String name) throws Exception {
        session.execute(String.format("patch:rollback %s", name));
        await(name, false);
    }

    private void await(String name, Boolean installed) throws Exception {
        long start = System.currentTimeMillis();
        boolean done = false;

        while (!done && System.currentTimeMillis() - start < TIMEOUT) {
            String result = null;
            try {
                output.reset();
                session.execute("patch:list");
            } catch (Exception exception) {
                // when we're updating patch-core, we may use stale patch:list service. Try again then before timeout.
                continue;
            }

            for (String line : output.toString("UTF-8").split("\\r?\\n")) {
                if (line.contains(name) && line.contains(!installed ? "false" : "root")) {
                    done = true;
                    break;
                }
            }

            if (!done) {
                Thread.sleep(100);
            }
        }

        if (!done) {
            fail(String.format("Patch %s does not have installed status %s after %s ms", name, installed, TIMEOUT));
        }
    }

    @Test
    public void testInstallAndRollbackPatch01() throws Exception {
        load(session, "patch-01");

        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());

        install("patch-01");
        assertEquals("1.0.1", getPatchableBundle().getVersion().toString());

        rollback("patch-01");
        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());
    }

    @Test
    public void testInstallAndRollbackPatch02() throws Exception {
        load(session, "patch-02");

        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());

        install("patch-02");
        assertEquals("1.1.2", getPatchableBundle().getVersion().toString());

        rollback("patch-02");
        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());
    }

    @Test
    public void testInstallAndRollbackPatch02WithoutRange() throws Exception {
        load(session, "patch-02-without-range");

        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());

        install("patch-02-without-range");
        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());

        rollback("patch-02-without-range");
        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());
    }

    @Test
    public void testInstallAndRollbackPatch01And02() throws Exception {
        load(session, "patch-01");
        load(session, "patch-02");

        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());

        install("patch-01");
        assertEquals("1.0.1", getPatchableBundle().getVersion().toString());

        install("patch-02");
        assertEquals("1.1.2", getPatchableBundle().getVersion().toString());

        rollback("patch-02");
        assertEquals("1.0.1", getPatchableBundle().getVersion().toString());

        rollback("patch-01");
        assertEquals("1.0.0", getPatchableBundle().getVersion().toString());
    }

    // Reinstall version 1.0.0 of the 'patchable' bundle and return the bundle id
    @Before
    public void installPatchableBundle() throws Exception {
        super.installPatchableBundle();
    }

}

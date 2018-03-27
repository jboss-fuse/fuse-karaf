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
package org.jboss.fuse.patch.itests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Integration tests for patching files.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class PatchFilesIntegrationTest extends AbstractPatchIntegrationTest {

    // Bundle-SymbolicName of the bundle we're patching
    protected static final String ORIGINAL_FILE_CONTENTS = "Original file contents\n";
    protected static final String PATCHED_FILE_CONTENTS = "Patched file contents\n";
    protected static final String PATCHED_FILE = "etc/patched.txt";
    protected static final String PATCHED2_FILE = "etc/patched2.txt";

    @Before
    public void createPatchZipFiles() throws IOException {
        File patches = new File(context.getProperty("fuse.patch.location"));
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new File(patches, "file-01.zip"))) {
            zos.putArchiveEntry(new ZipArchiveEntry("file-01.patch"));
            IOUtils.copy(context.getBundle().getResource("patches/file-01.patch").openStream(), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry(PATCHED_FILE));
            IOUtils.copy(new ByteArrayInputStream(PATCHED_FILE_CONTENTS.getBytes("UTF-8")), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry(PATCHED2_FILE));
            IOUtils.copy(new ByteArrayInputStream(PATCHED_FILE_CONTENTS.getBytes("UTF-8")), zos);
            zos.closeArchiveEntry();
        }

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new File(patches, "file-02.zip"))) {
            zos.putArchiveEntry(new ZipArchiveEntry("file-02.patch"));
            IOUtils.copy(context.getBundle().getResource("patches/file-02.patch").openStream(), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry(PATCHED_FILE));
            IOUtils.copy(new ByteArrayInputStream(PATCHED_FILE_CONTENTS.getBytes("UTF-8")), zos);
            zos.closeArchiveEntry();
            zos.putArchiveEntry(new ZipArchiveEntry(PATCHED2_FILE));
            IOUtils.copy(new ByteArrayInputStream(PATCHED_FILE_CONTENTS.getBytes("UTF-8")), zos);
            zos.closeArchiveEntry();
        }
    }

    @Test
    public void testInstallAndRollbackPatch01() throws Exception {
        load(context, "file-01");

        File base = new File(System.getProperty("karaf.base"));
        File patched = new File(base, PATCHED_FILE);

        FileUtils.write(patched, ORIGINAL_FILE_CONTENTS, "UTF-8");

        install("file-01");
        assertEquals(PATCHED_FILE_CONTENTS, FileUtils.readFileToString(patched, "UTF-8"));

        rollback("file-01");
        assertEquals(ORIGINAL_FILE_CONTENTS, FileUtils.readFileToString(patched, "UTF-8"));
    }

    @Test
    public void testInstallAndRollbackPatch02AddFile() throws Exception {
        load(context, "file-02");

        File base = new File(System.getProperty("karaf.base"));
        File patched = new File(base, PATCHED2_FILE);

        assertFalse(patched.exists());

        install("file-02");
        assertEquals(PATCHED_FILE_CONTENTS, FileUtils.readFileToString(patched, "UTF-8"));

        rollback("file-02");
        assertFalse(patched.exists());
    }

}

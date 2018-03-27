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
package org.jboss.fuse.patch.management.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.fuse.patch.management.Utils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class EOLFixesTest {

    private File target = new File("target/karaf-eol");

    @Before
    public void init() throws IOException {
        FileUtils.deleteDirectory(target);
    }

    @Test
    public void fixInCriticalDirs() throws IOException {
        {
            File t = target;
            File f = new File(t, "bin/x");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\n"));
        }
        {
            File t = target;
            File f = new File(t, "bin/y");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test\n", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\n"));
        }
        {
            File t = target;
            File f = new File(t, "bin/y.bat");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\r\n"));
        }
        {
            File t = new File(target, "y");
            File f = new File(t, "bin/x");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\n"));
        }
        {
            File t = target;
            File f = new File(t, "y/bin/x");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), not(equalTo("test\n")));
        }
    }

    @Test
    public void lineConversionInEtc() throws IOException {
        {
            File t = target;
            File f = new File(t, "etc/x1.cfg");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test\r\n", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\n"));
        }
        {
            File t = target;
            File f = new File(t, "etc/x2.cfg");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\n"));
        }
        {
            File t = target;
            File f = new File(t, "etc/x2.cfg2");
            Utils.mkdirs(f.getParentFile());
            EOLFixingFileOutputStream output = new EOLFixingFileOutputStream(t, f);
            IOUtils.write("test\r\n", output, "UTF-8");
            Utils.closeQuietly(output);
            assertThat(FileUtils.readFileToString(f, "UTF-8"), equalTo("test\r\n"));
        }
    }

    @Test
    public void copyDir() throws IOException {
        FileUtils.write(new File(target, "src/bin/admin1"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/bin/admin1.bat"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/bin/admin2"), "test\n", "UTF-8");
        FileUtils.write(new File(target, "src/bin/admin2.bat"), "test\r\n", "UTF-8");
        FileUtils.write(new File(target, "src/bin/x/admin3"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/bin/x/admin3.bat"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/x/bin/admin1"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/x/bin/admin1.bat"), "test", "UTF-8");
        FileUtils.write(new File(target, "src/x/bin/admin2"), "test\n", "UTF-8");
        FileUtils.write(new File(target, "src/x/bin/admin2.bat"), "test\r\n", "UTF-8");

        Utils.mkdirs(new File(target, "dest"));
        EOLFixingFileUtils.copyDirectory(new File(target, "src"), new File(target, "dest"), new File(target, "dest"), false);

        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/admin1"), "UTF-8"), equalTo("test\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/admin1.bat"), "UTF-8"), equalTo("test\r\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/admin2"), "UTF-8"), equalTo("test\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/admin2.bat"), "UTF-8"), equalTo("test\r\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/x/admin3"), "UTF-8"), equalTo("test\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/bin/x/admin3.bat"), "UTF-8"), equalTo("test\r\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/x/bin/admin1"), "UTF-8"), equalTo("test"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/x/bin/admin1.bat"), "UTF-8"), equalTo("test"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/x/bin/admin2"), "UTF-8"), equalTo("test\n"));
        assertThat(FileUtils.readFileToString(new File(target, "dest/x/bin/admin2.bat"), "UTF-8"), equalTo("test\r\n"));
    }

}

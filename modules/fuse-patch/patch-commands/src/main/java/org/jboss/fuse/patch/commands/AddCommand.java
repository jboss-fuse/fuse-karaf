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
package org.jboss.fuse.patch.commands;

import java.net.URL;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.jboss.fuse.patch.PatchService;
import org.jboss.fuse.patch.management.Patch;

@Command(scope = "patch", name = "add", description = "Download a patch")
public class AddCommand extends PatchCommandSupport {

    @Option(name = "--bundles", description = "Show bundles contained in patches")
    boolean bundles;

    @Argument(required = true, multiValued = false)
    String url;

    @Override
    protected void doExecute(PatchService service) throws Exception {
        Iterable<Patch> patches = service.download(new URL(url));
        display(patches, bundles);
    }

}

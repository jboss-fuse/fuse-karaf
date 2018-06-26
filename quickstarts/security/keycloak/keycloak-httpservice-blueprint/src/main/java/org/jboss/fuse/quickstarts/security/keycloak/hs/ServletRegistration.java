/*
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
package org.jboss.fuse.quickstarts.security.keycloak.hs;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import javax.servlet.Servlet;

import org.ops4j.pax.web.service.WebContainer;
import org.ops4j.pax.web.service.WebContainerConstants;
import org.osgi.service.http.HttpContext;

public class ServletRegistration {

    private Servlet infoServlet;
    private Servlet logoutServlet;

    private WebContainer webContainer;

    private HttpContext httpContext;

    public void start() throws Exception {
        httpContext = webContainer.createDefaultHttpContext();

        Dictionary<String, String> init = new Hashtable<>();
        init.put(WebContainerConstants.CONTEXT_NAME, "app2");
        webContainer.setContextParam(init, httpContext);

        webContainer.registerLoginConfig("KEYCLOAK", "hs", null, null, httpContext);

        // register two ordinary servlets using OSGi HTTP Service
        webContainer.registerServlet("/info", infoServlet, null, httpContext);
        webContainer.registerServlet("/logout", logoutServlet, null, httpContext);

        webContainer.registerConstraintMapping("admin resources", null, "/info/*",
                null, true, Collections.singletonList("admin"), httpContext);
    }

    public void stop() {
        webContainer.unregisterConstraintMapping(httpContext);
        webContainer.unregister("/info");
        webContainer.unregister("/logout");
        webContainer.unregisterLoginConfig(httpContext);
    }

    public void setWebContainer(WebContainer webContainer) {
        this.webContainer = webContainer;
    }

    public void setInfoServlet(Servlet infoServlet) {
        this.infoServlet = infoServlet;
    }

    public void setLogoutServlet(Servlet logoutServlet) {
        this.logoutServlet = logoutServlet;
    }

}

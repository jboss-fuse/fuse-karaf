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

import org.jboss.fuse.quickstarts.security.keycloak.hs.servlets.InfoServlet;
import org.jboss.fuse.quickstarts.security.keycloak.hs.servlets.LogoutServlet;
import org.ops4j.pax.web.service.WebContainer;
import org.ops4j.pax.web.service.WebContainerConstants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

public class NamedHttpContextActivator implements BundleActivator {

    private WebContainer http;
    private HttpContext httpContext;
    private ServiceTracker<HttpService, HttpService> hsTracker;
    private ServiceTracker<WebContainer, WebContainer> wcTracker;

    @Override
    public void start(BundleContext context) throws Exception {
        // org.osgi.service.http.HttpService allows only to register servlets and resources
//        hsTracker = new ServiceTracker<>(context, HttpService.class, null);
//        hsTracker.open();

        // org.ops4j.pax.web.service.WebContainer is pax-web extension of org.osgi.service.http.HttpService with
        // more registration options
        wcTracker = new ServiceTracker<>(context, WebContainer.class, null);
        wcTracker.open();
        http = wcTracker.waitForService(10000);

        if (http != null) {
            httpContext = http.createDefaultHttpContext();

            // equivalent of web.xml's /web-app/context-param to configure Keycloak config resolver
            // <context-param>
            //     <param-name>keycloak.config.resolver</param-name>
            //     <param-value>org.keycloak.adapters.osgi.PathBasedKeycloakConfigResolver</param-value>
            // </context-param>
            Dictionary<String, String> init = new Hashtable<>();
            init.put("keycloak.config.resolver", "org.keycloak.adapters.osgi.PathBasedKeycloakConfigResolver");
            // this is the way to set context path - javax.servlet.http.HttpServletRequest.getContextPath()
            // so "/info" servlet will be accessible using "http://localhost:8181/app1/info"
            init.put(WebContainerConstants.CONTEXT_NAME, "app1");
            http.setContextParam(init, httpContext);

            // set login configuration, so we can delegate to Keycloak, equivalent of:
            // <login-config>
            //     <auth-method>KEYCLOAK</auth-method>
            //     <realm-name>hs</realm-name>
            // </login-config>
            http.registerLoginConfig("KEYCLOAK", "hs", null, null, httpContext);

            // register two ordinary servlets using OSGi HTTP Service
            http.registerServlet("/info", new InfoServlet(), null, httpContext);
            http.registerServlet("/logout", new LogoutServlet(), null, httpContext);

            // security mapping for /info servlet, equivalent of:
            // <security-constraint>
            //     <web-resource-collection>
            //         <web-resource-name>admin resources</web-resource-name>
            //         <url-pattern>/info</url-pattern>
            //     </web-resource-collection>
            //     <auth-constraint>
            //         <role-name>admin</role-name>
            //     </auth-constraint>
            // </security-constraint>
            http.registerConstraintMapping("admin resources", null, "/info/*",
                    null, true, Collections.singletonList("admin"), httpContext);
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (http != null) {
            http.unregister("/info");
            http.unregister("/logout");
            http.unregisterLoginConfig(httpContext);
        }
        wcTracker.close();
    }

}

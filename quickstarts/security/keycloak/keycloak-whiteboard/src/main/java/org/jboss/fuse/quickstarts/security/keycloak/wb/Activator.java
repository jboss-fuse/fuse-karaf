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
package org.jboss.fuse.quickstarts.security.keycloak.wb;

import java.util.Collections;
import java.util.Hashtable;
import javax.servlet.Servlet;

import org.jboss.fuse.quickstarts.security.keycloak.wb.servlets.InfoServlet;
import org.jboss.fuse.quickstarts.security.keycloak.wb.servlets.LogoutServlet;
import org.ops4j.pax.web.extender.whiteboard.ExtenderConstants;
import org.ops4j.pax.web.service.WebContainer;
import org.ops4j.pax.web.service.WebContainerContext;
import org.ops4j.pax.web.service.whiteboard.HttpContextMapping;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {

    private WebContainer http;
    private HttpContext httpContext;
    private ServiceTracker<WebContainer, WebContainer> wcTracker;

    private ServiceRegistration<Servlet> infoServletRegistration;
    private ServiceRegistration<Servlet> logoutServletRegistration;
    private ServiceRegistration<HttpContextMapping> httpContextMappingRegistration;

    @Override
    public void start(BundleContext context) throws Exception {

        // org.ops4j.pax.web.service.WebContainer is needed even in whiteboard approach - we can register
        // directly Servlets, Filters, etc as OSGi services, but whiteboard doesn't track everything
        // we can do with org.ops4j.pax.web.service.WebContainer and/or WAR (e.g., Login Configuration)
        wcTracker = new ServiceTracker<>(context, WebContainer.class, null);
        wcTracker.open();
        http = wcTracker.waitForService(10000);

        if (http != null) {

            final String contextId = WebContainerContext.DefaultContextIds.DEFAULT.getValue();
//            final String contextId = "app3";

            // bundle-wide http context that we'll use as "key" to register different web elements
            // using the same key is equivalent of using the same "web application"
            // https://ops4j1.jira.com/browse/PAXWEB-1090 broke whiteboard handling with "default" context
            // and custom org.ops4j.pax.web.service.whiteboard.HttpContextMapping, that's why I'm using custom
            // contextId
            // https://ops4j1.jira.com/browse/PAXWEB-1165 fixes the problem
//            httpContext = http.createDefaultHttpContext();
            httpContext = http.createDefaultHttpContext(contextId);

            // equivalent of web.xml's /web-app/context-param to configure Keycloak config resolver
            // <context-param>
            //     <param-name>keycloak.config.resolver</param-name>
            //     <param-value>org.keycloak.adapters.osgi.PathBasedKeycloakConfigResolver</param-value>
            // </context-param>
//            Dictionary<String, String> init = new Hashtable<>();
//            init.put("keycloak.config.resolver", "org.keycloak.adapters.osgi.PathBasedKeycloakConfigResolver");
//            // this is the way to set context path - javax.servlet.http.HttpServletRequest.getContextPath()
//            // so "/info" servlet will be accessible using "http://localhost:8181/app3/info"
//            init.put(WebContainerConstants.CONTEXT_NAME, "app3");
//            http.setContextParam(init, httpContext);

            // with pax-web-extender-whiteboard however, we can register context path and context parameters
            // by registering org.ops4j.pax.web.service.whiteboard.HttpContextMapping service
            // we don't have to call org.ops4j.pax.web.service.WebContainer.setContextParam()
            HttpContextMapping contextMapping = new App3HttpContextMapping(contextId, "app3", httpContext);
            httpContextMappingRegistration = context.registerService(HttpContextMapping.class, contextMapping, null);

            // set login configuration, so we can delegate to Keycloak, equivalent of:
            // <login-config>
            //     <auth-method>KEYCLOAK</auth-method>
            //     <realm-name>hs</realm-name>
            // </login-config>
            // Keycloak uses org.keycloak.adapters.osgi.undertow.PaxWebIntegrationService#addingWebContainerCallback
            // when declaring org.keycloak.adapters.osgi.undertow.PaxWebIntegrationService in blueprint - which
            // is the recommended approach
//            http.registerLoginConfig("BASIC", "wb", null, null, httpContext);
            http.registerLoginConfig("KEYCLOAK", "wb", null, null, httpContext);

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
            // this is also done in org.keycloak.adapters.osgi.undertow.PaxWebIntegrationService#addConstraintMapping()
            http.registerConstraintMapping("admin resources", null, "/info/*",
                    null, true, Collections.singletonList("admin"), httpContext);

            // now, instead of registering servlets via org.osgi.service.http.HttpService.registerServlet()
            // we'll just register them as OSGi services - they'll be processed by pax-web-extender-whiteboard
            // it's important to set "httpContext.id" property, so servlets are registered in the same context
            // as login configuration, constraints and context parameters
            Hashtable<String, Object> infoProperties = new Hashtable<>();
            infoProperties.put(ExtenderConstants.PROPERTY_HTTP_CONTEXT_ID, contextId);
            infoProperties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, "info-servlet");
            infoProperties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, new String[] { "/info" });
            infoServletRegistration = context.registerService(Servlet.class, new InfoServlet(), infoProperties);

            Hashtable<String, Object> logoutProperties = new Hashtable<>();
            logoutProperties.put(ExtenderConstants.PROPERTY_HTTP_CONTEXT_ID, contextId);
            logoutProperties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, "logout-servlet");
            logoutProperties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, new String[] { "/logout" });
            logoutServletRegistration = context.registerService(Servlet.class, new LogoutServlet(), logoutProperties);
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (http != null) {
            http.unregisterLoginConfig(httpContext);
            http.unregisterConstraintMapping(httpContext);
        }
        if (logoutServletRegistration != null) {
            logoutServletRegistration.unregister();
        }
        if (infoServletRegistration != null) {
            infoServletRegistration.unregister();
        }
        if (httpContextMappingRegistration != null) {
            httpContextMappingRegistration.unregister();
        }
        wcTracker.close();
    }

}

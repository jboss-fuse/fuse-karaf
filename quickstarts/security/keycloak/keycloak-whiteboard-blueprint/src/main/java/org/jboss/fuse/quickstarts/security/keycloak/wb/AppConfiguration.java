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

import org.ops4j.pax.web.service.WebContainer;
import org.ops4j.pax.web.service.whiteboard.HttpContextMapping;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpContext;

public class AppConfiguration {

    private WebContainer webContainer;
    private BundleContext bundleContext;
    private App4HttpContextMapping contextMapping;

    private HttpContext httpContext;

    /**
     * This method will be called after setting all properties.
     */
    public void configure() {
        // we got the org.ops4j.pax.web.service.whiteboard.HttpContextMapping, we can take its context id
        // and create org.osgi.service.http.HttpContext
        httpContext = webContainer.createDefaultHttpContext(contextMapping.getHttpContextId());

        // and configure the context back in the mapping
        contextMapping.setHttpContext(httpContext);

        // finally the mapping will be registered as OSGi service, procesed by pax-web-extender-whiteboard
        // which will use it to configure basic settings of whiteboard "web application"
        // this could also be done using <service> blueprint xml
        bundleContext.registerService(HttpContextMapping.class, contextMapping, null);
    }

    public void setWebContainer(WebContainer webContainer) {
        this.webContainer = webContainer;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setContextMapping(App4HttpContextMapping contextMapping) {
        this.contextMapping = contextMapping;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

}

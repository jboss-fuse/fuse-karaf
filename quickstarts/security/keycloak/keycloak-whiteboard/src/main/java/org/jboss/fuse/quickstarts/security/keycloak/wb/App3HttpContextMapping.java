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

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.http.HttpContext;

public class App3HttpContextMapping implements org.ops4j.pax.web.service.whiteboard.HttpContextMapping {

    private final String contextId;
    private final String path;
    private final HttpContext httpContext;
    private final Map<String, String> contextParameters = new HashMap<>();

    public App3HttpContextMapping(String contextId, String path, HttpContext httpContext) {
        this.contextId = contextId;
        this.path = path;
        this.httpContext = httpContext;
        contextParameters.put("keycloak.config.resolver", "org.keycloak.adapters.osgi.PathBasedKeycloakConfigResolver");
    }

    @Override
    public String getHttpContextId() {
        return contextId;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Map<String, String> getParameters() {
        return contextParameters;
    }

    @Override
    public HttpContext getHttpContext() {
        return httpContext;
    }

}

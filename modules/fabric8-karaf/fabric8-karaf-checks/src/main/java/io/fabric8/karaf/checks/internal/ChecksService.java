/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.karaf.checks.internal;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import io.fabric8.karaf.checks.HealthChecker;
import io.fabric8.karaf.checks.ReadinessChecker;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(name = "io.fabric8.karaf.k8s.check", immediate = true, enabled = true,
        configurationPolicy = ConfigurationPolicy.OPTIONAL, configurationPid = "io.fabric8.checks")
public class ChecksService {

    public static final Logger LOG = LoggerFactory.getLogger(ChecksService.class);

    @Reference(service = HttpService.class, cardinality = ReferenceCardinality.MANDATORY)
    private HttpService httpService;

    @Reference(service = ReadinessChecker.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    final CopyOnWriteArrayList<ReadinessChecker> readinessCheckers = new CopyOnWriteArrayList<>();

    @Reference(service = HealthChecker.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    final CopyOnWriteArrayList<HealthChecker> healthCheckers = new CopyOnWriteArrayList<>();

    String readinessCheckPath = "/readiness-check";
    String healthCheckPath = "/health-check";

    // port set in @Activate
    private int port;
    private Undertow server;

    public ChecksService() {
        bind(new FrameworkState());
        bind(new BundleState());
        bind(new BootFeaturesState());
        try {
            bind(new BlueprintState());
        } catch (Throwable t) {
            // Ignore
        }
        try {
            bind(new ScrState());
        } catch (Throwable t) {
            // Ignore
        }
        try {
            bind(new WarState());
        } catch (Throwable t) {
            // Ignore
        }
        bind(new CamelState());
    }

    private void bind(Object checker) {
        if (checker instanceof ReadinessChecker) {
            bindReadinessCheckers((ReadinessChecker) checker);
        }
        if (checker instanceof HealthChecker) {
            bindHealthCheckers((HealthChecker) checker);
        }
    }

    @Activate
    void activate(Map<String, ?> configuration) throws ServletException, NamespaceException {
        String httpPort = (String) configuration.get("httpPort");
        this.port = -1;
        if (httpPort != null && !"".equals(httpPort.trim())) {
            try {
                this.port = Integer.parseInt(httpPort);
            } catch (NumberFormatException e) {
                LOG.warn("Can't parse {} as TCP port number", httpPort);
            }
        }

        String rcsURI = (String) configuration.get("readinessCheckPath");
        if (rcsURI != null && !"".equals(rcsURI)) {
            if (!rcsURI.startsWith("/")) {
                LOG.warn("Readiness Check URI doesn't start with \"/\", falling back to \"/readiness-check\".");
                readinessCheckPath = "/readiness-check";
            } else {
                readinessCheckPath = rcsURI.trim();
            }
        }
        String hcsURI = (String) configuration.get("healthCheckPath");
        if (hcsURI != null && !"".equals(hcsURI)) {
            if (!hcsURI.startsWith("/")) {
                LOG.warn("Health Check URI doesn't start with \"/\", falling back to \"/health-check\".");
                healthCheckPath = "/health-check";
            } else {
                healthCheckPath = hcsURI.trim();
            }
        }

        if (this.port == -1) {
            // register into built-in HttpService
            LOG.info("Starting health check service in built-in server and {} URI", healthCheckPath);
            httpService.registerServlet(healthCheckPath, new HealthCheckServlet(healthCheckers), null, null);
            LOG.info("Starting readiness check service in built-in server and {} URI", readinessCheckPath);
            httpService.registerServlet(readinessCheckPath, new ReadinessCheckServlet(readinessCheckers), null, null);
        } else {
            // create embedded, but simple Undertow instance to register the servlets
            PathHandler path = Handlers.path();
            this.server = Undertow.builder()
                    .addHttpListener(this.port, "0.0.0.0")
                    .setHandler(path)
                    .setIoThreads(2)     // defaults to Math.max(Runtime.getRuntime().availableProcessors(), 2);
                    .setWorkerThreads(4) // defaults to ioThreads * 8
                    .build();

            HttpServlet hcs = new HealthCheckServlet(healthCheckers);
            HttpServlet rcs = new ReadinessCheckServlet(readinessCheckers);

            ServletInfo hcServlet = Servlets.servlet("hcs", hcs.getClass(), new ImmediateInstanceFactory<HttpServlet>(hcs));
            hcServlet.addMapping(healthCheckPath);
            ServletInfo rcServlet = Servlets.servlet("rcs", rcs.getClass(), new ImmediateInstanceFactory<HttpServlet>(rcs));
            rcServlet.addMapping(readinessCheckPath);

            DeploymentInfo deploymentInfo = Servlets.deployment()
                    .setClassLoader(this.getClass().getClassLoader())
                    .setContextPath("/")
                    .setDeploymentName("")
                    .setUrlEncoding("UTF-8")
                    .addServlets(hcServlet, rcServlet);

            ServletContainer container = Servlets.newContainer();
            DeploymentManager dm = container.addDeployment(deploymentInfo);
            dm.deploy();
            HttpHandler handler = dm.start();

            path.addPrefixPath("/", handler);

            LOG.info("Starting health check service on port {} and {} URI", this.port, healthCheckPath);
            LOG.info("Starting readiness check service on port {} and {} URI", this.port, readinessCheckPath);
            server.start();
        }
    }

    @Deactivate
    void deactivate() {
        if (port != -1) {
            // stop Undertow
            LOG.info("Stoping health and readiness check services on port {}", this.port);
            server.stop();
            port = -1;
            server = null;
        } else {
            // unregister from built-in HttpService
            LOG.info("Unregistering health and readiness check services from built-in server");
            httpService.unregister(healthCheckPath);
            httpService.unregister(readinessCheckPath);
        }
    }

    void bindHttpService(HttpService httpService) {
        this.httpService = httpService;
    }
    void unbindHttpService(HttpService service) {
        this.httpService = null;
    }

    void bindReadinessCheckers(ReadinessChecker value) {
        readinessCheckers.add(value);
    }
    void unbindReadinessCheckers(ReadinessChecker value) {
        readinessCheckers.remove(value);
    }

    void bindHealthCheckers(HealthChecker value) {
        healthCheckers.add(value);
    }
    void unbindHealthCheckers(HealthChecker value) {
        healthCheckers.remove(value);
    }

}

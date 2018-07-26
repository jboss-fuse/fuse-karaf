keycloak-whiteboard: demonstrates a bundle that uses PAX-WEB HTTP Whiteboard Service to register servlets with Keycloak authentication
==========================
Author: Fuse Team  
Level: Intermediate  
Technologies: Keycloak, PAX-WEB
Summary: a bundle that uses PAX-WEB HTTP Whiteboard Service to register servlets with Keycloak authentication
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>


What is it?
-----------
This quickstart demonstrates how to create an OSGi bundle that uses PAX-WEB whiteboard extender mechanism to
register servlets protected by Keycloak authentication mechanisms.


System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.5.0 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

To build the quick start:

1. Change your working directory to `keycloak-whiteboard` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands to install required Keycloak features

        feature:repo-add mvn:org.keycloak/keycloak-osgi-features/${version.org.keycloak}/xml/features
        feature:install -v keycloak-pax-http-undertow

5. In the Red Hat Fuse console, enter the following command to install `keycloak-whiteboard` quickstart:

        install -s mvn:org.jboss.fuse.quickstarts.security/keycloak-whiteboard/${project.version}

6. Inside Fuse directory, create file `etc/app3-keycloak.json` with the following content configuring Keycloak integration:

        {
          "realm": "fuse7karaf",
          "auth-server-url": "http://localhost:8180/auth",
          "ssl-required": "external",
          "resource": "whiteboard-info",
          "public-client": true,
          "use-resource-role-mappings": true,
          "confidential-port": 0,
          "principal-attribute": "preferred_username"
        }


Use the bundle
--------------

We can simply browse to http://localhost:8181/app3/info URL

We will be redirected to Keycloak UI to perform authentication and when it succeeds, we'll get back to initial URL.


Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list -s` command to retrieve your bundle id and symbolic name
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

    or (uninstall by symbolic name)

        bundle:uninstall keycloak-whiteboard

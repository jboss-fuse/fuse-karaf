keycloak-war: demonstrates a WAR that uses `KEYCLOAK` authentication method
==========================
Author: Fuse Team  
Level: Intermediate  
Technologies: Keycloak, PAX-WEB
Summary: a WAR that uses `KEYCLOAK` authentication method
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>


What is it?
-----------
This quickstart demonstrates how to create a WAR application installable as OSGi bundle.
This WAR integrates with Keycloak using `web.xml` declaration that sets `KEYCLOAK` authentication mechanism.


System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.5.0 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

To build the quick start:

1. Change your working directory to `keycloak-war` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands to install required Keycloak features

        feature:repo-add mvn:org.keycloak/keycloak-osgi-features/${version.org.keycloak}/xml/features
        feature:install -v keycloak-pax-http-undertow

5. In the Red Hat Fuse console, enter the following command to install `keycloak-war` quickstart:

        install -s mvn:org.jboss.fuse.quickstarts.security/keycloak-war/${project.version}/war

6. Inside Fuse directory, create file `etc/keycloak-war-keycloak.json` with the following content configuring Keycloak integration:

        {
          "realm": "fuse7karaf",
          "auth-server-url": "http://localhost:8180/auth",
          "ssl-required": "external",
          "resource": "keycloak-war",
          "public-client": true,
          "use-resource-role-mappings": true,
          "confidential-port": 0
        }


Use the bundle
--------------

The `keycloak-war` bundle runs as web application and we can simply browse to http://localhost:8181/keycloak-war/info URL.

We will be redirected to Keycloak UI to perform authentication and when it succeeds, we'll get back to http://localhost:8181/keycloak-war/info
to see information about logged-in user. 


Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list -s` command to retrieve your bundle id and symbolic name
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

    or (uninstall by symbolic name)

        bundle:uninstall keycloak-war

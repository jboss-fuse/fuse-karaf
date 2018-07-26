keycloak-httpservice-blueprint: demonstrates a bundle that uses OSGi HTTP Service and Blueprint XML to register servlets with Keycloak authentication
==========================
Author: Fuse Team  
Level: Intermediate  
Technologies: Keycloak, PAX-WEB, Blueprint
Summary: a bundle that uses OSGi HTTP Service and Blueprint XML to register servlets with Keycloak authentication
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>


What is it?
-----------
This quickstart demonstrates how to create an OSGi bundle that uses Blueprint XML approach to register servlets
protected by Keycloak authentication mechanisms.


System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.5.0 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

To build the quick start:

1. Change your working directory to `keycloak-httpservice-blueprint` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands to install required Keycloak features

        feature:repo-add mvn:org.keycloak/keycloak-osgi-features/${version.org.keycloak}/xml/features
        feature:install -v keycloak-pax-http-undertow

5. In the Red Hat Fuse console, enter the following command to install `keycloak-httpservice-blueprint` quickstart:

        install -s mvn:org.jboss.fuse.quickstarts.security/keycloak-httpservice-blueprint/${project.version}

6. There's no need to create a Keycloak configuration inside `etc/` directory, as with this quickstart it's embedded
inside the bundle.


Use the bundle
--------------

We can simply browse to http://localhost:8181/app2/info URL

We will be redirected to Keycloak UI to perform authentication and when it succeeds, we'll get back to initial URL.


Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list -s` command to retrieve your bundle id and symbolic name
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

    or (uninstall by symbolic name)

        bundle:uninstall keycloak-httpservice-blueprint

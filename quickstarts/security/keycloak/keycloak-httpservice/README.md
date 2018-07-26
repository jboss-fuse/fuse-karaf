keycloak-httpservice: demonstrates a bundle that uses OSGi HTTP Service to register servlets with Keycloak authentication
==========================
Author: Fuse Team  
Level: Intermediate  
Technologies: Keycloak, PAX-WEB
Summary: a bundle that uses OSGi HTTP Service to register servlets with Keycloak authentication
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>


What is it?
-----------
This quickstart demonstrates how to create an OSGi bundle that uses Java-code approach to register servlets
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

1. Change your working directory to `keycloak-httpservice` directory.
2. Run `mvn clean install -P<profile-name>` to build the quickstart. As `<profile-name>` you can use one of:

    * `httpservice-default` - the servlet will be registered in default context (`/`)
    * `httpservice-named` - the servlet will be registered in named context (`/app1`)

3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands to install required Keycloak features

        feature:repo-add mvn:org.keycloak/keycloak-osgi-features/${version.org.keycloak}/xml/features
        feature:install -v keycloak-pax-http-undertow

5. In the Red Hat Fuse console, enter the following command to install `keycloak-httpservice` quickstart:

        install -s mvn:org.jboss.fuse.quickstarts.security/keycloak-httpservice/${project.version}

6. Inside Fuse directory, create file `etc/info-keycloak.json` **and** `etc/logout-keycloak.json`
(when using `httpservice-default` profile) or just `etc/app1-keycloak.json` (when using `httpservice-named` profile)
with the following content configuring Keycloak integration:

        {
          "realm": "fuse7karaf",
          "auth-server-url": "http://localhost:8180/auth",
          "ssl-required": "external",
          "resource": "hs-info",
          "public-client": true,
          "use-resource-role-mappings": true,
          "confidential-port": 0,
          "principal-attribute": "preferred_username"
        }

    The reason for a need to have both `etc/info-keycloak.json` and `etc/logout-keycloak.json` files is that when
    servlets are registered in default context (`/`), first path segment is used to distinguish between Keycloak
    configurations.


Use the bundle
--------------

We can simply browse to:

* http://localhost:8181/info URL when using `httpservice-default` profile
* http://localhost:8181/app1/info URL when using `httpservice-named` profile

We will be redirected to Keycloak UI to perform authentication and when it succeeds, we'll get back to initial URL.


Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list -s` command to retrieve your bundle id and symbolic name
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

    or (uninstall by symbolic name)

        bundle:uninstall keycloak-httpservice

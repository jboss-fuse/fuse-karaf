security/keycloak: Various quickstart projects showing Keycloak usage with Fuse
======================================================
Author: Fuse Team  
Level: Intermediate
Technologies: Keycloak, PAX-WEB, Camel, CXF, Blueprint
Summary: This directory contains various quickstart projects show how to integrate Camel, CXF and Blueprint with Keycloak  
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>  

The following quickstarts are provided out of the box:

* [keycloak-war]() - a WAR that uses `KEYCLOAK` authentication method
* [keycloak-httpservice]() - a bundle that uses OSGi HTTP Service to register servlets with Keycloak authentication
* [keycloak-httpservice-blueprint]() - a bundle that uses OSGi HTTP Service and Blueprint XML to register servlets with Keycloak authentication
* [keycloak-whiteboard]() - a bundle that uses PAX-WEB HTTP Whiteboard Service to register servlets with Keycloak authentication
* [keycloak-whiteboard-blueprint]() - a bundle that uses PAX-WEB HTTP Whiteboard Service and blueprint XML to register servlets with Keycloak authentication
* [keycloak-camel-blueprint]() - a bundle with Camel route that uses `undertow-keycloak` component
* [keycloak-camel-restdsl-blueprint]() - a bundle with Camel route that uses `undertow-keycloak` component and REST DSL
* [keycloak-cxf]() - a bundle that registers CXF endpoints that are protected using Keycloak

All the quickstarts require running Keycloak server version ${version.org.keycloak}:

    $ pwd
    /data/servers/keycloak-${version.org.keycloak}
    $ bin/standalone.sh -Djboss.socket.binding.port-offset=100
    ...
    12:00:04,279 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: Keycloak ${version.org.keycloak} started in 10242ms - Started 545 of 881 services (604 services are lazy, passive or on-demand)

And we need to import `security/keycloak/etc/fuse7karaf-realm-export.json` file using Keycloak Admin UI.

After import, we will have several Keycloak _clients_ configured and sample user called `admin` with `passw0rd` password.
We will be using these credentials in all the examples.

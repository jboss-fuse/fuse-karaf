keycloak-camel-restdsl-blueprint: demonstrates a bundle with Camel route that uses `undertow-keycloak` component and REST DSL
==========================
Author: Fuse Team  
Level: Intermediate  
Technologies: Keycloak, PAX-WEB, Blueprint, Camel
Summary: a bundle with Camel route that uses `undertow-keycloak` component and REST DSL
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/7.1.x.redhat-7-1-x/quickstarts/security/keycloak>


What is it?
-----------
This quickstart demonstrates how to create an OSGi bundle that starts Camel route using `undertow-keycloak` component
and uses Camel REST DSL.


System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.5.0 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

To build the quick start:

1. Change your working directory to `keycloak-camel-restdsl-blueprint` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands to install required Keycloak features

        feature:repo-add mvn:org.keycloak/keycloak-osgi-features/${version.org.keycloak}/xml/features
        feature:install -v keycloak-pax-http-undertow

5. In the Red Hat Fuse console, enter the following command to install `keycloak-camel-restdsl-blueprint` quickstart:

        install -s mvn:org.jboss.fuse.quickstarts.security/keycloak-camel-restdsl-blueprint/${project.version}

6. There's no need to create a Keycloak configuration inside `etc/` directory, as with this quickstart it's embedded
inside the bundle.


Use the bundle
--------------

After installing `keycloak-camel-restdsl-blueprint` bundle, we'll have a Camel route exposing an endpoint that
expectes `Bearer` authentication. This means we need valid OAuth2 token to authenticate.

The quickstart includes `org.jboss.fuse.quickstarts.security.keycloak.camel.CamelClientTest` unit test that shows
(in Java code) the OAuth2 flow required to securely connect to and endpoint exposed by Camel route.

The unit test can be run inside `keycloak-camel-restdsl-blueprint` directory:

    $ mvn test -Pqtest
    [INFO] Scanning for projects...
    [INFO] 
    [INFO] --< org.jboss.fuse.quickstarts.security:keycloak-camel-restdsl-blueprint >--
    [INFO] Building Red Hat Fuse :: Quickstarts :: Security :: Keycloak :: Camel REST DSL/Blueprint ${project.version}
    [INFO] -------------------------------[ bundle ]-------------------------------
    [INFO] 
    [INFO] --- maven-resources-plugin:3.0.2:resources (default-resources) @ keycloak-camel-restdsl-blueprint ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 2 resources
    [INFO] 
    [INFO] --- maven-compiler-plugin:3.7.0:compile (default-compile) @ keycloak-camel-restdsl-blueprint ---
    [INFO] Nothing to compile - all classes are up to date
    [INFO] 
    [INFO] --- maven-bundle-plugin:3.5.1:manifest (bundle-manifest) @ keycloak-camel-restdsl-blueprint ---
    [INFO] 
    [INFO] --- maven-resources-plugin:3.0.2:testResources (default-testResources) @ keycloak-camel-restdsl-blueprint ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 1 resource
    [INFO] 
    [INFO] --- maven-compiler-plugin:3.7.0:testCompile (default-testCompile) @ keycloak-camel-restdsl-blueprint ---
    [INFO] Nothing to compile - all classes are up to date
    [INFO] 
    [INFO] --- maven-surefire-plugin:2.20.1:test (default-test) @ keycloak-camel-restdsl-blueprint ---
    [INFO] 
    [INFO] -------------------------------------------------------
    [INFO]  T E S T S
    [INFO] -------------------------------------------------------
    [INFO] Running org.jboss.fuse.quickstarts.security.keycloak.camel.CamelClientTest
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "POST /auth/realms/fuse7karaf/protocol/openid-connect/token HTTP/1.1[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "Authorization: Basic Y2FtZWwtdW5kZXJ0b3ctcmVzdGRzbC1lbmRwb2ludDo5NGZiOTg4Mi01NzIwLTQwN2QtOWNjOC0xM2Q1Yjk5MjA3ZTQ=[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "Content-Length: 52[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "Content-Type: application/x-www-form-urlencoded[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "Host: localhost:8180[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "Connection: Keep-Alive[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "User-Agent: Apache-HttpClient/4.5.4 (Java/1.8.0_181)[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 >> "grant_type=password&username=admin&password=passw0rd"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "HTTP/1.1 200 OK[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "Connection: keep-alive[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "Set-Cookie: KC_RESTART=; Version=1; Expires=Thu, 01-Jan-1970 00:00:10 GMT; Max-Age=0; Path=/auth/realms/fuse7karaf/; HttpOnly[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "Content-Type: application/json[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "Content-Length: 3713[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "Date: Thu, 26 Jul 2018 11:34:35 GMT[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-0 << "{"access_token":"<token>","expires_in":300,"refresh_expires_in":1800,"refresh_token":"<refreshtoken>","token_type":"bearer","not-before-policy":0,"session_state":"913411d1-4a57-4b38-b973-5066c91bfc26","scope":"profile email"}"
    13:34:35 INFO [org.jboss.fuse.quickstarts.security.keycloak.camel.CamelClientTest] : token: <token>
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "GET /restdsl/info HTTP/1.1[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "Authorization: Bearer <token>"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "Host: localhost:8484[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "Connection: Keep-Alive[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "User-Agent: Apache-HttpClient/4.5.4 (Java/1.8.0_181)[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 >> "[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "HTTP/1.1 200 OK[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "Connection: keep-alive[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "Content-Length: 40[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "Date: Thu, 26 Jul 2018 11:34:35 GMT[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "[\r][\n]"
    13:34:35 DEBUG [org.apache.http.wire] : http-outgoing-1 << "Hello admin! Your full name is John Doe."
    13:34:35 INFO [org.jboss.fuse.quickstarts.security.keycloak.camel.CamelClientTest] : response: Hello admin! Your full name is John Doe.
    [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.138 s - in org.jboss.fuse.quickstarts.security.keycloak.camel.CamelClientTest
    [INFO] 
    [INFO] Results:
    [INFO] 
    [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
    [INFO] 
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time: 3.910 s
    [INFO] Finished at: 2018-07-26T13:34:35+02:00
    [INFO] ------------------------------------------------------------------------


Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list -s` command to retrieve your bundle id and symbolic name
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

    or (uninstall by symbolic name)

        bundle:uninstall keycloak-camel-restdsl-blueprint

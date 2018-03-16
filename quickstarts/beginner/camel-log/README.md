camel-log: Demonstrates how to use logging with Camel
======================================================
Author: Fuse Team  
Level: Beginner  
Technologies: Camel  
Summary: This quickstart shows a simple Apache Camel application that logs a message to the server log every 5th second.  
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>  

What is it?
-----------

This quick start shows how to create a simple Apache Camel application that logs a message to the server log every 5th second.  
This example is implemented using solely the XML DSL (there is no Java code). The source code is provided in the following XML file `src/main/resources/OSGI-INF/blueprint/camel-log.xml`.

System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------

1. Change your working directory to `camel-log` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/beginner-camel-log/${project.version}
5. Fuse should give you an id when the bundle is deployed
6. You can check that everything is ok by issuing the command:

        bundle:list
   your bundle should be present at the end of the list

Use the bundle
---------------------

To use the application be sure to have deployed the quickstart in Fuse as described above.

1. At the fuse prompt, enter the following command: log:tail
2. Every 5 seconds you will see a message containing ">>> Hello from Fuse based Camel route!"
3. Hit ctrl-c to return to the fuse prompt.

Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

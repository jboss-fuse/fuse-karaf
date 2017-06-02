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

* Maven 3.1.1 or higher
* JDK 1.7 or 1.8
* JBoss Fuse 7


Build and Deploy the Quickstart
-------------------------

1. Change your working directory to `camel-log` directory.
* Run `mvn clean install` to build the quickstart.
* Start JBoss Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
* In the JBoss Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/beginner-camel-log/${project.version}

* Fuse should give you an id when the bundle is deployed

* You can check that everything is ok by issuing the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
---------------------

To use the application be sure to have deployed the quickstart in Fuse as described above. 

1. At the fuse prompt, enter the following command: log:tail
2. Every 5 seconds you will see a message containing ">>> Hello from Fabric based Camel route!"
3. Hit ctrl-c to return to the fuse prompt.

Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

### Using the web console

You can deploy and run this example from the web console, as follows

1. It is assumed that you have already created a fabric and are logged into a container called `root`.
1. Login the web console
1. Click the Wiki button in the navigation bar
1. Select `quickstarts` --> `beginner` --> `camel.log`
1. Click the `New` button in the top right corner
1. In the Create New Container page, enter `mychild` in the Container Name field, and click the *Create and start container* button

## How to try this example
This example comes with sample data which you can use to try this example

1. Login the web console
1. Click the Containers button in the navigation bar
1. Select the `mychild` container in the containers list, and click the *open* button right next to the container name.
1. A new window opens and connects to the container. Click the *Logs* button in the navigation bar if the logs are not already displayed
1. You can also click the *Camel* button in the top navigation bar, to see information about the Camel application. 

## Undeploy this example

To stop and undeploy the example in fabric8:

1. In the web console, click the *Runtime* button in the navigation bar.
1. Select the `mychild` container in the *Containers* list, and click the *Stop* button in the top right corner

camel-cbr: Demonstrates the Camel CBR Pattern
======================================================
Author: Fuse Team  
Level: Beginner  
Technologies: Camel, Blueprint, ActiveMQ  
Summary: This quickstart demonstrates how to use Apache Camel to route messages using the Content Based Router (cbr) pattern.
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>



What is it?
-----------

This quick start shows how to use Apache Camel, and its OSGi integration to dynamically route messages to new or updated OSGi bundles. This allows you to route to newly deployed services at runtime without impacting running services.

This quick start combines use of the Camel Recipient List, which allows you to at runtime specify the Camel Endpoint to route to, and use of the Camel VM Component, which provides a SEDA queue that can be accessed from different OSGi bundles running in the same Java virtual machine.

In studying this quick start you will learn:

* how to define a Camel route using the Blueprint XML syntax
* how to build and deploy an OSGi bundle in Red Hat Fuse
* how to use the CBR enterprise integration pattern

This example picks up incoming XML files, and depending on the content of the XML files, they are routed to different endpoints, as shown in figure below.

![Camel CBR diagram](https://raw.githubusercontent.com/jboss-fuse/fabric8/1.2.0.redhat-6-3-x/docs/images/camel-cbr-diagram.jpg)

For more information see:

* http://www.enterpriseintegrationpatterns.com/ContentBasedRouter.html for more information about the CBR EIP
* https://access.redhat.com/documentation/red-hat-jboss-fuse for more information about using Red Hat Fuse

Note: Extra steps, like use of Camel VM Component, need to be taken when accessing Camel Routes in different Camel Contexts, and in different OSGi bundles, as you are dealing with classes in different ClassLoaders.


System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------

1. Change your working directory to `camel-cbr` directory.
* Run `mvn clean install` to build the quickstart.
* Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
* In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/beginner-camel-cbr/${project.version}

* Fuse should give you an id when the bundle is deployed

* You can check that everything is ok by issuing the command:

        bundle:list

   your bundle should be present at the end of the list


Use the bundle
---------------------

To use the application be sure to have deployed the quickstart in Fuse as described above. 

1. As soon as the Camel route has been started, you will see a directory `work/cbr/input` in your Red Hat Fuse installation.
2. Copy the files you find in this quick start's `src/main/fuse/data` directory to the newly created `work/cbr/input`
directory.
3. Wait a few moments and you will find the same files organized by country under the `work/cbr/output` directory.
  * `order1.xml` in `work/cbr/output/others`
  * `order2.xml` and `order4.xml` in `work/cbr/output/uk`
  * `order3.xml` and `order5.xml` in `work/cbr/output/us`
4. Use `log:display` to check out the business logging.
        Receiving order order1.xml
        Sending order order1.xml to another country
        Done processing order1.xml


Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>
 

camel-errorhandler: demonstrates how to handle exceptions in Camel.
===================================
Author: Fuse Team  
Level: Beginner  
Technologies: Fuse, OSGi, Camel  
Summary: This quickstart demonstrates how to handle exceptions that can occur while routing messages with Camel.
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>

What is it?
-----------

This quickstart demonstrates how to handle exceptions that occur while routing messages with Camel.

This quickstart show you how to add a default error handler to your Camel context for all uncaught exceptions.
Additionally, it will show you how to add exception handling routines for dealing with specific exception types.

In studying this quick start you will learn:

* how to define a Camel route using the Blueprint XML syntax
* how to build and deploy an OSGi bundle in Red Hat Fuse
* how to define a default error handler to your Camel context
* how to define exception-specific error handling routines

This example picks up XML files, and depending on the error that occurs during processing, they are routed to different endpoints, as shown in figure below.

![Camel DLC diagram](https://raw.githubusercontent.com/jboss-fuse/fabric8/1.2.0.redhat-6-3-x/docs/images/camel-errorhandler-diagram.jpg)   

For more information see:

* http://www.enterpriseintegrationpatterns.com/DeadLetterChannel.html for the Dead Letter Channel EIP
* https://access.redhat.com/documentation/en-us/red-hat-fuse for more information about using Red Hat Fuse


System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

1. Change your working directory to `camel-errorhandler` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/beginner-camel-errorhandler/${project.version}

5. Fuse should give you an id when the bundle is deployed
6. You can check that everything is ok by issuing  the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
--------------

To use the application be sure to have deployed the quickstart in Fuse as described above. Successful deployment will create and start a Camel route in Fuse.

1. As soon as the Camel route has been started, you will see a directory `work/errors/input` in your Red Hat Fuse installation.
2. Copy the file you find in this quick start's `src/main/fuse/data` directory to the newly created
`work/errors/input` directory.
4. Wait a few moments and you will find the files in directories under `work/errors`:
  * `order4.xml` will always end up in the `work/errors/validation` directory
  * other files will end up in `work/errors/done` or `work/errors/deadletter` depending on the runtime exceptions that occur
5. Use `log:display` to check out the business logging - the exact output may look differently because the 'unexpected runtime exception...' happen randomly

        Processing order4.xml
        Order validation failure: order date 2017-03-04 should not be a Sunday
        Validation failed for order4.xml - moving the file to work/errors/validation
        Processing order5.xml
        An unexcepted runtime exception occurred while processing order5.xml
        Done processing order5.xml
        ...

Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

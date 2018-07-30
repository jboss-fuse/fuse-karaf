camel-eips: Demonstrates how to combine multiple EIPS in Camel
===================================
Author: Fuse Team  
Level: Beginner  
Technologies: Camel,Blueprint  
Summary: This quickstart demonstrates how to combine multiple EIPs in Camel in order to solve integration problems.
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>

What is it?
-----------

This quickstart demonstrates how to combine multiple EIPs to solve integration problems.

In this example, an orders file containing several orders for zoos around the world is sent to us.

We first want to make sure we retain a copy of the original file. This is done using the Wiretap EIP.

After saving the original, we want to split the file up into the individual orders. This is done using the Splitter EIP.

Then we want to store the orders in separate directories by geographical region. This is done using a Recipient List EIP.

Finally, we want to filter out the orders that contain more than 100 animals and generate a message for the strategic account team. This is done using a Filter EIP, as shown in the figure below.

![Camel EIP diagram](https://raw.githubusercontent.com/jboss-fuse/fabric8/1.2.0.redhat-6-3-x/docs/images/camel-eips-diagram.jpg)



In studying this example you will learn:

* how to define a Camel route using the Blueprint XML syntax
* how to build and deploy an OSGi bundle in Red Hat Fuse
* how to combine multiple Enterprise Integration Patterns to create an integration solution
* how to use the Wiretap EIP to copy messages as they pass through a route
* how to use the Splitter EIP to split large messages into smaller ones
* how to use a Recipient List EIP to dynamically determine how a message passes through a route
* how to use the Filter EIP to filter messages and execute logic for the ones that match the filter
* how to define and use a bean to process a message
* how to use a `direct:` endpoint to link multiple smaller routes together


For more information see:

* http://www.enterpriseintegrationpatterns.com/RecipientList.html
* http://www.enterpriseintegrationpatterns.com/WireTap.html
* http://www.enterpriseintegrationpatterns.com/Filter.html
* http://www.enterpriseintegrationpatterns.com/Sequencer.html
* https://access.redhat.com/documentation/en-us/red-hat-fuse for more information about using Red Hat Fuse


System requirements
-------------------

Before building and running this example you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

1. Change your working directory to `camel-eips` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/beginner-camel-eips/${project.version}

5. Fuse should give you an id when the bundle is deployed
6. You can check that everything is ok by issuing  the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
--------------

To use the application be sure to have deployed the quickstart in Fuse as described above. Successful deployment will create and start a Camel route in Fuse.

1. As soon as the Camel route has been started, you will see a directory `work/eip/input` in your Red Hat Fuse installation.
2. Copy the file you find in this example's `src/main/fuse/data` directory to the newly created `work/eip/input`
directory.
3. Wait a few moments and you will find multiple files organized by geographical region under `work/eip/output`:
 * `2017_0003.xml` and `2017_0005.xml` in `work/eip/output/AMER`
 * `2017_0020.xml` in `work/eip/output/APAC`
 * `2017_0001.xml`, `2017_0002.xml` and `2017_0004.xml` in `work/eip/output/EMEA`
4. Use `log:display` on the ESB shell to check out the business logging.
        [main]    Processing orders.xml
        [wiretap]  Archiving orders.xml
        [splitter] Shipping order 2017_0001 to region EMEA
        [splitter] Shipping order 2017_0002 to region EMEA
        [filter]   Order 2017_0002 is an order for more than 100 animals
        ...

Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

camel-hl7: Demonstrates the camel-hl7 component
===========
Author: Fuse QE Team
Level: Beginner
Technologies: Camel, Blueprint
Summary: This quickstart demonstrates how to use the camel-hl7 component in Camel.
Target Product: Fuse
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts/camel/camel-hl7>

Requirements
------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7

Build and Deploy the Quickstart
-------------------------------

1. Change your working directory to `camel-hl7` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following commands:

       feature:install camel-hl7 camel-netty4 camel-groovy
       bundle:install -s mvn:org.jboss.fuse.quickstarts/camel-hl7/${project.version}

5. Fuse should give you an id when the bundle is deployed
6. You can check that everything is ok by issuing  the command:

       bundle:list
   your bundle should be present at the end of the list


7. To see what is happening within the Red Hat Fuse server, you can continuously view the log (tail) with the following command

       log:tail

File Based Test
---------------

1. As soon as the Camel route has been started, you will see a directory `work/camel-hl7/input` in your Red Hat Fuse installation.
2. Copy the files you find in this quickstart's `src/main/resources/data` directory to the newly created `work/camel-hl7/input`
directory. There are two example files - valid.hl7 and invalid.hl7
3. If the message is valid, you will see successful response in log:

       HL7 Response: MSH|^~\&|EKG|EKG|HIS|RIH|20171213145154.359+0100||ACK^A01|202|P|2.2

4. If the message is invalid, HL7Exception will be logged:

       Caused by: ca.uhn.hl7v2.HL7Exception: Invalid or incomplete encoding characters - MSH-2 is ^~\\&
       	at ca.uhn.hl7v2.parser.PipeParser.getVersion(PipeParser.java:1081) ~[225:ca.uhn.hapi.osgi-base:2.2.0]
       	at ca.uhn.hl7v2.parser.GenericParser.getVersion(GenericParser.java:191) ~[225:ca.uhn.hapi.osgi-base:2.2.0]
       	...

TCP Based Test
--------------

1. Change your working directory to `camel-hl7/src/main/resources/scripts`, you will find helper scripts to send message to TCP endpoint.
2. To test valid file on UNIX, run:

       ./send.sh ../data/valid.hl7 localhost 8888

Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>

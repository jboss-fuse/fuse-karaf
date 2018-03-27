camel-odata: Demonstrates the camel-olingo4 component
======================================================
Author: Fuse Team  
Level: Beginner  
Technologies: Camel, Blueprint, JBoss Data Virtualization  
Summary: Demonstrates how to use the camel-olingo4 component in Camel to integrate with the sample
OData 4.0 remote TripPinservice published on http://services.odata.org/TripPinRESTierService by creating
two People who's data are loaded from a directory.  
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts/camel-odata>  



What is it?
-----------

This quick start shows how to use Apache Camel, and its OSGi integration to create records in the TripPinservice.

This quick start combines use of the Camel File consumer to read records from json files, and Camel Olingo4 component
to create records in the TripPinservice.

In studying this quick start you will learn:

* how to define a Camel route using the Blueprint XML syntax
* how to build and deploy an OSGi bundle in Red Hat Fuse
* how to use read a file and convert it to a String
* how to use the Camel Olingo4 component for OData

For more information see:

[comment]: <> (TODO Update to Fuse 7 docs once they are available)

* https://access.redhat.com/documentation/en-us/red_hat_jboss_fuse/6.3/html/apache_camel_component_reference/idu-olingo2 for more information about the Camel Olingo2 component
* https://access.redhat.com/documentation/red-hat-jboss-fuse for more information about using Red Hat Fuse

System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7

Build and Deploy the Quickstart
-------------------------

* Change your working directory to `camel-odata` directory.
* Run `mvn clean install` to build the quickstart.
* Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
* In the Red Hat Fuse console, enter the following commands:

        feature:install camel-olingo4
        bundle:install -s mvn:org.jboss.fuse.quickstarts/camel-odata/${project.version}

* Fuse should give you an id when the bundle is deployed

* You can check that everything is ok by issuing  the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
---------------------

To use the application be sure to have deployed the quickstart in Fuse as described above. 

1. As soon as the Camel route has been started, you will see a directory `work/odata/input` in your Red Hat Fuse installation.
2. Copy the files you find in this quick start's `src/main/resources/data` directory to the newly created `work/odata/input`
directory.
3. Use `log:display` to check out the business logging.

```
2017-11-29 15:46:22,524 | INFO  | nt Dispatcher: 1 | BlueprintCamelContext            | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Apache Camel 2.21.0-SNAPSHOT (CamelContext: odata4-example-context) started in 0.102 seconds
2017-11-29 15:46:23,528 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Receiving file person2.json
2017-11-29 15:46:23,528 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Sending file person2.json to OData Test Service
2017-11-29 15:46:24,317 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Receiving file person1.json
2017-11-29 15:46:24,317 | INFO  | work/odata/input | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Sending file person1.json to OData Test Service
2017-11-29 15:46:24,665 | INFO  | I/O dispatcher 1 | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Done creating person with properties [ClientPropertyImpl{name=UserName, value=jdoe, annotations=[]}, ClientPropertyImpl{name=FirstName, value=John, annotations=[]}, ClientPropertyImpl{name=LastName, value=Doe, annotations=[]}, ClientPropertyImpl{name=MiddleName, value=, annotations=[]}, ClientPropertyImpl{name=Gender, value=Male, annotations=[]}, ClientPropertyImpl{name=Age, value=, annotations=[]}, ClientPropertyImpl{name=Emails, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=FavoriteFeature, value=Feature1, annotations=[]}, ClientPropertyImpl{name=Features, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=AddressInfo, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=HomeAddress, value=, annotations=[]}]
2017-11-29 15:46:24,689 | INFO  | I/O dispatcher 2 | odata-route                      | 62 - org.apache.camel.camel-core - 2.21.0.SNAPSHOT | Done creating person with properties [ClientPropertyImpl{name=UserName, value=jmorrow, annotations=[]}, ClientPropertyImpl{name=FirstName, value=Jerome, annotations=[]}, ClientPropertyImpl{name=LastName, value=Morrow, annotations=[]}, ClientPropertyImpl{name=MiddleName, value=, annotations=[]}, ClientPropertyImpl{name=Gender, value=Male, annotations=[]}, ClientPropertyImpl{name=Age, value=, annotations=[]}, ClientPropertyImpl{name=Emails, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=FavoriteFeature, value=Feature1, annotations=[]}, ClientPropertyImpl{name=Features, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=AddressInfo, value=ClientCollectionValueImpl [values=[]super[AbstractClientValue [typeName=null]]], annotations=[]}, ClientPropertyImpl{name=HomeAddress, value=, annotations=[]}]
```

Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

`uninstall camel-example-olingo4-blueprint`

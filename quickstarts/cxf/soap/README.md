soap: demonstrates a SOAP web service with Apache CXF
==========================
Author: Fuse Team  
Level: Beginner  
Technologies: Fuse, OSGi, CXF  
Summary: This quickstart demonstrates how to create a SOAP Web service with Apache CXF and expose it through the OSGi HTTP Service.
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>


What is it?
-----------
This quick start demonstrates how to create a SOAP Web service with Apache CXF and expose it through the OSGi HTTP Service.

In studying this quick start you will learn:

* how to configure JAX-WS Web services by using the blueprint configuration file
* how to configure additional CXF features like logging
* how to use standard Java Web Service annotations to define a Web service interface
* how to use standard Java Web Service annotations when implementing a Web service in Java
* how to use CXF's `JaxWsProxyFactoryBean` to create a client side proxy to invoke a remote Web service

For more information see:

* https://access.redhat.com/documentation/red-hat-jboss-fuse for more information about using Red Hat Fuse

System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7


Build and Deploy the Quickstart
-------------------------------

To build the quick start:

1.Change your working directory to `soap` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/cxf-soap/${project.version}

5. Fuse should give you on id when the bundle is deployed
6. You can check that everything is ok by issue the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
--------------

There are several ways you can interact with the running web services: you can browse the web service metadata,
but you can also invoke the web services in a few different ways.


### Browsing web service metadata

A full listing of all CXF web services is available at

    http://localhost:8181/cxf

After you deployed this quick start, you will see the 'HelloWorld' service appear in the 'Available SOAP Services' section,
together with a list of operations for the endpoint and some additional information like the endpoint's address and a link
to the WSDL file for the web service:

    http://localhost:8181/cxf/HelloWorld?wsdl

You can also use "cxf:list-endpoints" in Fuse to check the state of all CXF web services like this 

    Fuse:karaf@root> cxf:list-endpoints
    
    |Name                |  State  |  Address    |       BusID                                      |
    |:------------------:|:-------:|:-----------:|:------------------------------------------------:|
    | HelloWorldImplPort | Started | /HelloWorld | org.jboss.fuse.quickstarts.cxf-soap-cxf278553749 |
    
    

### To run a Web client:

You can use an external tool such as SoapUI to test web services.


### To run the test:

In this cxf-jaxws quistart, we also provide an integration test which can perform a few HTTP requests to test our web services. We
created a Maven `test` profile to allow us to run tests code with a simple Maven command after having deployed the bundle to Fuse:

1. Change to the `soap` directory.
2. Run the following command:

        mvn -Ptest

    The test sends the contents of the request.xml sample SOAP request file to the server and afterwards display the response
    message:

        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
          <soap:Body>
            <ns2:sayHiResponse xmlns:ns2="http://soap.fuse.quickstarts.jboss.org/">
              <return>Hello John Doe</return>
            </ns2:sayHiResponse>
          </soap:Body>
        </soap:Envelope>


### Changing /cxf servlet alias

By default CXF Servlet is assigned a '/cxf' alias. You can change it in a couple of ways

1. Add org.apache.cxf.osgi.cfg to the /etc directory and set the 'org.apache.cxf.servlet.context' property, for example:

        org.apache.cxf.servlet.context=/custom
   
   In this way, Red Hat Fuse will load the cfg when the CXF Servlet is reloaded, you can restart the CXF bundle to load the change.

2. Use shell config commands, for example:

        config:edit org.apache.cxf.osgi
        config:property-set org.apache.cxf.servlet.context /custom
        config:update

    Red Hat Fuse will create org.apache.cxf.osgi.cfg file in the /etc directory and and set the entry as we did in the first way after the commands are run, you need to restart the CXF bundle to load the change.
    
Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>


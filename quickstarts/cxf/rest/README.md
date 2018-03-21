rest: Demonstrates how to create a REST Web service
===============================================
Author: Fuse Team  
Level: Beginner  
Technologies: Fuse, OSGi, CXF  
Summary: This quickstart demonstrates how to create a RESTful (JAX-RS) web service using CXF and expose it through the OSGi HTTP Service.
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>

What is it?
-----------
This quick start demonstrates how to create a RESTful (JAX-RS) web service using CXF and expose it with the OSGi HTTP Service.

In studying this quick start you will learn:

* how to configure the JAX-RS web services by using the blueprint configuration file.
* how to use JAX-RS annotations to map methods and classes to URIs
* how to use JAXB annotations to define beans and output XML responses
* how to use the JAX-RS API to create HTTP responses

For more information see:

* <https://access.redhat.com/documentation/red-hat-jboss-fuse> for more information about using Red Hat Fuse

System requirements
-------------------
Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7

Build and Deploy the Quickstart
-------------------------------

1. Change your working directory to `rest` directory.
2. Run `mvn clean install` to build the quickstart.
3. Start Red Hat Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
4. In the Red Hat Fuse console, enter the following command:

        bundle:install -s mvn:org.jboss.fuse.quickstarts/cxf-rest/${project.version}

5. Fuse should give you an id when the bundle is deployed
6. You can check that everything is ok by issuing  the command:

        bundle:list
   your bundle should be present at the end of the list


Use the bundle
--------------

### Browsing Web service metadata

A full listing of all CXF web services is available at

    http://localhost:8181/cxf

After you deployed this quick start, you will see the following endpoint address appear in the 'Available RESTful services' section:

    http://localhost:8181/cxf/crm
**Note:**: Don't try to access this endpoint address from browser, as it's inaccessible by design

Just below it, you'll find a link to the WADL describing all the root resources:

    http://localhost:8181/cxf/crm?_wadl

You can also look at the more specific WADL, the only that only lists information about 'customerservice' itself:

	http://localhost:8181/cxf/crm/customerservice?_wadl&_type=xml

### Access services using a web browser

You can use any browser to perform a HTTP GET.  This allows you to very easily test a few of the RESTful services we defined:

Use this URL to display the XML representation for customer 123:

    http://localhost:8181/cxf/crm/customerservice/customers/123

You can also access the XML representation for order 223 ...

    http://localhost:8181/cxf/crm/customerservice/orders/223

... or the XML representation of product 323 in order 223 with

    http://localhost:8181/cxf/crm/customerservice/orders/223/products/323

**Note:** if you use Safari, you will only see the text elements but not the XML tags - you can view the entire document with 'View Source'

### To run the tests:

In this quick start project, we also provide integration tests which perform a few HTTP requests to test our Web services. We
created a Maven `test` profile to allow us to run tests code with a simple Maven command after having deployed the bundle to Fuse:

1. Change to the `rest` directory.
2. Run the following command:

        mvn -Ptest
        
The tests in `src/test/java/org.jboss.fuse.quickstarts.rest/CrmTest`  make a sequence of RESTful invocations and displays the results.

### To run a command-line utility:

You can use a command-line utility, such as cURL or wget, to perform the HTTP requests.  We have provided a few files with sample XML representations in `src/test/resources`, so we will use those for testing our services.

1. Open a command prompt and change directory to `rest`.
2. Run the following curl commands (curl commands may not be available on all platforms):
    
    * Create a customer
 
            curl -X POST -T src/test/resources/add_customer.xml -H "Content-Type: application/xml" http://localhost:8181/cxf/crm/customerservice/customers
  
    * Retrieve the customer instance with id 123
    
            curl http://localhost:8181/cxf/crm/customerservice/customers/123

    * Update the customer instance with id 123
  
            curl -X PUT -T src/test/resources/update_customer.xml -H "Content-Type: application/xml" http://localhost:8181/cxf/crm/customerservice/customers

    * Delete the customer instance with id 123
  
             curl -X DELETE http://localhost:8181/cxf/crm/customerservice/customers/123


### Changing /cxf servlet alias

By default CXF Servlet is assigned a '/cxf' alias. You can change it in a couple of ways

1. Add `org.apache.cxf.osgi.cfg` to the `/etc` directory and set the `org.apache.cxf.servlet.context` property, for example:

        org.apache.cxf.servlet.context=/custom

2. Use shell config commands, for example:

        config:edit org.apache.cxf.osgi
        config:property-set org.apache.cxf.servlet.context /custom
        config:update

Undeploy the Bundle
-------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>


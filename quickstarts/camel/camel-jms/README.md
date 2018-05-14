camel-jms: Demonstrates how to use the camel-jms component
======================================================
Author: Fuse Team  
Level: Beginner  
Technologies: Camel, AMQ 7  
Summary: This quickstart demonstrates how to use the camel-jms component to connect to an AMQ 7 broker and use JMS messaging between two Camel routes.  
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts/camel/camel-jms>  


What is it?
-----------
In this quickstart, orders from zoos all over the world will be copied from the input directory into a specific
output directory per country. We will use two Camel routes to send and receive orders to an AMQ 7 Broker.

System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* JBoss Fuse 7
* AMQ Broker 7 running

AMQ Broker 7
-------------------

This quickstart assumes you have an AMQ 7 broker running. The installation and configuration of the AMQ 7 Broker is beyond the scope of this quickstart. For more information you can
visit the AMQ 7 [documentation](https://access.redhat.com/documentation/en-us/red_hat_jboss_amq/7.0/html/using_amq_broker/) 


Build and Deploy the Quickstart
-------------------------

1. Change your working directory to `camel-jms` directory.
* Run `mvn clean install` to build the quickstart.
* Start JBoss Fuse 7 by running bin/fuse (on Linux) or bin\fuse.bat (on Windows).
* Copy the file `org.ops4j.connectionfactory-amq7.cfg` you find in this quickstart's `src/main/resources/etc` directory
 to the `etc` directory. In your  JBoss Fuse installation. Verify it's content for the correct broker URL and 
 credentials. By default, the broker URL is set to tcp://localhost:61616 accepting AMQ 7's CORE protocol.
  Credentials are set to admin/admin.  
* In the JBoss Fuse console, enter the following commands:

        feature:install pax-jms-pool artemis-jms-client pax-jms-artemis camel-blueprint camel-jms 
        install -s mvn:org.jboss.fuse.quickstarts/camel-jms/${project.version}

* Fuse should give you an id when the bundle is deployed

By showing the logs with:

```
karaf@root()> log:display
```

The following messages should be displayed:

```
12:13:50.445 INFO [Blueprint Event Dispatcher: 1] Attempting to start Camel Context jms-example-context
12:13:50.446 INFO [Blueprint Event Dispatcher: 1] Apache Camel 2.21.0.fuse-000030 (CamelContext: jms-example-context) is starting
12:13:50.446 INFO [Blueprint Event Dispatcher: 1] JMX is enabled
12:13:50.528 INFO [Blueprint Event Dispatcher: 1] StreamCaching is not in use. If using streams then its recommended to enable stream caching. See more details at http://camel.apache.org/stream-caching.html
12:13:50.553 INFO [Blueprint Event Dispatcher: 1] Route: file-to-jms-route started and consuming from: file://work/jms/input
12:13:50.555 INFO [Blueprint Event Dispatcher: 1] Route: jms-cbr-route started and consuming from: jms://queue:incomingOrders?transacted=true
12:13:50.556 INFO [Blueprint Event Dispatcher: 1] Total 2 routes, of which 2 are started
```

Use the bundle
--------------------- 

To use the application be sure to have deployed the quickstart in Fuse as described above. 

1. As soon as the Camel routes have started, you will see a directory `work/jms/input` in your JBoss Fuse installation.
2. Copy the files you find in this quickstart's `src/main/data` directory to the newly created `work/jms/input` directory.
3. Wait a few moments and you will find the same files organized by country under the `work/jms/output` directory.
  * `order1.xml`, `order2.xml` and `order4.xml` in `work/jms/output/others`
  * `order3.xml` and `order5.xml` in `work/jms/output/us`
  * `order6.xml` in `work/jms/output/fr`

4. Use `log:display` to check out the business logging:

    ```
    Receiving order order1.xml
    Sending order order1.xml to another country
    Done processing order1.xml
    ```
        
Camel commands can be used to gain some insights on the Camel context, e.g.:

- The `camel:context-list` displays the Camel context:

    ```
    Context               Status              Total #       Failed #     Inflight #   Uptime        
    -------               ------              -------       --------     ----------   ------        
    jms-example-context   Started                  12              0              0   3 minutes  
    ```

- The `camel:route-list` command displays the Camel routes configured:

    ```
     Context               Route               Status              Total #       Failed #     Inflight #   Uptime        
     -------               -----               ------              -------       --------     ----------   ------        
     jms-example-context   file-to-jms-route   Started                   6              0              0   3 minutes    
     jms-example-context   jms-cbr-route       Started                   6              0              0   3 minutes 
    ```

- And the `camel:route-info` command displays the exchange statistics

    ```
    karaf@root()> camel:route-info jms-example-context jms-cbr-route 
    Camel Route jms-cbr-route
        Camel Context: jms-example-context
        State: Started
        State: Started
    
    
    Statistics
        Exchanges Total: 6
        Exchanges Completed: 6
        Exchanges Failed: 0
        Exchanges Inflight: 0
        Min Processing Time: 2 ms
        Max Processing Time: 12 ms
        Mean Processing Time: 4 ms
        Total Processing Time: 29 ms
        Last Processing Time: 4 ms
        Delta Processing Time: 1 ms
        Start Statistics Date: 2018-01-30 12:13:50
        Reset Statistics Date: 2018-01-30 12:13:50
        First Exchange Date: 2018-01-30 12:19:47
        Last Exchange Date: 2018-01-30 12:19:47
    ```

Undeploy the Archive
--------------------

To stop and undeploy the bundle in Fuse:

1. Enter `bundle:list` command to retrieve your bundle id
2. To stop and uninstall the bundle enter

        bundle:uninstall <id>
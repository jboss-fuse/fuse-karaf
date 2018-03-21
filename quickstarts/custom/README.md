custom: Demonstrates how to create a custom assembly
====================================================
Author: Fuse Team  
Level: Intermediate  
Technologies: Red Hat Fuse, Maven
Summary: This quickstart demonstrates how to use Maven to create a custom assembly of Red Hat Fuse
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>  

What is it?
-----------

This quick start shows how to use Apache Maven to create a custom assembly of Red Hat Fuse.

This quick start demonstrates how to create a small, custom assembly. 
The custom assembly created will have a small footprint including just your application and his dependencies.
The `beginner-camel-log` quickstart will be used as an example of an application.

In studying this quick start you will learn:

* how to use Karaf's Features Maven plugin to create a new custom Fuse 7 distro

For more information see:

* https://access.redhat.com/documentation/red-hat-jboss-fuse for more information about using Red Hat Fuse

System requirements
-------------------

Before building and running this quick start you need:

* Maven 3.3.1 or higher
* JDK 1.8
* Red Hat Fuse 7

Build the custom assembly
-------------------------

* Run `mvn clean install` to build the quickstart.
* After the build has finished, you will find the `target/custom-distro-${project.version}.zip` file with the custom assembly.

Run your custom assembly
------------------------

In `target/assembly` there is the unziped version of the custom distro that can be used to quickly locally run it:
1. run `target/assembly/bin/karaf`
2. the custom distro should startup and in Red Hat Fuse command prompt should run `log:tail`
3. a message similar to `| >>> Hello from Fuse based Camel route! :` should be printed every 5 seconds. This is because the custom distro already contains our application (The `beginner-camel-log` quickstart).
4. to exit the command use `Ctrl+C`
5. to stop Red Hat Fuse use `Ctrl+D`, intentionally no `system:*` commands were part of the custom distro so is not possible to use `system:shutdown`.

Customizing the assembly
------------------------

The quick start shows a custom assembly with just a few features enabled. Typically, that list of features needs to be modified to match your own environment or requirement.

The whole custom distro is configured in the pom.xml which is commented out in each section. 
Adding and removing features and bundles is usually done in `<configuration>` section of the `karaf-maven-plugin`. 
Files that you need to include inside the assembly (for example configuration files) can be placed in `src/main/resources/assembly`, as an example an `etc/test-property.cfg` is already included and can be found in the built assembly at `target/assembly/etc/test-property.cfg`.
As an exercise is possible to uncomment some feature in the `<configuration>` section of the `karaf-maven-plugin`, for example `<!--<feature>system</feature>-->`, rebuild the project and see what happens (Will you be able to use the `shutdown` command to stop Red Hat Fuse this time?).

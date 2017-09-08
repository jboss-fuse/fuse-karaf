Fuse Credential Store
=====================

Provides a facility to include passwords and other sensitive strings as masked strings that are resolved from an Wildfly
Elytron Credential store.

The built-in support is for OSGI environment, specifically for Apache Karaf, and for Java system properties.

You might have specified passwords, for instance `javax.net.ssl.keyStorePassword`, as system properties in clear
text this project allows you to specify these values as references to a credential store.

With the Fuse Credential Store installed you can specify those sensitive strings as references to a value stored in
Credential Store, so instead of the clear text value you use an alias reference, for instance `CS:alias` referencing
the value stored under the `alias` in a configured Credential Store.

Getting started on Karaf
------------------------

First you need to build and install the `fuse-credential-store-karaf` to your local Maven repository by runing:

    $ ./mvnw

from this directory.

Next, if you do are adding the credential store support to a new Karaf download and extract
[Apache Karaf distribution](http://karaf.apache.org/download.html).

    $ curl -O http://www.apache.org/dyn/closer.lua/karaf/4.0.8/apache-karaf-4.0.8.tar.gz
    $ tar xf apache-karaf-4.0.8.tar.gz

Change into `apache-karaf-4.0.8` directory and run the `bin/karaf` to startup the container, and then install the
Fuse Credential Store bundle.

    $ cd apache-karaf-4.0.8
    $ bin/karaf
            __ __                  ____      
           / //_/____ __________ _/ __/      
          / ,<  / __ `/ ___/ __ `/ /_        
         / /| |/ /_/ / /  / /_/ / __/        
        /_/ |_|\__,_/_/   \__,_/_/         
    
      Apache Karaf (4.0.8)
    
    Hit '<tab>' for a list of available commands
    and '[cmd] --help' for help on a specific command.
    Hit '<ctrl-d>' or type 'system:shutdown' or 'logout' to shutdown Karaf.
    
    karaf@root()> bundle:install -s mvn:org.jboss.fuse.credential.store/fuse-credential-store-karaf/0.0.1-SNAPSHOT

Next create a credential store using `credential-store:create`:

    karaf@root()> credential-store:create -a location=credential.store -k password="my password" -k algorithm=masked-MD5-DES
    In order to use this credential store set the following environment variables
    Variable                              | Value
    ------------------------------------------------------------------------------------------------------------------------
    CREDENTIAL_STORE_PROTECTION_ALGORITHM | masked-MD5-DES
    CREDENTIAL_STORE_PROTECTION_PARAMS    | MDkEKXNvbWVhcmJpdHJhcnljcmF6eXN0cmluZ3RoYXRkb2Vzbm90bWF0dGVyAgID6AQIsUOEqvog6XI=
    CREDENTIAL_STORE_PROTECTION           | Sf6sYy7gNpygs311zcQh8Q==
    CREDENTIAL_STORE_ATTR_location        | credential.store
    Or simply use this:
    export CREDENTIAL_STORE_PROTECTION_ALGORITHM=masked-MD5-DES
    export CREDENTIAL_STORE_PROTECTION_PARAMS=MDkEKXNvbWVhcmJpdHJhcnljcmF6eXN0cmluZ3RoYXRkb2Vzbm90bWF0dGVyAgID6AQIsUOEqvog6XI=
    export CREDENTIAL_STORE_PROTECTION=Sf6sYy7gNpygs311zcQh8Q==
    export CREDENTIAL_STORE_ATTR_location=credential.store

This should have created `credential.store` file, a JCEKS KeyStore for storing the secrets.

Exit the Karaf container by issuing `logout`:

    karaf@root()> logout

Set the required environment variables presented when creating the credential store:

    $ export CREDENTIAL_STORE_PROTECTION_ALGORITHM=masked-MD5-DES
    $ export CREDENTIAL_STORE_PROTECTION_PARAMS=MDkEKXNvbWVhcmJpdHJhcnljcmF6eXN0cmluZ3RoYXRkb2Vzbm90bWF0dGVyAgID6AQIsUOEqvog6XI=
    $ export CREDENTIAL_STORE_PROTECTION=Sf6sYy7gNpygs311zcQh8Q==
    $ export CREDENTIAL_STORE_ATTR_location=credential.store

Next add your secrets to the credential store by using `credential-store:store`:

    karaf@root()> credential-store:store -a javax.net.ssl.keyStorePassword -s "don't panic"
    Value stored in the credential store to reference it use: CS:javax.net.ssl.keyStorePassword

Exit the Karaf container again by issuing `logout`:

    karaf@root()> logout

And run the Karaf again specifying the reference to your secret instead of the value:

    $ EXTRA_JAVA_OPTS="-Djavax.net.ssl.keyStorePassword=CR:javax.net.ssl.keyStorePassword" bin/karaf

And the value of `javax.net.ssl.keyStorePassword` when accessed using `System::getProperty` should contain the
string `"don't panic"`.

Security
--------

This is password masking, so if the environment variables are leaked outside of your environment or intended use along
with the content of the credential store file, your secretes are compromised. The value of the property when accessed 
through JMX gets replaced with the string `"<sensitive>"`, but do note that there are many code paths that lead to 
`System::getProperty`, for instance diagnostics or monitoring tools might access it along with any 3rd party software
for debugging purposes.

<!--

     Copyright 2005-2017 Red Hat, Inc.

     Red Hat licenses this file to you under the Apache License, version
     2.0 (the "License"); you may not use this file except in compliance
     with the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
     implied.  See the License for the specific language governing
     permissions and limitations under the License.

-->
beginner: Fuse Quickstarts for new Fuse users.
======================================================
Author: Fuse Team  
Level: Beginner  
Technologies: Fuse  
Summary: This directory contains the beginner quickstarts which demonstrate how to use fuse with various technologies.  
Target Product: Fuse  
Source: <https://github.com/jboss-fuse/fuse-karaf/tree/master/quickstarts>  

The following quickstarts are beginner examples that use Apache Camel and which we recommend for first time users

* [camel.cbr](camel-cbr) - a small Camel application using Content Based Router (one of the most common EIP pattern)
* [camel.eips](camel-eips) - demonstrates a number of other commonly used EIP patterns with Apache Camel.
* [camel.errorhandler](camel-errorhandler) - introduction to error handling with Camel, including using redelivery and a Dead Letter Channel.
* [camel.log](camel-log) - a very simple Camel application using a timer to trigger a message every 5th second which is then written to the server log.
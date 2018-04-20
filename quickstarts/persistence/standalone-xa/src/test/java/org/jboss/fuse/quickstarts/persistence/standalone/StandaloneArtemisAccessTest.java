/*
 *  Copyright 2005-2018 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.jboss.fuse.quickstarts.persistence.standalone;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class StandaloneArtemisAccessTest {

    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StandaloneArtemisAccessTest.class);

    @Test
    public void manualNonXAJMSAccess() throws Exception {
        ApplicationContext context = new ClassPathXmlApplicationContext("classpath:/StandaloneArtemisAccessTest.xml");

        ConnectionFactory cfCore = context.getBean("coreCf", ConnectionFactory.class);
        {
            try (Connection c = cfCore.createConnection("fuse", "fuse")) {
                c.start();
                try (Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    Queue q = context.getBean("coreQueue", Queue.class);
                    LOG.info("Using Artemis queue: {}", q.getClass().getName());

                    try (MessageProducer producer = session.createProducer(q)) {
                        TextMessage message = session.createTextMessage("Hello A-MQ 7");
                        producer.send(message);
                    }
                }
            }
        }
        ConnectionFactory cfQpid = context.getBean("qpidCf", ConnectionFactory.class);
        {
            try (Connection c = cfQpid.createConnection("fuse", "fuse")) {
                c.start();
                try (Session session = c.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                    Queue q = context.getBean("qpidQueue", Queue.class);
                    LOG.info("Using QPid queue: {}", q.getClass().getName());

                    try (MessageConsumer consumer = session.createConsumer(q)) {
                        Message msg = consumer.receive(5000);
                        LOG.info("MESSAGE: " + ((TextMessage)msg).getText());
                        LOG.info("MESSAGE: " + msg);
                    }
                }
            }
        }
    }

}

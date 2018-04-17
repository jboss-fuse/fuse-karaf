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

import java.io.File;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.util.propertyservice.PropertiesFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.apache.commons.io.FileUtils;
import org.jboss.narayana.osgi.jta.internal.OsgiTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.messaginghub.pooled.jms.JmsPoolXAConnectionFactory;

public class StandaloneXAArtemisAccessTest {

    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StandaloneXAArtemisAccessTest.class);

    private UserTransaction userTransaction;
    private TransactionManager transactionManager;

    @Before
    public void init() {
        File txDir = new File("target/tx");
        FileUtils.deleteQuietly(txDir);
        txDir.mkdirs();

        Properties properties = PropertiesFactory.getDefaultProperties();
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.objectStoreType", "com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.objectStoreDir", "target/tx");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.localOSRoot", "defaultStore");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.communicationStore.objectStoreType", "com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.communicationStore.objectStoreDir", "target/tx");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.communicationStore.localOSRoot", "communicationStore");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.stateStore.objectStoreType", "com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.stateStore.objectStoreDir", "target/tx");
        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.stateStore.localOSRoot", "stateStore");

        // Arjuna/Narayana objects
        JTAEnvironmentBean env = jtaPropertyManager.getJTAEnvironmentBean();
        OsgiTransactionManager tmimpl = new OsgiTransactionManager();
        env.setUserTransaction(tmimpl);
        env.setTransactionManager(tmimpl);

        // javax.transaction API
        this.userTransaction = tmimpl;
        this.transactionManager = tmimpl;
    }

    @Test
    public void manualXAJMSAccess() throws Exception {
        /* Traditional JMS API using Artemis JMS Client */

        // broker-specific, non-pooling, non-enlisting javax.jms.XAConnectionFactory
        ActiveMQXAConnectionFactory brokerCF
                = new org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory("tcp://localhost:61616");
        // broker-specific configuration
        brokerCF.setCallTimeout(2000);
        brokerCF.setInitialConnectAttempts(3);
        // ...

        // non broker-specific, pooling, enlisting javax.jms.ConnectionFactory
        JmsPoolXAConnectionFactory pool = new org.messaginghub.pooled.jms.JmsPoolXAConnectionFactory();
        // delegate to broker-specific XAConnectionFactory
        pool.setConnectionFactory(brokerCF);
        // delegate to JTA transaction manager
        pool.setTransactionManager(transactionManager);
        // non broker-specific configuration
        pool.setMaxConnections(10);
        pool.setIdleTimeout(10000);
        // ...

        // JMS code
        javax.jms.ConnectionFactory jmsCF = pool;

        userTransaction.begin();

        try (Connection c = jmsCF.createConnection("fuse", "fuse")) {
            c.start();
            try (Session session = c.createSession(false, Session.SESSION_TRANSACTED)) {
                ActiveMQQueue brokerQueue = new ActiveMQQueue("DEV.QUEUE.1");
                Queue jmsQueue = brokerQueue;

                try (MessageProducer producer = session.createProducer(jmsQueue)) {
                    TextMessage message = session.createTextMessage("Hello A-MQ 7");
                    producer.send(message);
                }
            }
        }

        userTransaction.commit();
    }

}

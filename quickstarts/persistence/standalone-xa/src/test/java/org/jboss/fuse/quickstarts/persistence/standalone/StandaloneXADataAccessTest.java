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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.common.util.propertyservice.PropertiesFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.DataSourceConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.apache.commons.dbcp2.managed.DataSourceXAConnectionFactory;
import org.apache.commons.dbcp2.managed.ManagedDataSource;
import org.apache.commons.dbcp2.managed.PoolableManagedConnectionFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.jboss.narayana.osgi.jta.internal.OsgiTransactionManager;
import org.junit.Before;
import org.junit.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.xa.PGXADataSource;

public class StandaloneXADataAccessTest {

    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StandaloneXADataAccessTest.class);

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
    public void manualNonXADataAccess() throws Exception {

        // database-specific, non-pooling, non-enlisting javax.sql.DataSource
        PGSimpleDataSource postgresql = new org.postgresql.ds.PGSimpleDataSource();
        postgresql.setUrl("jdbc:postgresql://localhost:5432/reportdb");
        postgresql.setUser("fuse");
        postgresql.setPassword("fuse");
        postgresql.setConnectTimeout(5);
        postgresql.setCurrentSchema("report");
        postgresql.setApplicationName("StandaloneXADataAccessTest.manualNonXADataAccess()");

        // non database-specific, pooling, non-enlisting javax.sql.DataSource
        // if we just use org.apache.commons.dbcp2.BasicDataSource, the connections would be created using
        // org.apache.commons.dbcp2.DriverConnectionFactory without delegating to org.postgresql.ds.PGSimpleDataSource
        // so we have to do more configuration manually

        // DBCP API for org.apache.commons.dbcp2.ConnectionFactory
        DataSourceConnectionFactory dbcpFactory = new org.apache.commons.dbcp2.DataSourceConnectionFactory(postgresql);
        // DBCP extension to commons-pool2 API
        PoolableConnectionFactory dbcpPooledObjectFactory = new org.apache.commons.dbcp2.PoolableConnectionFactory(dbcpFactory, null);
        // commons-pool API
        GenericObjectPool<PoolableConnection> objectPool = new org.apache.commons.pool2.impl.GenericObjectPool<>(dbcpPooledObjectFactory);
        objectPool.setMinIdle(2);
        objectPool.setMaxTotal(10);
        objectPool.setTestOnBorrow(true);

        dbcpPooledObjectFactory.setPool(objectPool);
        dbcpPooledObjectFactory.setMaxConnLifetimeMillis(30000);
        dbcpPooledObjectFactory.setValidationQuery("select schema_name, schema_owner from information_schema.schemata");
        dbcpPooledObjectFactory.setValidationQueryTimeout(2);

        // final DBCP pool
        PoolingDataSource<PoolableConnection> pool = new org.apache.commons.dbcp2.PoolingDataSource<>(objectPool);

        javax.sql.DataSource applicationDataSource = pool;

        try (Connection c = applicationDataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select id, details from incident")) {
                    while (rs.next()) {
                        LOG.info(String.format("%d: %s", rs.getLong("id"), rs.getString("details")));
                    }
                }
            }
        }
    }

    @Test
    public void convenientNonXADataAccess() throws Exception {

        // non database-specific, pooling, non-enlisting javax.sql.DataSource
        // if we don't mind, we can just use org.apache.commons.dbcp2.BasicDataSource, and
        // the connections would be created using org.apache.commons.dbcp2.DriverConnectionFactory without delegating
        // to database-specific org.postgresql.ds.PGSimpleDataSource
        // PGSimpleDataSource uses java.sql.DriverManager.getConnection() anyway internally

        // final DBCP pool
        BasicDataSource pool = new org.apache.commons.dbcp2.BasicDataSource();
        pool.setUrl("jdbc:postgresql://localhost:5432/reportdb");
        pool.setUsername("fuse");
        pool.setPassword("fuse");
        pool.setMinIdle(2);
        pool.setMaxTotal(10);

        javax.sql.DataSource applicationDataSource = pool;

        try (Connection c = applicationDataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select id, summary, details from report.incident")) {
                    while (rs.next()) {
                        LOG.info(String.format("%d: %s, %s", rs.getLong("id"), rs.getString("summary"), rs.getString("details")));
                    }
                }
            }
        }
    }

    @Test
    public void manualXADataAccess() throws Exception {

        // database-specific, non-pooling, non-enlisting javax.sql.XADataSource
        PGXADataSource postgresql = new org.postgresql.xa.PGXADataSource();
        postgresql.setUrl("jdbc:postgresql://localhost:5432/reportdb");
        postgresql.setUser("fuse");
        postgresql.setPassword("fuse");
        postgresql.setConnectTimeout(5);
        postgresql.setCurrentSchema("report");
        postgresql.setApplicationName("StandaloneXADataAccessTest.manualXADataAccess()");

        // non database-specific, pooling, enlisting javax.sql.DataSource
        // here's the same configuration as in manualNonXADataAccess(), but using _managed_ versions

        // DBCP API for org.apache.commons.dbcp2.ConnectionFactory
        DataSourceXAConnectionFactory dbcpFactory = new org.apache.commons.dbcp2.managed.DataSourceXAConnectionFactory(transactionManager, postgresql);
        // DBCP extension to commons-pool2 API
        PoolableManagedConnectionFactory dbcpPooledObjectFactory = new org.apache.commons.dbcp2.managed.PoolableManagedConnectionFactory(dbcpFactory, null);
        // commons-pool API
        GenericObjectPool<PoolableConnection> objectPool = new org.apache.commons.pool2.impl.GenericObjectPool<>(dbcpPooledObjectFactory);
        objectPool.setMinIdle(2);
        objectPool.setMaxTotal(10);
        dbcpPooledObjectFactory.setPool(objectPool);
        // final DBCP pool
        ManagedDataSource<PoolableConnection> pool = new org.apache.commons.dbcp2.managed.ManagedDataSource<>(objectPool, dbcpFactory.getTransactionRegistry());

        javax.sql.DataSource applicationDataSource = pool;

        userTransaction.begin();

        try (Connection c = applicationDataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select id, name, summary from incident")) {
                    while (rs.next()) {
                        LOG.info(String.format("%d: %s, %s", rs.getLong("id"), rs.getString("name"), rs.getString("summary")));
                    }
                }
            }
        }

        userTransaction.commit();
    }

    @Test
    public void convenientXADataAccess() throws Exception {

        // database-specific, non-pooling, non-enlisting javax.sql.XADataSource
        PGXADataSource postgresql = new org.postgresql.xa.PGXADataSource();
        postgresql.setUrl("jdbc:postgresql://localhost:5432/reportdb");
        postgresql.setUser("fuse");
        postgresql.setPassword("fuse");
        postgresql.setConnectTimeout(5);
        postgresql.setCurrentSchema("report");
        postgresql.setApplicationName("StandaloneXADataAccessTest.convenientXADataAccess()");

        // non database-specific, pooling, enlisting javax.sql.DataSource
        // using BasicManagedDataSource hides the configuration we had to perform in manualXADataAccess

        BasicManagedDataSource pool = new org.apache.commons.dbcp2.managed.BasicManagedDataSource();
        pool.setXaDataSourceInstance(postgresql);
        pool.setTransactionManager(transactionManager);
        pool.setMinIdle(3);
        pool.setMaxTotal(10);

        javax.sql.DataSource applicationDataSource = pool;

        userTransaction.begin();

        try (Connection c = applicationDataSource.getConnection()) {
            try (Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select id, summary from incident")) {
                    while (rs.next()) {
                        LOG.info(String.format("%d: %s", rs.getLong("id"), rs.getString("summary")));
                    }
                }
            }
        }

        userTransaction.commit();
    }

}

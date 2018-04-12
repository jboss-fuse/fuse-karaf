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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.recovery.RecoveryModule;
import com.arjuna.ats.internal.jbossatx.jta.XAResourceRecoveryHelperWrapper;
import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import com.arjuna.ats.jta.xa.XidImple;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import com.arjuna.common.util.propertyservice.PropertiesFactory;
import org.apache.commons.io.FileUtils;
import org.jboss.narayana.osgi.jta.internal.OsgiTransactionManager;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneTxManagerTest {

    public static org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(StandaloneTxManagerTest.class);

    @Before
    public void init() {
        File txDir = new File("target/tx");
        FileUtils.deleteQuietly(txDir);
        txDir.mkdirs();
    }

    @Test
    public void useNarayanaTxManager() throws Exception {
        Properties properties = PropertiesFactory.getDefaultProperties();
        properties.setProperty("com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean.recoveryBackoffPeriod", "1");

        // as in EAP 7.1
//        properties.setProperty("com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean.objectStoreType", "com.arjuna.ats.internal.arjuna.objectstore.ShadowNoFileLockStore");
        // as in narayana-osgi-jta-5.5.31.Final-redhat-1.jar (requires Artemis)
//        properties.setProperty("com.arjuna.ats.arjuna.objectstore.ObjectStoreEnvironmentBean.objectStoreType", "com.arjuna.ats.internal.arjuna.objectstore.hornetq.HornetqObjectStoreAdaptor");

        // there are 3 stores actually
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
        UserTransaction ut = tmimpl;
        TransactionManager tm = tmimpl;
        LOG.info("env: " + env);

        // begin JTA/XA transaction
        ut.begin();

        Transaction tx = tm.getTransaction();
        LOG.info("Current transaction: {}", tx);

        // manually enlist two different resources - that's normally the task of "enlisting datasource"
        final DummyXAResource r1 = new DummyXAResource("r1", false);
        final DummyXAResource r2 = new DummyXAResource("r2", true);
        tx.enlistResource(r1);
        tx.enlistResource(r2);

        // JTA/XA commit - one of the resource will fail in 2nd phase simulating unavailability of databse after
        // XA-prepare and before XA-commit, leaving distributed transaction in uncertain state
        ut.commit();

        // we're in failed state - 2nd resource didn't perform 2nd phase of 2PC
        ObjectStoreEnvironmentBean storeEnv = BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class);
        String dir = storeEnv.getObjectStoreDir() + File.separator + "ShadowNoFileLockStore" + File.separator + storeEnv.getLocalOSRoot();
        String actionDir = new AtomicAction().type();
        File[] xids = new File(dir, actionDir).listFiles();
        assertThat(xids).isNotNull();
        assertThat(xids.length).isEqualTo(1);
        assertThat(xids[0].getName()).as("There should be XID stored in Arjuna object store")
                .isEqualTo(((XidImple)r2.crashedXid).getTransactionUid().fileStringForm());

        // access Narayana recovery manager and tell it about XA resources
        RecoveryManager rm = RecoveryManager.manager(RecoveryManager.DIRECT_MANAGEMENT);
        rm.initialize();
        Optional<RecoveryModule> xarm = rm.getModules().stream()
                .filter(_rm -> _rm instanceof XARecoveryModule).findFirst();
        assertThat(xarm.isPresent());
        xarm.ifPresent(recoveryModule ->
                ((XARecoveryModule)recoveryModule).addXAResourceRecoveryHelper(new XAResourceRecoveryHelperWrapper(()
                        -> new XAResource[] { r1, r2 })));

        final CountDownLatch cl = new CountDownLatch(1);
        r2.callback = cl::countDown;

        // after running recovery, the commit on previously failed XA resource should be called again with same XID
        rm.scan();
        assertThat(cl.getCount()).isEqualTo(0L);
    }

    /**
     * Test implementation of {@link XAResource}. Usually this interface is implemented by JDBC or JMS connection.
     * Here we don't operate on any persistent data store and only on virtual <em>XA resource</em> that can participate
     * in 2-phase commit protocol.
     */
    public static final class DummyXAResource implements XAResource {

        private final boolean crash;
        private final String name;

        private boolean recovered = false;
        private Xid crashedXid;

        public Runnable callback;

        public DummyXAResource(String name, boolean crashOnCommit) {
            this.name = name;
            this.crash = crashOnCommit;
        }

        @Override
        public void start(Xid xid, int i) throws XAException {
            LOG.info(name + " start({}, {})", xid, i);
        }

        @Override
        public void end(Xid xid, int i) throws XAException {
            LOG.info(name + " end({})", xid);
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            LOG.info(name + " prepare({})", xid);
            return XAResource.XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean b) throws XAException {
            LOG.info(name + " commit({}, {})", xid, b);
            if (callback != null) {
                callback.run();
            }
            if (crash && crashedXid == null) {
                crashedXid = xid;
                throw new XAException(XAException.XAER_RMFAIL);
            }
        }

        @Override
        public void forget(Xid xid) throws XAException {
            LOG.info(name + " forget({})", xid);
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return xaResource == this;
        }

        @Override
        public Xid[] recover(int i) throws XAException {
            LOG.info(name + " recover({})", i);
            Xid[] result = crash && !recovered ? new Xid[] { crashedXid } : new Xid[0];
            recovered = true;
            return result;
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            LOG.info(name + " rollback({})", xid);
        }

        @Override
        public boolean setTransactionTimeout(int i) throws XAException {
            return false;
        }
    }

}

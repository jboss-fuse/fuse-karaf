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

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;

@WebServlet("tx")
public class TxServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            InitialContext context = new InitialContext();
            DataSource ds1 = (DataSource) context.lookup("java:/PostgresXADS");
            DataSource ds2 = (DataSource) context.lookup("java:/MysqlXADS");
            UserTransaction tx = (UserTransaction) context.lookup("java:comp/UserTransaction");
            tx.begin();
            Connection c1 = ds1.getConnection();
            Connection c2 = ds2.getConnection();
            resp.setContentType("text/plain");
            try (PrintWriter writer = resp.getWriter()) {
                writer.println("db1: " + c1.getMetaData().getDatabaseProductName() + "/" + c1.getMetaData().getDatabaseProductVersion());
                writer.println("db2: " + c2.getMetaData().getDatabaseProductName() + "/" + c2.getMetaData().getDatabaseProductVersion());
            }
            c1.close();
            c2.close();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

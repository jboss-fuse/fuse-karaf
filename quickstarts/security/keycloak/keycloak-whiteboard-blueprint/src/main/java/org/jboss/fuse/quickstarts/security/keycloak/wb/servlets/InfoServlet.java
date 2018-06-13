/**
 *  Copyright 2005-2015 Red Hat, Inc.
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
package org.jboss.fuse.quickstarts.security.keycloak.wb.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.LoggerFactory;

public class InfoServlet extends HttpServlet {

    public static org.slf4j.Logger LOG = LoggerFactory.getLogger(InfoServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");

        if (req.getUserPrincipal() != null) {
            StringWriter sw = new StringWriter();
            byte[] buf = new byte[4096];
            int read = -1;
            try (InputStream is = getClass().getResourceAsStream("/page.html")) {
                while ((read = is.read(buf, 0, 4096)) > 0) {
                    sw.write(new String(buf, 0, read));
                }
            }

            String page = sw.toString();
            page = page.replace("${user}", req.getUserPrincipal().getName());
            page = page.replace("${class}", req.getUserPrincipal().getClass().getName());

            resp.getWriter().println(page);
        } else {
            resp.getWriter().println("[keycloak-whiteboard-blueprint] User not logged in");
        }
        resp.getWriter().flush();
        resp.getWriter().close();
    }

}

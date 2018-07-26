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
package org.jboss.fuse.quickstarts.security.keycloak.cxf;

import java.util.LinkedList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.util.BasicAuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import static org.junit.Assert.fail;

public class JaxRsClientTest {

    public static Logger LOG = LoggerFactory.getLogger(JaxRsClientTest.class);

    @BeforeClass
    public static void initLogging() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @AfterClass
    public static void cleanupLogging() {
        SLF4JBridgeHandler.uninstall();
    }

    @Test
    public void helloExternalAuthenticated() throws Exception {

        String accessToken = null;

        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            // "4.3.  Resource Owner Password Credentials Grant"
            // from https://tools.ietf.org/html/rfc6749#section-4.3
            // we use "resource owner" credentials directly to obtain the token
            HttpPost post = new HttpPost("http://localhost:8180/auth/realms/fuse7karaf/protocol/openid-connect/token");
            LinkedList<NameValuePair> params = new LinkedList<>();
            params.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD));
            params.add(new BasicNameValuePair("username", "admin"));
            params.add(new BasicNameValuePair("password", "passw0rd"));
            UrlEncodedFormEntity postData = new UrlEncodedFormEntity(params);
            post.setEntity(postData);

            String basicAuth = BasicAuthHelper.createHeader("cxf-external", "7e20addd-87fc-4528-808c-e9c7c950ef23");
            post.setHeader("Authorization", basicAuth);
            CloseableHttpResponse response = client.execute(post);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            if (json.get("error") == null) {
                accessToken = json.get("access_token").asText();
                LOG.info("token: {}", accessToken);
            } else {
                LOG.warn("error: {}, description: {}", json.get("error"), json.get("error_description"));
                fail();
            }
            response.close();
        }

        if (accessToken != null) {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                // "The OAuth 2.0 Authorization Framework: Bearer Token Usage"
                // https://tools.ietf.org/html/rfc6750
                HttpGet get = new HttpGet("http://localhost:8282/jaxrs/service/hello/hi");

                get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                CloseableHttpResponse response = client.execute(get);

                LOG.info("response: {}", EntityUtils.toString(response.getEntity()));
                response.close();
            }
        }
    }

    @Test
    public void helloEmbeddedAuthenticated() throws Exception {

        String accessToken = null;

        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            // "4.3.  Resource Owner Password Credentials Grant"
            // from https://tools.ietf.org/html/rfc6749#section-4.3
            // we use "resource owner" credentials directly to obtain the token
            HttpPost post = new HttpPost("http://localhost:8180/auth/realms/fuse7karaf/protocol/openid-connect/token");
            LinkedList<NameValuePair> params = new LinkedList<>();
            params.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD));
            params.add(new BasicNameValuePair("username", "admin"));
            params.add(new BasicNameValuePair("password", "passw0rd"));
            UrlEncodedFormEntity postData = new UrlEncodedFormEntity(params);
            post.setEntity(postData);

            String basicAuth = BasicAuthHelper.createHeader("cxf", "f1ec716d-2262-434d-8e98-bf31b6b858d6");
            post.setHeader("Authorization", basicAuth);
            CloseableHttpResponse response = client.execute(post);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.getEntity().getContent());
            if (json.get("error") == null) {
                accessToken = json.get("access_token").asText();
                LOG.info("token: {}", accessToken);
            } else {
                LOG.warn("error: {}, description: {}", json.get("error"), json.get("error_description"));
                fail();
            }
            response.close();
        }

        if (accessToken != null) {
            try (CloseableHttpClient client = HttpClients.createMinimal()) {
                // "The OAuth 2.0 Authorization Framework: Bearer Token Usage"
                // https://tools.ietf.org/html/rfc6750
                HttpGet get = new HttpGet("http://localhost:8181/cxf/jaxrs/service/hello/hi");

                get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                CloseableHttpResponse response = client.execute(get);

                LOG.info("response: {}", EntityUtils.toString(response.getEntity()));
                response.close();
            }
        }
    }

}

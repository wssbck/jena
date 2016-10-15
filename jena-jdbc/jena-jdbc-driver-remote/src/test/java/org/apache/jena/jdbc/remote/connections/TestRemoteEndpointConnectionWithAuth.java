/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.jdbc.remote.connections;

import java.io.File ;
import java.io.FileWriter ;
import java.io.IOException ;
import java.sql.SQLException ;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.fuseki.ServerCtl ;
import org.apache.jena.fuseki.server.FusekiConfig ;
import org.apache.jena.fuseki.server.SPARQLServer ;
import org.apache.jena.fuseki.server.ServerConfig ;
import org.apache.jena.jdbc.JdbcCompatibility ;
import org.apache.jena.jdbc.connections.JenaConnection ;
import org.apache.jena.jdbc.utils.TestUtils ;
import org.apache.jena.query.Dataset ;
import org.apache.jena.sparql.core.DatasetGraph ;
import org.apache.jena.sparql.core.DatasetGraphFactory ;
import org.apache.jena.sparql.modify.request.Target ;
import org.apache.jena.sparql.modify.request.UpdateDrop ;
import org.apache.jena.update.Update ;
import org.apache.jena.update.UpdateExecutionFactory ;
import org.apache.jena.update.UpdateProcessor ;
import org.junit.After ;
import org.junit.AfterClass ;
import org.junit.BeforeClass ;
import org.junit.Ignore;

/**
 * Tests for the {@link RemoteEndpointConnection} where we use HTTP
 * authentication
 * 
 */
@Ignore
public class TestRemoteEndpointConnectionWithAuth extends AbstractRemoteEndpointConnectionTests {

    private static String USER = "test";
    private static String PASSWORD = "letmein";
    private static File realmFile;
    private static SPARQLServer server;
    private static HttpClient client;

    /**
     * Setup for the tests by allocating a Fuseki instance to work with
     * @throws IOException 
     */
    @BeforeClass
    public static void setup() throws IOException {
        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
        credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER, PASSWORD));
        client = HttpClients.custom().setDefaultCredentialsProvider(credsProv).build();

        realmFile = File.createTempFile("realm", ".properties");

        try(FileWriter writer = new FileWriter(realmFile)) {
            writer.write(USER + ": " + PASSWORD + ", fuseki\n");
        }

        DatasetGraph dsg = DatasetGraphFactory.create();

        // This must agree with ServerTest
        ServerConfig conf = FusekiConfig.defaultConfiguration(ServerCtl.datasetPath(), dsg, true, false);
        conf.port = ServerCtl.port();
        conf.pagesPort = ServerCtl.port();
        conf.authConfigFile = realmFile.getAbsolutePath();

        server = new SPARQLServer(conf);
        server.start();
    }

    /**
     * Clean up after each test by resetting the Fuseki instance
     */
    @After
    public void cleanupTest() {
        Update clearRequest = new UpdateDrop(Target.ALL) ;
        UpdateProcessor proc = UpdateExecutionFactory.createRemote(clearRequest, ServerCtl.serviceUpdate(), client) ;
        proc.execute() ;
    }

    /**
     * Clean up after tests by de-allocating the Fuseki instance
     */
    @AfterClass
    public static void cleanup() {
        server.stop();
        realmFile.delete();
    }

    @Override
    protected boolean supportsTimeouts() {
        // While timeouts are supported they are unreliable for remote
        // connections
        return false;
    }

    @Override
    protected JenaConnection getConnection() throws SQLException {
        return new RemoteEndpointConnection(ServerCtl.serviceQuery(), ServerCtl.serviceUpdate(), null, null, null, null,
                client, JenaConnection.DEFAULT_HOLDABILITY,
                JdbcCompatibility.DEFAULT, null, null);
    }

    @Override
    protected JenaConnection getConnection(Dataset ds) throws SQLException {
        // Set up the dataset
        TestUtils.copyToRemoteDataset(ds, ServerCtl.serviceREST(), client);
        return new RemoteEndpointConnection(ServerCtl.serviceQuery(), ServerCtl.serviceUpdate(), null, null, null, null,
                client, JenaConnection.DEFAULT_HOLDABILITY,
                JdbcCompatibility.DEFAULT, null, null);
    }
}

/*
 * Copyright (c) 2008-2026, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.config;

import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastParallelClassRunner.class)
@Category(QuickTest.class)
public class XmlSchemaSourceSecurityTest {

    private final SchemaLoader schemaLoader = new SchemaLoader();

    @Test
    public void loadsBundledSchemaFromClasspath() throws Exception {
        try (InputStream input = schemaLoader.load("hazelcast-config-5.7.xsd")) {
            assertThat(input).isNotNull();
            assertThat(input.read()).isNotNegative();
        }
    }

    @Test
    public void buildsConfigurationWithBundledSchema() {
        Config config = new XmlConfigBuilder(xmlInput(
                "http://www.hazelcast.com/schema/config "
                        + "http://www.hazelcast.com/schema/config/hazelcast-config-5.7.xsd"))
                .build();

        assertThat(config.getClusterName()).isEqualTo("schema-security-test");
    }

    @Test
    public void rejectsExternalSchemaBeforeNetworkAccess() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/external.xsd", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String externalSchema = "http://127.0.0.1:" + server.getAddress().getPort() + "/external.xsd";
            assertThatThrownBy(() -> new XmlConfigBuilder(xmlInput(
                    "http://www.hazelcast.com/schema/config "
                            + "http://www.hazelcast.com/schema/config/hazelcast-config-5.7.xsd "
                            + "urn:untrusted " + externalSchema)).build())
                    .isInstanceOf(InvalidConfigurationException.class)
                    .hasMessageContaining("External xsd schemas are not allowed");
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rejectsDocumentSelectedExternalAndRelativeSchemas() {
        String[] rejected = {
                "http://127.0.0.1:8080/schema.xsd",
                "file:/tmp/schema.xsd",
                "../schema.xsd",
                "/schema.xsd",
                "schemas\\schema.xsd"
        };

        for (String schema : rejected) {
            assertThatThrownBy(() -> schemaLoader.load(schema))
                    .as(schema)
                    .isInstanceOf(InvalidConfigurationException.class);
        }
    }

    private static final class SchemaLoader extends AbstractXmlConfigHelper {
        private InputStream load(String schemaLocation) {
            return loadSchemaFile(schemaLocation);
        }
    }

    private static InputStream xmlInput(String schemaLocations) {
        String xml = "<hazelcast xmlns=\"http://www.hazelcast.com/schema/config\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"" + schemaLocations + "\">"
                + "<cluster-name>schema-security-test</cluster-name>"
                + "</hazelcast>";
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}

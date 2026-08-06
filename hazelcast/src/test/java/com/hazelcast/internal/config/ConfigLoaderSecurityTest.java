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

package com.hazelcast.internal.config;

import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static com.hazelcast.internal.config.ConfigLoader.MAX_CONFIG_BYTES;
import static com.hazelcast.internal.config.ConfigLoader.REMOTE_CONFIG_ORIGINS_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class ConfigLoaderSecurityTest {

    private static final byte[] CONFIG_BODY = "<hazelcast/>".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServer server;

    @After
    public void tearDown() {
        System.clearProperty(REMOTE_CONFIG_ORIGINS_PROPERTY);
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void opensLocalFile() throws Exception {
        File config = temporaryFolder.newFile("config.xml");
        Files.write(config.toPath(), CONFIG_BODY);

        try (InputStream inputStream = ConfigLoader.openConfig(config.toURI().toURL())) {
            assertThat(inputStream.read(new byte[0])).isZero();
            assertThat(inputStream.readAllBytes()).isEqualTo(CONFIG_BODY);
        }
    }

    @Test
    public void opensEntryFromLocalJar() throws Exception {
        File archive = temporaryFolder.newFile("config.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive.toPath()))) {
            output.putNextEntry(new JarEntry("config/member.xml"));
            output.write(CONFIG_BODY);
            output.closeEntry();
        }
        URL entry = new URL("jar:" + archive.toURI().toURL() + "!/config/member.xml");

        try (InputStream inputStream = ConfigLoader.openConfig(entry)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(CONFIG_BODY);
        }
    }

    @Test
    public void rejectsRemoteConfigurationByDefaultWithoutConnecting() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer("/config", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, CONFIG_BODY);
        });

        assertThatThrownBy(() -> ConfigLoader.openConfig(url("127.0.0.1", "/config")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not allowed");
        assertThat(requests).hasValue(0);
    }

    @Test
    public void opensOnlyAnExactlyAllowlistedRemoteOrigin() throws Exception {
        startServer("/config", exchange -> respond(exchange, 200, CONFIG_BODY));
        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("127.0.0.1"));

        try (InputStream inputStream = ConfigLoader.openConfig(url("127.0.0.1", "/config"))) {
            assertThat(inputStream.readAllBytes()).isEqualTo(CONFIG_BODY);
        }

        assertThatThrownBy(() -> ConfigLoader.openConfig(url("localhost", "/config")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    public void normalizesHostnameCaseBeforeExactOriginComparison() throws Exception {
        startServer("/config", exchange -> respond(exchange, 200, CONFIG_BODY));
        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("LOCALHOST"));

        try (InputStream inputStream = ConfigLoader.openConfig(url("localhost", "/config"))) {
            assertThat(inputStream.readAllBytes()).isEqualTo(CONFIG_BODY);
        }
    }

    @Test
    public void neverFollowsRemoteRedirects() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", url("127.0.0.1", "/target").toString());
            respond(exchange, 302, new byte[0]);
        });
        server.createContext("/target", exchange -> {
            targetRequests.incrementAndGet();
            respond(exchange, 200, CONFIG_BODY);
        });
        server.start();
        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("127.0.0.1"));

        assertThatThrownBy(() -> ConfigLoader.openConfig(url("127.0.0.1", "/redirect")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP status 302");
        assertThat(targetRequests).hasValue(0);
    }

    @Test
    public void rejectsRemoteContentLargerThanLimit() throws Exception {
        startServer("/oversized", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[8_192];
            long remaining = MAX_CONFIG_BYTES + 1;
            try {
                while (remaining > 0) {
                    int length = (int) Math.min(chunk.length, remaining);
                    exchange.getResponseBody().write(chunk, 0, length);
                    remaining -= length;
                }
            } catch (IOException ignored) {
                // The client is expected to close the connection as soon as the limit is exceeded.
            } finally {
                exchange.close();
            }
        });
        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("127.0.0.1"));

        assertThatThrownBy(() -> {
            try (InputStream inputStream = ConfigLoader.openConfig(url("127.0.0.1", "/oversized"))) {
                byte[] buffer = new byte[8_192];
                while (inputStream.read(buffer) != -1) {
                    // Consume the bounded stream.
                }
            }
        })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    public void rejectsRemoteFileAuthoritiesAndRemoteJarFiles() throws Exception {
        assertThatThrownBy(() -> ConfigLoader.openConfig(new URL("file://127.0.0.1/share/config.xml")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("authorities");
        assertThatThrownBy(() -> ConfigLoader.openConfig(
                new URL("jar:http://127.0.0.1:9/config.jar!/config.xml")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("local file-backed");
    }

    @Test
    public void rejectsMalformedAllowlistEntriesAndNormalizedPathBypasses() throws Exception {
        startServer("/config", exchange -> respond(exchange, 200, CONFIG_BODY));
        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("127.0.0.1") + "/admin");

        assertThatThrownBy(() -> ConfigLoader.openConfig(url("127.0.0.1", "/config")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("only an origin");

        System.setProperty(REMOTE_CONFIG_ORIGINS_PROPERTY, origin("127.0.0.1"));
        assertThatThrownBy(() -> ConfigLoader.openConfig(url("127.0.0.1", "/safe/../config")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-normalized");
    }

    private void startServer(String path, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> handler.handle(exchange));
        server.start();
    }

    private URL url(String host, String path) throws IOException {
        return new URL("http", host, server.getAddress().getPort(), path);
    }

    private String origin(String host) {
        return "http://" + host + ':' + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try {
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}

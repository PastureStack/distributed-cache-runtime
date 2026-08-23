/*
 * Copyright 2026 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.elastic;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.function.SupplierEx;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;

import javax.annotation.Nonnull;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Convenience factories for the supported Elasticsearch REST 5 client.
 * <p>
 * The returned builder is intended for
 * {@link ElasticSourceBuilder#clientFn(SupplierEx)} and
 * {@link ElasticSinkBuilder#clientFn(SupplierEx)}.
 */
public final class ElasticClients {

    private static final int DEFAULT_PORT = 9200;

    private ElasticClients() {
    }

    /** Creates a client builder for {@code http://localhost:9200}. */
    @Nonnull
    public static Rest5ClientBuilder client() {
        return client("localhost", DEFAULT_PORT);
    }

    /**
     * Creates a client builder for a location containing a host and,
     * optionally, a scheme and port. HTTP is used when the scheme is omitted.
     */
    @Nonnull
    public static Rest5ClientBuilder client(@Nonnull String location) {
        String normalized = location.contains("://") ? location : "http://" + location;
        return Rest5Client.builder(URI.create(normalized));
    }

    /** Creates an HTTP client builder for the given host and port. */
    @Nonnull
    public static Rest5ClientBuilder client(@Nonnull String hostname, int port) {
        return Rest5Client.builder(new HttpHost("http", hostname, port));
    }

    /** Creates an HTTP client builder with basic authentication. */
    @Nonnull
    public static Rest5ClientBuilder client(
            @Nonnull String username,
            @Nonnull String password,
            @Nonnull String hostname,
            int port
    ) {
        return client(username, password, hostname, port, "http");
    }

    /**
     * Creates a client builder with basic authentication and an explicit
     * {@code http} or {@code https} scheme.
     */
    @Nonnull
    public static Rest5ClientBuilder client(
            @Nonnull String username,
            @Nonnull String password,
            @Nonnull String hostname,
            int port,
            @Nonnull String scheme
    ) {
        String token = Base64.getEncoder().encodeToString(
                (username + ':' + password).getBytes(StandardCharsets.UTF_8));
        Header[] headers = {new BasicHeader("Authorization", "Basic " + token)};
        return Rest5Client.builder(new HttpHost(scheme, hostname, port))
                          .setDefaultHeaders(headers);
    }
}

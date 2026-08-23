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

package com.hazelcast.jet.elastic.impl;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.TransportOptions;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.core.test.TestSupport;
import com.hazelcast.jet.elastic.ElasticClients;
import com.hazelcast.jet.elastic.impl.Shard.Prirep;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ElasticSourcePTest {

    private static final String KEEP_ALIVE = "42m";
    private static final String SCROLL_ID = "random-scroll-id";

    private final List<CapturedRequest> requests = new ArrayList<>();
    private final AtomicInteger scrollRequests = new AtomicInteger();
    private HttpServer server;
    private boolean returnHits;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void whenRunProcessor_thenSendScrollAndPerRequestOptions() throws Exception {
        FunctionEx<co.elastic.clients.elasticsearch._types.RequestBase, TransportOptions> optionsFn = request ->
                DefaultTransportOptions.EMPTY.toBuilder().addHeader("TestHeader", "value").build();

        runProcessor(optionsFn, emptyList(), false, false).expectOutput(emptyList());

        CapturedRequest search = requests.get(0);
        assertThat(URLDecoder.decode(search.query, StandardCharsets.UTF_8)).contains("scroll=42m");
        assertThat(search.testHeader).isEqualTo("value");
    }

    @Test
    public void givenMultiplePages_whenRunProcessor_thenReturnAllHitsAndClearScroll() throws Exception {
        returnHits = true;

        runProcessor(request -> DefaultTransportOptions.EMPTY, emptyList(), false, false)
                .expectOutput(List.of("Frantisek", "Vladimir"));

        assertThat(requests)
                .filteredOn(request -> request.path.equals("/_search/scroll") && request.method.equals("POST"))
                .hasSize(2)
                .allSatisfy(request -> assertThat(request.body)
                        .contains(SCROLL_ID)
                        .contains(KEEP_ALIVE));
        assertThat(requests)
                .anySatisfy(request -> {
                    assertThat(request.method).isEqualTo("DELETE");
                    assertThat(request.path).isEqualTo("/_search/scroll");
                    assertThat(request.body).contains(SCROLL_ID);
                });
    }

    @Test
    public void whenSlicingEnabled_thenUseGlobalProcessorCoordinates() throws Exception {
        TestSupport support = runProcessor(
                request -> DefaultTransportOptions.EMPTY, emptyList(), true, false);
        support.localProcessorIndex(1);
        support.localParallelism(2);
        support.globalProcessorIndex(4);
        support.totalParallelism(6);
        support.expectOutput(emptyList());

        assertThat(requests.get(0).body).contains("\"slice\":{\"id\":\"4\",\"max\":6}");
    }

    @Test
    public void whenCoLocated_thenUseLocalNodeAndShardPreference() throws Exception {
        // Elasticsearch's _cat/nodes http_address field has no URI scheme.
        String address = "127.0.0.1:" + server.getAddress().getPort();
        List<Shard> shards = List.of(
                new Shard("my-index", 0, Prirep.p, 42, "STARTED", "127.0.0.1", address, "es1"),
                new Shard("my-index", 1, Prirep.p, 42, "STARTED", "127.0.0.1", address, "es1")
        );

        runProcessor(request -> DefaultTransportOptions.EMPTY, shards, false, true)
                .expectOutput(emptyList());

        String query = URLDecoder.decode(requests.get(0).query, StandardCharsets.UTF_8);
        assertThat(query).contains("preference=_shards:0,1|_only_local");
    }

    private TestSupport runProcessor(
            FunctionEx<co.elastic.clients.elasticsearch._types.RequestBase, TransportOptions> optionsFn,
            List<Shard> shards,
            boolean slicing,
            boolean coLocatedReading
    ) throws Exception {
        int port = server.getAddress().getPort();
        ElasticSourceConfiguration<String> configuration = new ElasticSourceConfiguration<>(
                () -> ElasticClients.client("127.0.0.1", port),
                () -> SearchRequest.of(request -> request.index("*")),
                optionsFn,
                hit -> String.valueOf(hit.source().to(Map.class).get("name")),
                slicing,
                coLocatedReading,
                KEEP_ALIVE,
                0);

        return TestSupport.verifyProcessor(() -> new ElasticSourceP<>(configuration, shards))
                          .disableSnapshots();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(),
                body,
                exchange.getRequestHeaders().getFirst("TestHeader")));

        String response;
        if (exchange.getRequestURI().getPath().equals("/_search/scroll")
                && exchange.getRequestMethod().equals("DELETE")) {
            response = "{\"succeeded\":true,\"num_freed\":1}";
        } else if (exchange.getRequestURI().getPath().equals("/_search/scroll")) {
            response = scrollRequests.getAndIncrement() == 0 && returnHits
                    ? searchResponse("Vladimir", 2)
                    : searchResponse(null, returnHits ? 2 : 0);
        } else {
            response = returnHits ? searchResponse("Frantisek", 2) : searchResponse(null, 0);
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String searchResponse(String name, int total) {
        String hit = name == null ? "" : "{\"_index\":\"my-index\",\"_id\":\"" + name
                + "\",\"_score\":1.0,\"_source\":{\"name\":\"" + name + "\"}}";
        return "{\"_scroll_id\":\"" + SCROLL_ID + "\",\"took\":1,\"timed_out\":false,"
                + "\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":" + total + ",\"relation\":\"eq\"},"
                + "\"max_score\":1.0,\"hits\":[" + hit + "]}}";
    }

    private record CapturedRequest(String method, String path, String query, String body, String testHeader) {
    }
}

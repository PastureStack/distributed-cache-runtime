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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.RequestBase;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Node;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.logging.ILogger;
import org.apache.hc.core5.http.HttpHost;

import javax.annotation.Nonnull;
import java.net.URISyntaxException;
import java.util.List;

import static com.hazelcast.jet.elastic.impl.RetryUtils.withRetry;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

final class ElasticSourceP<T> extends AbstractProcessor {

    private final ElasticSourceConfiguration<T> configuration;
    private final List<Shard> shards;
    private ElasticsearchClient client;
    private Rest5Client restClient;
    private ILogger logger;
    private Traverser<T> traverser;

    // Retained so the scroll can be released when processing ends or fails.
    private ElasticScrollTraverser scrollTraverser;

    ElasticSourceP(ElasticSourceConfiguration<T> configuration, List<Shard> shards) {
        this.configuration = configuration;
        this.shards = shards;
    }

    @Override
    protected void init(@Nonnull Context context) throws Exception {
        super.init(context);

        logger = context.logger();
        logger.fine("init");

        restClient = configuration.clientFn().get().build();
        client = new ElasticsearchClient(new Rest5ClientTransport(
                restClient, new JacksonJsonpMapper()));

        SearchRequest.Builder requestBuilder = configuration.searchRequestFn().get().rebuild()
                .scroll(scroll -> scroll.time(configuration.scrollKeepAlive()));

        if (configuration.isSlicingEnabled()) {
            int sliceId;
            int totalSlices;
            if (configuration.isCoLocatedReadingEnabled()) {
                sliceId = context.localProcessorIndex();
                totalSlices = context.localParallelism();
            } else {
                sliceId = context.globalProcessorIndex();
                totalSlices = context.totalParallelism();
            }
            if (totalSlices > 1) {
                logger.fine("Slice id=%s, max=%s", sliceId, totalSlices);
                requestBuilder.slice(slice -> slice.id(String.valueOf(sliceId)).max(totalSlices));
            }
        }

        if (configuration.isCoLocatedReadingEnabled()) {
            logger.fine("Assigned shards: %s", shards);
            if (shards.isEmpty()) {
                traverser = Traversers.empty();
                return;
            }

            restClient.setNodes(singleton(createLocalElasticNode()));
            String preference = "_shards:"
                    + shards.stream().map(shard -> String.valueOf(shard.getShard())).collect(joining(","))
                    + "|_only_local";
            requestBuilder.preference(preference);
        }

        scrollTraverser = new ElasticScrollTraverser(
                configuration, client, requestBuilder.build(), logger);
        traverser = scrollTraverser.map(configuration.mapToItemFn());
    }

    private Node createLocalElasticNode() throws URISyntaxException {
        List<String> ips = shards.stream().map(Shard::getHttpAddress).distinct().collect(toList());
        if (ips.size() != 1) {
            throw new JetException("Should receive shards from single local node, got: " + ips);
        }
        return new Node(HttpHost.create(ips.get(0)));
    }

    @Override
    public boolean isCooperative() {
        return false;
    }

    @Override
    public boolean complete() {
        return emitFromTraverser(traverser);
    }

    @Override
    public void close() {
        if (scrollTraverser != null) {
            scrollTraverser.close();
        }

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.fine("Could not close client", e);
            }
        }
    }

    static class ElasticScrollTraverser implements Traverser<Hit<JsonData>> {

        private final ILogger logger;
        private final ElasticsearchClient client;
        private final ElasticSourceConfiguration<?> configuration;
        private final String scrollKeepAlive;
        private final int retries;

        private List<Hit<JsonData>> hits;
        private int nextHit;
        private String scrollId;

        ElasticScrollTraverser(
                ElasticSourceConfiguration<?> configuration,
                ElasticsearchClient client,
                SearchRequest searchRequest,
                ILogger logger
        ) {
            this.configuration = configuration;
            this.client = client;
            this.scrollKeepAlive = configuration.scrollKeepAlive();
            this.retries = configuration.retries();
            this.logger = logger;

            try {
                SearchResponse<JsonData> response = withRetry(
                        () -> requestClient(searchRequest).search(searchRequest, JsonData.class), retries);

                HitsMetadata<JsonData> hitsMetadata = requireNonNull(
                        response.hits(), "null hits in the response");
                hits = hitsMetadata.hits();
                scrollId = response.scrollId();
                if (scrollId == null && !hits.isEmpty()) {
                    throw new IllegalStateException("Unexpected response: returned scrollId is null, but hits.size "
                            + "is not zero (" + hits.size() + "). Please file a bug.");
                }

                TotalHits totalHits = hitsMetadata.total();
                if (totalHits != null) {
                    logger.fine("Initialized scroll with scrollId " + scrollId + ", total results "
                            + totalHits.relation() + ", " + totalHits.value());
                }
            } catch (Exception e) {
                throw new JetException("Could not execute SearchRequest to Elastic", e);
            }
        }

        @Override
        public Hit<JsonData> next() {
            if (hits.isEmpty()) {
                scrollId = null;
                return null;
            }

            if (nextHit >= hits.size()) {
                try {
                    ScrollRequest request = ScrollRequest.of(scroll -> scroll
                            .scrollId(scrollId)
                            .scroll(time -> time.time(scrollKeepAlive)));
                    ScrollResponse<JsonData> response = withRetry(
                            () -> requestClient(request).scroll(request, JsonData.class), retries);
                    hits = response.hits().hits();
                    scrollId = response.scrollId();
                    if (hits.isEmpty()) {
                        return null;
                    }
                    nextHit = 0;
                } catch (Exception e) {
                    throw new JetException("Could not execute ScrollRequest to Elastic", e);
                }
            }

            return hits.get(nextHit++);
        }

        public void close() {
            if (scrollId != null) {
                clearScroll(scrollId);
                scrollId = null;
            }
        }

        private void clearScroll(String id) {
            ClearScrollRequest request = ClearScrollRequest.of(clear -> clear.scrollId(id));
            try {
                ClearScrollResponse response = withRetry(
                        () -> requestClient(request).clearScroll(request), retries);

                if (response.succeeded()) {
                    logger.fine("Succeeded clearing %s scrolls", response.numFreed());
                } else {
                    logger.warning("Clearing scroll " + id + " failed");
                }
            } catch (Exception e) {
                logger.fine("Could not clear scroll with scrollId=" + id, e);
            }
        }

        private ElasticsearchClient requestClient(RequestBase request) {
            TransportOptions options = configuration.optionsFn().apply(request);
            return options == null ? client : client.withTransportOptions(options);
        }
    }
}

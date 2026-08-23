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

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.pipeline.BatchSource;

import javax.annotation.Nonnull;

/**
 * Provides factory methods for Elasticsearch sources.
 * Alternatively you can use {@link ElasticSourceBuilder}
 *
 * @since Jet 4.2
 */
public final class ElasticSources {

    private ElasticSources() {
    }

    /**
     * Creates a source which queries local instance of Elasticsearch for all
     * documents
     * <p>
     * Useful for quick prototyping. See other methods
     * {@link #elastic(SupplierEx, SupplierEx, FunctionEx)} and
     * {@link #builder()}
     * <p>
     * For example:
     * <pre>{@code
     * pipeline.readFrom(ElasticSources.elastic());
     * }</pre>
     */
    @Nonnull
    public static BatchSource<String> elastic() {
        SupplierEx<Rest5ClientBuilder> client = ElasticClients::client;
        return elastic(client);
    }

    /**
     * Creates a source which queries Elasticsearch using client obtained from
     * {@link Rest5ClientBuilder} supplier function. Queries all indexes for all
     * documents. Maps each source document to JSON text.
     * function
     * <p>
     * For example:
     * <pre>{@code
     * pipeline.readFrom(ElasticSources.elastic(
     *   () -> ElasticClients.client("localhost", 9200),
     * ));
     * }</pre>
     *
     * @param clientFn supplier function returning configured RestClientBuilder
     */
    @Nonnull
    public static BatchSource<String> elastic(@Nonnull SupplierEx<Rest5ClientBuilder> clientFn) {
        return elastic(clientFn, hit -> hit.source().toJson().toString());
    }

    /**
     * Creates a source which queries local instance of Elasticsearch for all
     * documents. Uses provided {@code mapToItemFn} to map results.
     * <p>
     * For example:
     * <pre>{@code
     * pipeline.readFrom(ElasticSources.elastic(
     *   hit -> hit.source().to(Map.class)
     * ));
     * }</pre>
     *
     * @param mapToItemFn function mapping the result from a search hit to a
     *                    result type
     * @param <T>         result type returned by the map function
     */
    @Nonnull
    public static <T> BatchSource<T> elastic(@Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn) {
        return elastic(ElasticClients::client, mapToItemFn);
    }

    /**
     * Creates a source which queries Elasticsearch using client obtained from
     * {@link Rest5ClientBuilder} supplier function. Uses provided
     * {@code mapToItemFn} to map results. Queries all indexes for all
     * documents.
     * <p>
     * For example:
     * <pre>{@code
     * pipeline.readFrom(ElasticSources.elastic(
     *   () -> ElasticClients.client("localhost", 9200),
     *   hit -> hit.source().to(Map.class)
     * ));
     * }</pre>
     *
     * @param clientFn    supplier function returning configured
     *                    Rest5ClientBuilder
     * @param mapToItemFn function mapping the result from a search hit to a
     *                    result type
     * @param <T>         result type returned by the map function
     */
    @Nonnull
    public static <T> BatchSource<T> elastic(
            @Nonnull SupplierEx<Rest5ClientBuilder> clientFn,
            @Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn) {
        return elastic(clientFn, () -> SearchRequest.of(request -> request), mapToItemFn);
    }

    /**
     * Creates a source which queries Elasticsearch using a REST 5 client
     * builder supplied by the caller.
     * <p>
     * For example:
     * <pre>{@code
     * pipeline.readFrom(ElasticSources.elastic(
     *   () -> ElasticClients.client("localhost", 9200),
     *   () -> SearchRequest.of(request -> request.index("my-index")),
     *   hit -> hit.source().to(Map.class)
     * ));
     * }</pre>
     *
     * @param clientFn        supplier function returning configured
     *                        Rest5ClientBuilder
     * @param searchRequestFn supplier function of a SearchRequest used to
     *                        query for documents
     * @param mapToItemFn     function mapping the result from a search hit to a
     *                        result type
     * @param <T>             result type returned by the map function
     */
    @Nonnull
    public static <T> BatchSource<T> elastic(
            @Nonnull SupplierEx<Rest5ClientBuilder> clientFn,
            @Nonnull SupplierEx<SearchRequest> searchRequestFn,
            @Nonnull FunctionEx<? super Hit<JsonData>, T> mapToItemFn
    ) {
        return ElasticSources.builder()
                .clientFn(clientFn)
                .searchRequestFn(searchRequestFn)
                .mapToItemFn(mapToItemFn)
                .build();
    }

    /**
     * Returns new instance of {@link ElasticSourceBuilder}
     */
    @Nonnull
    public static ElasticSourceBuilder<Void> builder() {
        return new ElasticSourceBuilder<>();
    }

}

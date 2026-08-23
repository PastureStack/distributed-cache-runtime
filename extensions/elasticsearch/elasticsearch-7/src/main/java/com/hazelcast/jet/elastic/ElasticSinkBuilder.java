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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.RequestBase;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.TransportOptions;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.JetException;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.SinkBuilder;
import com.hazelcast.logging.ILogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hazelcast.jet.elastic.impl.RetryUtils.withRetry;
import static com.hazelcast.jet.impl.util.Util.checkNonNullAndSerializable;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Builder for a sink backed by the supported Elasticsearch Java API Client.
 * Items are mapped to {@link BulkOperation bulk operations} and sent using the
 * Elasticsearch bulk API.
 * <p>
 * Usage:
 * <pre>{@code
 * Sink<Map<String, ?>> elasticSink = new ElasticSinkBuilder<Map<String, ?>>()
 *   .clientFn(() -> ElasticClients.client(host, port))
 *   .mapToRequestFn(item -> BulkOperation.of(op -> op
 *       .index(index -> index.index("my-index").document(item))))
 *   .build();
 * }</pre>
 *
 * @param <T> type of input items
 * @since Jet 4.2
 */
public final class ElasticSinkBuilder<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_NAME = "elasticSink";
    private static final int DEFAULT_LOCAL_PARALLELISM = 2;
    private static final int DEFAULT_RETRIES = 5;

    private SupplierEx<Rest5ClientBuilder> clientFn;
    private SupplierEx<BulkRequest> bulkRequestFn =
            () -> BulkRequest.of(request -> request.operations(emptyList()));
    private FunctionEx<? super T, ? extends BulkOperation> mapToRequestFn;
    private FunctionEx<? super RequestBase, TransportOptions> optionsFn =
            request -> DefaultTransportOptions.EMPTY;
    private int retries = DEFAULT_RETRIES;

    /** Sets the serializable supplier of configured REST 5 client builders. */
    @Nonnull
    public ElasticSinkBuilder<T> clientFn(@Nonnull SupplierEx<Rest5ClientBuilder> clientFn) {
        this.clientFn = checkNonNullAndSerializable(clientFn, "clientFn");
        return this;
    }

    /**
     * Sets a supplier for immutable bulk-request settings. The connector adds
     * mapped operations to a fresh copy of the supplied request for each flush.
     */
    @Nonnull
    public ElasticSinkBuilder<T> bulkRequestFn(@Nonnull SupplierEx<BulkRequest> bulkRequestFn) {
        this.bulkRequestFn = checkNonNullAndSerializable(bulkRequestFn, "bulkRequestFn");
        return this;
    }

    /** Sets the required function that maps each item to a bulk operation. */
    @Nonnull
    @SuppressWarnings("unchecked")
    public <T_NEW> ElasticSinkBuilder<T_NEW> mapToRequestFn(
            @Nonnull FunctionEx<? super T_NEW, ? extends BulkOperation> mapToRequestFn
    ) {
        ElasticSinkBuilder<T_NEW> newThis = (ElasticSinkBuilder<T_NEW>) this;
        newThis.mapToRequestFn = checkNonNullAndSerializable(mapToRequestFn, "mapToRequestFn");
        return newThis;
    }

    /** Sets per-request transport options such as authorization headers. */
    @Nonnull
    public ElasticSinkBuilder<T> optionsFn(
            @Nonnull FunctionEx<? super RequestBase, TransportOptions> optionsFn
    ) {
        this.optionsFn = checkNonNullAndSerializable(optionsFn, "optionsFn");
        return this;
    }

    /** Sets the number of connector retries in addition to client retries. */
    @Nonnull
    public ElasticSinkBuilder<T> retries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("retries must be positive");
        }
        this.retries = retries;
        return this;
    }

    /** Creates the configured sink. */
    @Nonnull
    public Sink<T> build() {
        requireNonNull(clientFn, "clientFn is not set");
        requireNonNull(mapToRequestFn, "mapToRequestFn is not set");

        return SinkBuilder
                .sinkBuilder(DEFAULT_NAME, ctx -> new BulkContext(
                        newClient(clientFn.get()), bulkRequestFn, optionsFn,
                        retries, ctx.logger()))
                .<T>receiveFn((bulkContext, item) -> bulkContext.add(mapToRequestFn.apply(item)))
                .flushFn(BulkContext::flush)
                .destroyFn(BulkContext::close)
                .preferredLocalParallelism(DEFAULT_LOCAL_PARALLELISM)
                .build();
    }

    private static ElasticsearchClient newClient(Rest5ClientBuilder builder) {
        return new ElasticsearchClient(new Rest5ClientTransport(
                builder.build(), new JacksonJsonpMapper()));
    }

    static final class BulkContext {

        private final ElasticsearchClient client;
        private final SupplierEx<BulkRequest> bulkRequestSupplier;
        private final FunctionEx<? super RequestBase, TransportOptions> optionsFn;
        private final int retries;
        private final ILogger logger;

        private List<BulkOperation> operations = new ArrayList<>();

        BulkContext(
                ElasticsearchClient client,
                SupplierEx<BulkRequest> bulkRequestSupplier,
                FunctionEx<? super RequestBase, TransportOptions> optionsFn,
                int retries,
                ILogger logger
        ) {
            this.client = client;
            this.bulkRequestSupplier = bulkRequestSupplier;
            this.optionsFn = optionsFn;
            this.retries = retries;
            this.logger = logger;
        }

        void add(BulkOperation operation) {
            operations.add(operation);
        }

        void flush() {
            if (operations.isEmpty()) {
                return;
            }

            BulkRequest request = bulkRequestSupplier.get().rebuild()
                                                       .operations(operations)
                                                       .build();
            withRetry(
                    () -> {
                        TransportOptions options = optionsFn.apply(request);
                        ElasticsearchClient requestClient = options == null
                                ? client : client.withTransportOptions(options);
                        BulkResponse response = requestClient.bulk(request);
                        if (response.errors()) {
                            String failures = response.items().stream()
                                    .filter(item -> item.error() != null)
                                    .map(item -> item.operationType() + "[" + item.index() + "/" + item.id()
                                            + "]: " + item.error().reason())
                                    .collect(Collectors.joining("; "));
                            throw new JetException("Bulk request failed: " + failures);
                        }
                        if (logger.isFineEnabled()) {
                            logger.fine("BulkRequest with %s requests succeeded", operations.size());
                        }
                        return response;
                    },
                    retries,
                    IOException.class, JetException.class
            );
            operations = new ArrayList<>();
        }

        void close() throws IOException {
            logger.fine("Closing BulkContext");
            try {
                flush();
            } finally {
                client.close();
            }
        }
    }
}

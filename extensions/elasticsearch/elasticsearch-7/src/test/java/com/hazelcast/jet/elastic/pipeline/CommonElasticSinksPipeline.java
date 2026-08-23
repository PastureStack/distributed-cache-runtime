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

package com.hazelcast.jet.elastic.pipeline;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.elastic.CommonElasticSinksTest.TestItem;
import com.hazelcast.jet.elastic.ElasticSinkBuilder;
import com.hazelcast.jet.elastic.ElasticSinks;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.test.TestSources;

import static java.util.Collections.emptyList;

public final class CommonElasticSinksPipeline {

    private CommonElasticSinksPipeline() {
    }

    public static Pipeline writeItemsToIndexPipeline(
            String index,
            SupplierEx<Rest5ClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(CommonElasticSinksPipeline::immediateBulkRequest)
                .mapToRequestFn((TestItem item) -> BulkOperation.of(operation -> operation
                        .index(request -> request.index(index).document(item.asMap()))))
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline writeItemsToIndexUsingSourceFactoryMethodPipeline(
            String index,
            SupplierEx<Rest5ClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = ElasticSinks.elastic(
                elasticSupplier,
                item -> BulkOperation.of(operation -> operation
                        .index(request -> request.index(index).document(item.asMap())))
        );

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline updateItemsInIndexPipeline(
            String index,
            SupplierEx<Rest5ClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(CommonElasticSinksPipeline::immediateBulkRequest)
                .mapToRequestFn((TestItem item) -> BulkOperation.of(operation -> operation
                        .update(request -> request
                                .index(index)
                                .id(item.getId())
                                .action(action -> action.doc(item.asMap())))))
                .retries(0)
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static Pipeline deleteItemsFromIndexPipeline(
            String index,
            SupplierEx<Rest5ClientBuilder> elasticSupplier,
            TestItem... items) {
        Pipeline p = Pipeline.create();

        Sink<TestItem> elasticSink = new ElasticSinkBuilder<>()
                .clientFn(elasticSupplier)
                .bulkRequestFn(CommonElasticSinksPipeline::immediateBulkRequest)
                .mapToRequestFn((TestItem item) -> BulkOperation.of(operation -> operation
                        .delete(request -> request.index(index).id(item.getId()))))
                .build();

        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    private static BulkRequest immediateBulkRequest() {
        return BulkRequest.of(request -> request
                .refresh(Refresh.True)
                .operations(emptyList()));
    }
}

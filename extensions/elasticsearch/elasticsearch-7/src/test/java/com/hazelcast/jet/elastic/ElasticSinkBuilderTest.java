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

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.jet.pipeline.PipelineTestSupport;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URI;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Category({QuickTest.class, ParallelJVMTest.class})
public class ElasticSinkBuilderTest extends PipelineTestSupport {

    @Test
    public void when_writeToFailingSink_then_shouldCloseClient() {
        ClientHolder.elasticClients.clear();

        Sink<String> elasticSink = new ElasticSinkBuilder<>()
                .clientFn(() -> {
                    Rest5ClientBuilder builder = spy(Rest5Client.builder(URI.create("http://localhost:9200")));
                    when(builder.build()).thenAnswer(invocation -> {
                        Object result = invocation.callRealMethod();
                        Rest5Client client = (Rest5Client) result;
                        ClientHolder.elasticClients.add(client);
                        return client;
                    });
                    return builder;
                })
                .bulkRequestFn(() -> BulkRequest.of(request -> request
                        .refresh(Refresh.True)
                        .operations(emptyList())))
                .mapToRequestFn((String item) -> BulkOperation.of(operation -> operation
                        .index(request -> request.index("my-index").document(emptyMap()))))
                .retries(0)
                .build();

        p.readFrom(TestSources.items("a", "b", "c"))
         .writeTo(elasticSink);

        try {
            execute();
        } catch (Exception e) {
            // ignore - elastic is not running
        }

        ClientHolder.assertAllClientsNotRunning();
    }

}

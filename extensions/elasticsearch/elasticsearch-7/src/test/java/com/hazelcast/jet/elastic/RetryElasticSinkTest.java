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
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.elastic.CommonElasticSinksTest.TestItem;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.NightlyTest;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.apache.hc.core5.util.Timeout;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testcontainers.containers.ToxiproxyContainer;

import java.net.URI;

import static com.hazelcast.jet.TestedVersions.TOXIPROXY_IMAGE;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test running single Jet member locally and Elastic in docker
 */
@Category(NightlyTest.class)
public class RetryElasticSinkTest extends BaseElasticTest {

    @Rule
    public ToxiproxyContainer toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(ElasticSupport.network)
            .withNetworkAliases("toxiproxy");

    private final TestHazelcastFactory factory = new TestHazelcastFactory();

    @After
    @Override
    public void tearDown() {
        factory.terminateAll();
    }

    @Override
    protected HazelcastInstance createHazelcastInstance() {
        // This starts very quickly, no need to cache the instance
        return factory.newHazelcastInstance(config());
    }

    @Test
    public void when_elasticNotInitiallyAvailable_then_shouldWriteAllDocuments() throws Exception {
        int batchSize = 10_000;
        TestItem[] items = new TestItem[batchSize];
        for (int i = 0; i < batchSize; i++) {
            items[i] = new TestItem("id" + i, "name" + i);
        }

        final ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        final Proxy proxy = toxiproxyClient.createProxy("elastic", "0.0.0.0:8666", "elastic:9200");

        eu.rekawek.toxiproxy.model.toxic.Timeout timeout =
                proxy.toxics().timeout("timeout", ToxicDirection.UPSTREAM, 0);
        try {
            String address = toxiproxy.getHost();
            URI[] hosts = {URI.create("http://" + address + ':' + toxiproxy.getMappedPort(8666))};
            Job job = submitJobNoWait(
                    retryElasticSinkTestPipeline("my-index", hosts, 5000, items)
            );
            HazelcastTestSupport.sleepSeconds(10);
            timeout.remove();
            timeout = null;

            job.join();
            refreshIndex();

            SearchResponse<Void> response = elasticClient.search(
                    request -> request.index("my-index").size(0), Void.class);
            TotalHits totalHits = response.hits().total();
            assertThat(totalHits.value()).isEqualTo(batchSize);
        } finally {
            if (timeout != null) {
                timeout.remove();
            }
        }
    }

    public static Pipeline retryElasticSinkTestPipeline(
            String index,
            URI[] hosts,
            int elasticTimeout,
            TestItem... items) {
        Sink<TestItem> elasticSink = new ElasticSinkBuilder<>()
                .clientFn(elasticClientSupplier(hosts, elasticTimeout))
                .bulkRequestFn(() -> BulkRequest.of(request -> request
                        .refresh(Refresh.True)
                        .operations(emptyList())))
                .mapToRequestFn((TestItem item) -> BulkOperation.of(operation -> operation
                        .index(request -> request.index(index).document(item.asMap()))))
                .build();

        Pipeline p = Pipeline.create();
        p.readFrom(TestSources.items(items))
         .writeTo(elasticSink);

        return p;
    }

    public static SupplierEx<Rest5ClientBuilder> elasticClientSupplier(URI[] hosts, int elasticTimeout) {
        return () -> Rest5Client.builder(hosts).setRequestConfigCallback(
                requestConfigBuilder -> requestConfigBuilder
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(elasticTimeout))
                        .setConnectTimeout(Timeout.ofMilliseconds(elasticTimeout))
                        .setResponseTimeout(Timeout.ofMilliseconds(elasticTimeout)));
    }

}

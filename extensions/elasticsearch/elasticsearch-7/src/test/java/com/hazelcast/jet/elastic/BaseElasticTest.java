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
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.hazelcast.collection.IList;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.test.IgnoreInJenkinsOnWindows;
import com.hazelcast.jet.test.SerialTest;
import com.hazelcast.test.HazelcastSerialClassRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hazelcast.test.DockerTestUtil.assumeDockerEnabled;
import static com.hazelcast.test.HazelcastTestSupport.smallInstanceConfig;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for running Elasticsearch connector tests
 *
 * To use implement:
 * - {@link #elasticClientSupplier()}
 * - {@link #createHazelcastInstance()}
 * Subclasses are free to cache
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category({SerialTest.class, IgnoreInJenkinsOnWindows.class})
public abstract class BaseElasticTest {

    protected static final int BATCH_SIZE = 42;

    protected ElasticsearchClient elasticClient;
    protected HazelcastInstance hz;
    protected IList<String> results;

    @BeforeClass
    public static void beforeClassCheckDocker() {
        assumeDockerEnabled();
    }

    @Before
    public void setUpBase() {
        if (elasticClient == null) {
            elasticClient = new ElasticsearchClient(new Rest5ClientTransport(
                    elasticClientSupplier().get().build(), new JacksonJsonpMapper()));
        }
        cleanElasticData();

        if (hz == null) {
            hz = createHazelcastInstance();
        }
        results = hz.getList("results");
        results.clear();
    }

    @After
    public void tearDown() throws Exception {
        if (elasticClient != null) {
            try {
                elasticClient.close();
            } finally {
                elasticClient = null;
            }
        }
    }

    /**
     * REST 5 client builder supplier, used to create a client before each
     * test for use by all methods from this class interacting with elastic
     */
    protected SupplierEx<Rest5ClientBuilder> elasticClientSupplier() {
        return ElasticSupport.elasticClientSupplier();
    }

    /**
     * REST 5 client builder supplier, used as a parameter of
     * {@link ElasticSourceBuilder#clientFn(SupplierEx)}
     */
    protected SupplierEx<Rest5ClientBuilder> elasticPipelineClientSupplier() {
        return ElasticSupport.elasticClientSupplier();
    }

    protected abstract HazelcastInstance createHazelcastInstance();

    /**
     * Creates an index with given name with 3 shards
     */
    protected void initShardedIndex(String index) throws IOException {
        createShardedIndex(index, 3, 0);
        indexBatchOfDocuments(index);
    }

    /**
     * Creates an index with given name with 3 shards
     */
    protected void createShardedIndex(String index, int shards, int replicas) throws IOException {
        elasticClient.indices().create(request -> request
                .index(index)
                .settings(settings -> settings
                        .numberOfShards(String.valueOf(shards))
                        .numberOfReplicas(String.valueOf(replicas))
                        .otherSettings("index.unassigned.node_left.delayed_timeout",
                                co.elastic.clients.json.JsonData.of("1s"))));
    }

    /**
     * Deletes all documents in all indexes and drops all indexes
     */
    protected void cleanElasticData() {
        try {
            // Elasticsearch 9 keeps destructive wildcard deletion disabled by
            // default. Resolve regular test indexes first, then delete only the
            // explicit names instead of weakening that server-side safeguard.
            List<String> indexes = List.copyOf(elasticClient.indices().get(request -> request
                    .index("*")
                    .allowNoIndices(true)
                    .ignoreUnavailable(true)).indices().keySet());
            if (!indexes.isEmpty()) {
                elasticClient.indices().delete(request -> request.index(indexes));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes all documents in all indexes
     */
    protected void deleteDocuments() throws IOException {
        elasticClient.deleteByQuery(request -> request
                .index("*")
                .query(query -> query.matchAll(matchAll -> matchAll))
                .refresh(true));
    }

    /**
     * Indexes a batch of documents to an index with given name
     */
    protected List<String> indexBatchOfDocuments(String index) {
        return indexBatchOfDocuments(index, CommonElasticSourcesTest.BATCH_SIZE);
    }

    /**
     * Indexes a batch of documents to an index with given name
     */
    protected List<String> indexBatchOfDocuments(String index, int batchSize) {
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            docs.add(Map.of("title", "document " + i));
        }
        return indexDocuments(index, docs);
    }

    /**
     * Indexes a given document to an index with given name
     */
    protected String indexDocument(String index, Map<String, Object> document) {
        return indexDocuments(index, List.of(document)).get(0);
    }

    /**
     * Indexes a given list of documents to an index with given name
     */
    protected List<String> indexDocuments(String index, List<Map<String, Object>> documents) {
        BulkRequest.Builder request = new BulkRequest.Builder().refresh(Refresh.True);
        for (Map<String, Object> document : documents) {
            request.operations(BulkOperation.of(operation -> operation
                    .index(item -> item.index(index).document(document))));
        }

        try {
            BulkResponse response = elasticClient.bulk(request.build());
            return response.items().stream().map(item -> item.id()).toList();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void refreshIndex() throws IOException {
        // Need to refresh index because the default bulk request doesn't do it and we may not see the result
        elasticClient.indices().refresh(request -> request.index("my-index"));
    }

    protected void assertSingleDocument() throws IOException {
        assertSingleDocument("id", "Frantisek");
    }

    protected void assertSingleDocument(String id, String name) throws IOException {
        SearchResponse<Map> response = elasticClient.search(
                request -> request.index("my-index"), Map.class);
        List<Hit<Map>> hits = response.hits().hits();
        assertThat(hits).hasSize(1);
        Map<String, Object> document = hits.get(0).source();
        assertThat(document).contains(
                entry("id", id),
                entry("name", name)
        );
    }

    protected void assertNoDocuments(String index) throws IOException {
        SearchResponse<Map> response = elasticClient.search(
                request -> request.index(index), Map.class);
        List<Hit<Map>> hits = response.hits().hits();
        assertThat(hits).hasSize(0);
    }

    /**
     * Creates a new job from given Pipeline
     *
     * Adds this.getClass to config so any lambdas used in a test class can be deserialized when run in remote cluster.
     */
    protected void submitJob(Pipeline p) {
        Job job = submitJobNoWait(p);
        job.join();
    }

    protected Job submitJobNoWait(Pipeline p) {
        JobConfig config = new JobConfig();

        Class<?> clazz = this.getClass();
        while (clazz.getSuperclass() != null) {
            config.addClass(clazz);
            clazz = clazz.getSuperclass();
        }

        return hz.getJet().newJob(p, config);
    }

    protected static Config config() {
        Config config = smallInstanceConfig();
        config.getJetConfig().setResourceUploadEnabled(true);
        return config;
    }
}

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
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.annotation.Nonnull;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ElasticSourceBuilderTest {

    @Test
    public void when_createElasticSourceUsingBuilder_then_sourceHasCorrectName() {
        BatchSource<Object> source = builderWithRequiredParams()
                .build();
        assertThat(source.name()).isEqualTo("elasticSource");
    }

    @Nonnull
    private ElasticSourceBuilder<Object> builderWithRequiredParams() {
        return new ElasticSourceBuilder<>()
                .clientFn(() -> Rest5Client.builder(URI.create("http://localhost:9200")))
                .searchRequestFn(() -> SearchRequest.of(request -> request))
                .mapToItemFn(FunctionEx.identity());
    }

    @Test
    public void when_createElasticSourceWithoutClientSupplier_then_throwException() {
        assertThatThrownBy(() -> new ElasticSourceBuilder<>()
                .searchRequestFn(() -> SearchRequest.of(request -> request))
                .mapToItemFn(FunctionEx.identity())
                .build())
                .hasMessage("clientFn must be set");
    }

    @Test
    public void when_createElasticSourceWithoutSearchRequestSupplier_then_throwException() {
        assertThatThrownBy(() -> new ElasticSourceBuilder<>()
                .clientFn(() -> Rest5Client.builder(URI.create("http://localhost:9200")))
                .mapToItemFn(FunctionEx.identity())
                .build())
                .hasMessage("searchRequestFn must be set");
    }

    @Test
    public void when_createElasticSourceWithoutMapHitFnSupplier_then_throwException() {
        assertThatThrownBy(() -> new ElasticSourceBuilder<>()
                .clientFn(() -> Rest5Client.builder(URI.create("http://localhost:9200")))
                .searchRequestFn(() -> SearchRequest.of(request -> request))
                .build())
                .hasMessage("mapToItemFn must be set");
    }

}

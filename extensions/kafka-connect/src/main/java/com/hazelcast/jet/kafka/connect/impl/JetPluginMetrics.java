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

package com.hazelcast.jet.kafka.connect.impl;

import com.hazelcast.core.HazelcastException;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.PluginMetrics;
import org.apache.kafka.common.metrics.internals.PluginMetricsImpl;

import java.io.IOException;
import java.util.Map;

/** Owns the Kafka Connect 4 plugin metrics registry and its lifecycle. */
final class JetPluginMetrics implements AutoCloseable {
    private final Metrics metrics = new Metrics();
    private final PluginMetricsImpl pluginMetrics;

    JetPluginMetrics(Map<String, String> tags) {
        pluginMetrics = new PluginMetricsImpl(metrics, tags);
    }

    PluginMetrics pluginMetrics() {
        return pluginMetrics;
    }

    @Override
    public void close() {
        try {
            pluginMetrics.close();
        } catch (IOException e) {
            throw new HazelcastException("Failed to close Kafka Connect plugin metrics", e);
        } finally {
            metrics.close();
        }
    }
}

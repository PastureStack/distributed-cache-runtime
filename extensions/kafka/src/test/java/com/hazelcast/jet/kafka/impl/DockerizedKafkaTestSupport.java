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

package com.hazelcast.jet.kafka.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

class DockerizedKafkaTestSupport extends KafkaTestSupport {
    // Keep aligned with hazelcast-parent/pom.xml confluent.version.
    private static final String TEST_KAFKA_VERSION = System.getProperty("test.kafka.version", "8.3.1");
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerizedKafkaTestSupport.class);

    private ConfluentKafkaContainer kafkaContainer;

    @Override
    protected String createKafkaCluster0() throws IOException {
        kafkaContainer = new ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka").withTag(TEST_KAFKA_VERSION))
                .withLogConsumer(new Slf4jLogConsumer(LOGGER));
        kafkaContainer.start();

        return kafkaContainer.getBootstrapServers();
    }

    @Override
    protected void shutdownKafkaCluster0() {
        if (kafkaContainer != null) {
            kafkaContainer.stop();
            kafkaContainer = null;
        }
    }
}

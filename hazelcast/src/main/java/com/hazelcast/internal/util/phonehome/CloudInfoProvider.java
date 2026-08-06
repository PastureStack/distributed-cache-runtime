/*
 * Copyright (c) 2008-2026, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.util.phonehome;

import com.hazelcast.instance.impl.Node;
import com.hazelcast.spi.properties.HazelcastProperties;
import com.hazelcast.spi.properties.HazelcastProperty;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.hazelcast.internal.util.phonehome.PhoneHomeMetrics.CLOUD;
import static com.hazelcast.internal.util.phonehome.PhoneHomeMetrics.DOCKER;
import static com.hazelcast.internal.util.phonehome.PhoneHomeMetrics.VIRIDIAN;

/**
 * Provides information about cloud deployment
 */
class CloudInfoProvider implements MetricsProvider {
    static final HazelcastProperty KUBERNETES_TOKEN_PATH = new HazelcastProperty(
            "hazelcast.phonehome.path.kubernetes.token", "/var/run/secrets/kubernetes.io/serviceaccount/token");
    static final HazelcastProperty DOCKER_FILE_PATH = new HazelcastProperty(
            "hazelcast.phonehome.path.docker.file", "/.dockerenv");

    static final String CLOUD_ENVIRONMENT_ENV_VAR = "HZ_CLOUD_ENVIRONMENT";

    private volatile Map<Metric, String> environmentInfo;

    @Override
    public void provideMetrics(Node node, MetricsCollectionContext context) {
        if (environmentInfo != null) {
            environmentInfo.forEach(context::collect);
            return;
        }

        HazelcastProperties props = node.getProperties();
        Map<Metric, String> info = new HashMap<>(2);

        info.put(CLOUD, detectCloud(System.getenv()));

        try {
            Paths.get(props.getString(DOCKER_FILE_PATH)).toRealPath();
            try {
                Paths.get(props.getString(KUBERNETES_TOKEN_PATH)).toRealPath();
                info.put(DOCKER, "K");
            } catch (IOException e) {
                info.put(DOCKER, "D");
            }
        } catch (IOException e) {
            info.put(DOCKER, "N");
        }

        String cloudEnv = System.getenv(CLOUD_ENVIRONMENT_ENV_VAR);
        if (cloudEnv != null) {
            info.put(VIRIDIAN, cloudEnv);
        }

        environmentInfo = info;
        environmentInfo.forEach(context::collect);
    }

    static String detectCloud(Map<String, String> environment) {
        if (hasEnvironmentMarker(environment, "AWS_EXECUTION_ENV", "AWS_LAMBDA_FUNCTION_NAME",
                "ECS_CONTAINER_METADATA_URI", "ECS_CONTAINER_METADATA_URI_V4")) {
            return "A";
        }
        if (hasEnvironmentMarker(environment, "WEBSITE_INSTANCE_ID", "FUNCTIONS_WORKER_RUNTIME",
                "IDENTITY_ENDPOINT", "MSI_ENDPOINT")) {
            return "Z";
        }
        if (hasEnvironmentMarker(environment, "K_SERVICE", "FUNCTION_TARGET", "GCE_METADATA_HOST",
                "GOOGLE_CLOUD_PROJECT")) {
            return "G";
        }
        return "N";
    }

    private static boolean hasEnvironmentMarker(Map<String, String> environment, String... names) {
        for (String name : names) {
            String value = environment.get(name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}

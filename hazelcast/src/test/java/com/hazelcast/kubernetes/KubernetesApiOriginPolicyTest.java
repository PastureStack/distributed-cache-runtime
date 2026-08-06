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

package com.hazelcast.kubernetes;

import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.hazelcast.kubernetes.KubernetesApiOriginPolicy.ALLOWED_ORIGINS_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class KubernetesApiOriginPolicyTest {

    @After
    public void clearAllowlist() {
        System.clearProperty(ALLOWED_ORIGINS_PROPERTY);
    }

    @Test
    public void acceptsCanonicalInClusterOriginByDefault() {
        assertThat(KubernetesApiOriginPolicy.requireAllowed("https://KUBERNETES.DEFAULT.SVC"))
                .isEqualTo("https://kubernetes.default.svc:443");
    }

    @Test
    public void customOriginRequiresExactExplicitAllowlistEntry() {
        String trusted = "https://api.cluster.example:6443";

        assertThatThrownBy(() -> KubernetesApiOriginPolicy.requireAllowed(trusted))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("not allowed");

        System.setProperty(ALLOWED_ORIGINS_PROPERTY, trusted);
        assertThat(KubernetesApiOriginPolicy.requireAllowed(trusted)).isEqualTo(trusted);
        assertThatThrownBy(() -> KubernetesApiOriginPolicy.requireAllowed("https://api.cluster.example.attacker:6443"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    public void rejectsOriginConfusionAndNonOriginInput() {
        System.setProperty(ALLOWED_ORIGINS_PROPERTY, "https://api.cluster.example:6443");
        String[] rejected = {
                "https://api.cluster.example:6443@169.254.169.254",
                "https://api.cluster.example:6443/api",
                "https://api.cluster.example:6443?target=internal",
                "https://api.cluster.example:6443#fragment",
                "file:///etc/passwd"
        };

        for (String candidate : rejected) {
            assertThatThrownBy(() -> KubernetesApiOriginPolicy.requireAllowed(candidate))
                    .as(candidate)
                    .isInstanceOf(InvalidConfigurationException.class);
        }
    }
}

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

package com.hazelcast.spi.discovery.multicast;

import com.hazelcast.config.properties.ValidationException;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@Category(QuickTest.class)
public class MulticastDiscoveryStrategyTest {

    @Test
    public void acceptsNumericMulticastAddresses() {
        InetAddress ipv4 = MulticastDiscoveryStrategy.resolveMulticastGroup("224.2.2.3");
        InetAddress ipv6 = MulticastDiscoveryStrategy.resolveMulticastGroup("ff02::1");

        assertEquals("224.2.2.3", ipv4.getHostAddress());
        assertTrue(ipv4.isMulticastAddress());
        assertTrue(ipv6.isMulticastAddress());
    }

    @Test
    public void rejectsUnicastLoopbackAndHostnamesBeforeSocketCreation() {
        String[] invalidGroups = {
                "127.0.0.1",
                "10.0.0.1",
                "::1",
                "localhost",
                "metadata.google.internal",
                "224.2.2.999",
                "224.2.2.3 "
        };

        for (String group : invalidGroups) {
            assertThrows(group, ValidationException.class,
                    () -> MulticastDiscoveryStrategy.resolveMulticastGroup(group));
        }
    }
}

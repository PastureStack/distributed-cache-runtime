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

package com.hazelcast.internal.util;

import com.hazelcast.function.SupplierEx;

import java.net.InetAddress;

/**
 * HostnameUtil contains hostname helper methods
 */
public final class HostnameUtil {

    private HostnameUtil() {
    }

    /**
     * Resolves local hostname
     *
     * @return local hostname or null if it can't be resolved
     */
    public static String getLocalHostname() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null) {
            hostname = getOrNull(() -> InetAddress.getLocalHost().getHostName());
        }
        return shortHostname(hostname);
    }

    private static String shortHostname(String hostname) {
        if (hostname == null) {
            return null;
        }
        if (hostname.contains(".")) {
            hostname = hostname.substring(0, hostname.indexOf("."));
        }
        return hostname;
    }

    private static String getOrNull(SupplierEx<String> supplierEx) {
        try {
            return supplierEx.getEx();
        } catch (Exception e) {
            return null;
        }
    }
}

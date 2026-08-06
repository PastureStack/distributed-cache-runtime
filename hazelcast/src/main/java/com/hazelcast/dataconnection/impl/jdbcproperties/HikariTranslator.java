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

package com.hazelcast.dataconnection.impl.jdbcproperties;

import com.zaxxer.hikari.HikariConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class HikariTranslator {

    private static final String HIKARI_PREFIX = "hikari.";
    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();
    private static final Set<String> ALLOWED_HIKARI_PROPERTIES = Set.of(
            "connectionInitSql",
            "connectionTestQuery",
            "initializationFailTimeout",
            "isolateInternalQueries",
            "leakDetectionThreshold",
            "readOnly",
            "registerMbeans",
            "validationTimeout"
    );
    private static final Map<String, BiConsumer<HikariConfig, String>> HIKARI_PROPERTY_APPLIERS = Map.ofEntries(
            Map.entry("connectionTimeout", (config, value) -> config.setConnectionTimeout(Long.parseLong(value))),
            Map.entry("idleTimeout", (config, value) -> config.setIdleTimeout(Long.parseLong(value))),
            Map.entry("keepaliveTime", (config, value) -> config.setKeepaliveTime(Long.parseLong(value))),
            Map.entry("maxLifetime", (config, value) -> config.setMaxLifetime(Long.parseLong(value))),
            Map.entry("minimumIdle", (config, value) -> config.setMinimumIdle(Integer.parseInt(value))),
            Map.entry("maximumPoolSize", (config, value) -> config.setMaximumPoolSize(Integer.parseInt(value))),
            Map.entry("connectionInitSql", HikariConfig::setConnectionInitSql),
            Map.entry("connectionTestQuery", HikariConfig::setConnectionTestQuery),
            Map.entry("initializationFailTimeout",
                    (config, value) -> config.setInitializationFailTimeout(Long.parseLong(value))),
            Map.entry("isolateInternalQueries",
                    (config, value) -> config.setIsolateInternalQueries(Boolean.parseBoolean(value))),
            Map.entry("leakDetectionThreshold",
                    (config, value) -> config.setLeakDetectionThreshold(Long.parseLong(value))),
            Map.entry("readOnly", (config, value) -> config.setReadOnly(Boolean.parseBoolean(value))),
            Map.entry("registerMbeans", (config, value) -> config.setRegisterMbeans(Boolean.parseBoolean(value))),
            Map.entry("validationTimeout", (config, value) -> config.setValidationTimeout(Long.parseLong(value)))
    );

    private final AtomicInteger poolCounter;
    private final String name;

    // The translation from HZ to Hikari properties
    static {
        PROPERTY_MAP.put(DataConnectionProperties.CONNECTION_TIMEOUT, "connectionTimeout");
        PROPERTY_MAP.put(DataConnectionProperties.IDLE_TIMEOUT, "idleTimeout");
        PROPERTY_MAP.put(DataConnectionProperties.KEEP_ALIVE_TIME, "keepaliveTime");
        PROPERTY_MAP.put(DataConnectionProperties.MAX_LIFETIME, "maxLifetime");
        PROPERTY_MAP.put(DataConnectionProperties.MINIMUM_IDLE, "minimumIdle");
        PROPERTY_MAP.put(DataConnectionProperties.MAXIMUM_POOL_SIZE, "maximumPoolSize");
    }

    public HikariTranslator(AtomicInteger poolCounter, String name) {
        this.poolCounter = poolCounter;
        this.name = name;
    }

    public Properties translate(Properties source) {
        Properties hikariProperties = new Properties();

        // Iterate over source Properties and translate from HZ to Hikari
        source.forEach((key, value) -> {
            String keyString = (String) key;
            if (DataConnectionProperties.JDBC_URL.equals(keyString)) {
                // The endpoint is installed explicitly after this untrusted property bag is translated.
                return;
            } else if (PROPERTY_MAP.containsKey(keyString)) {
                hikariProperties.put(keyString, value);
            } else if (keyString.startsWith(HIKARI_PREFIX)) {
                String keyNoPrefix = keyString.substring(HIKARI_PREFIX.length());
                if (PROPERTY_MAP.containsValue(keyNoPrefix) || ALLOWED_HIKARI_PROPERTIES.contains(keyNoPrefix)) {
                    hikariProperties.put(keyNoPrefix, value);
                }
            } else {
                hikariProperties.put("dataSource." + keyString, value);
            }
        });

        int cnt = poolCounter.getAndIncrement();
        hikariProperties.put("poolName", "HikariPool-" + cnt + "-" + name);

        return hikariProperties;
    }

    /**
     * Applies supported pool settings without passing the untrusted property bag to Hikari's generic
     * {@code Properties} constructor. The JDBC endpoint is installed separately after URL policy validation.
     */
    public HikariConfig translateToConfig(Properties source) {
        HikariConfig config = new HikariConfig();

        source.forEach((key, value) -> {
            String keyString = (String) key;
            if (DataConnectionProperties.JDBC_URL.equals(keyString)) {
                return;
            }
            if (PROPERTY_MAP.containsKey(keyString)) {
                applyHikariProperty(config, PROPERTY_MAP.get(keyString), value);
                return;
            }
            if (keyString.startsWith(HIKARI_PREFIX)) {
                String hikariName = keyString.substring(HIKARI_PREFIX.length());
                if (PROPERTY_MAP.containsValue(hikariName) || ALLOWED_HIKARI_PROPERTIES.contains(hikariName)) {
                    applyHikariProperty(config, hikariName, value);
                }
                return;
            }
            config.addDataSourceProperty(keyString, value);
        });

        config.setPoolName(nextPoolName());
        return config;
    }

    private static void applyHikariProperty(HikariConfig config, String propertyName, Object value) {
        BiConsumer<HikariConfig, String> applier = HIKARI_PROPERTY_APPLIERS.get(propertyName);
        if (applier == null) {
            throw new IllegalArgumentException("Unsupported Hikari property: " + propertyName);
        }
        applier.accept(config, String.valueOf(value));
    }

    private String nextPoolName() {
        return "HikariPool-" + poolCounter.getAndIncrement() + "-" + name;
    }
}

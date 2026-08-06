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

package com.hazelcast.dataconnection.impl;

import com.hazelcast.core.HazelcastException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hazelcast.dataconnection.impl.jdbcproperties.DataConnectionProperties.JDBC_URL;

/**
 * Enforces the trust boundary between declarative data-connection properties and JDBC network access.
 */
final class JdbcUrlPolicy {

    static final String ALLOWED_URL_PROPERTY_PREFIX = "hazelcast.jdbc.allowed-url.";

    private static final int MAX_JDBC_URL_LENGTH = 4_096;
    private static final Pattern INDEXED_ALLOWLIST_PROPERTY = Pattern.compile(
            Pattern.quote(ALLOWED_URL_PROPERTY_PREFIX) + "[0-9]+");
    private static final Pattern SAFE_LOCAL_H2_URL = Pattern.compile(
            "\\Ajdbc:h2:mem:([A-Za-z0-9._-]{1,128})(?:;DB_CLOSE_DELAY=(-1|0|[1-9][0-9]{0,8}))?\\z");
    private static final Set<String> ENDPOINT_PROPERTY_NAMES = Set.of(
            "url",
            "jdbcurl",
            "server",
            "servername",
            "host",
            "hostname",
            "port",
            "portnumber",
            "databasename",
            "datasource",
            "datasourceclassname",
            "driverclassname"
    );

    private JdbcUrlPolicy() {
    }

    static String requireAllowed(Properties properties, String connectionName) {
        rejectEndpointOverrides(properties, connectionName);

        Object configuredUrl = properties.get(JDBC_URL);
        if (!(configuredUrl instanceof String)) {
            throw new HazelcastException(JDBC_URL + " property is not defined for data connection '"
                    + connectionName + "'");
        }
        String candidate = (String) configuredUrl;
        requireWellFormed(candidate, "JDBC URL", connectionName);

        Matcher localH2 = SAFE_LOCAL_H2_URL.matcher(candidate);
        if (localH2.matches()) {
            String closeDelay = localH2.group(2);
            return "jdbc:h2:mem:" + localH2.group(1)
                    + (closeDelay == null ? "" : ";DB_CLOSE_DELAY=" + closeDelay);
        }

        Properties systemProperties = System.getProperties();
        List<String> allowedUrls = new ArrayList<>();
        for (String key : systemProperties.stringPropertyNames()) {
            if (!INDEXED_ALLOWLIST_PROPERTY.matcher(key).matches()) {
                continue;
            }
            String allowed = systemProperties.getProperty(key);
            requireWellFormed(allowed, key, connectionName);
            allowedUrls.add(allowed);
        }
        for (String allowed : allowedUrls) {
            if (allowed.equals(candidate)) {
                return allowed;
            }
        }

        throw new HazelcastException("JDBC URL is not allowed for data connection '" + connectionName
                + "'. Add the exact URL to an indexed " + ALLOWED_URL_PROPERTY_PREFIX + "<n> system property");
    }

    private static void rejectEndpointOverrides(Properties properties, String connectionName) {
        for (Object key : properties.keySet()) {
            if (!(key instanceof String)) {
                throw new HazelcastException("JDBC property names must be strings for data connection '"
                        + connectionName + "'");
            }
            String propertyName = (String) key;
            if (JDBC_URL.equals(propertyName)) {
                continue;
            }
            String normalized = stripEndpointPrefixes(propertyName.toLowerCase(Locale.ROOT));
            if (ENDPOINT_PROPERTY_NAMES.contains(normalized)) {
                throw new HazelcastException("JDBC endpoint override property '" + propertyName
                        + "' is not allowed for data connection '" + connectionName + "'");
            }
        }
    }

    private static String stripEndpointPrefixes(String value) {
        String previous;
        do {
            previous = value;
            value = stripPrefix(value, "hikari.");
            value = stripPrefix(value, "datasource.");
        } while (!value.equals(previous));
        return value;
    }

    private static String stripPrefix(String value, String prefix) {
        while (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        return value;
    }

    private static void requireWellFormed(String url, String source, String connectionName) {
        if (url == null || url.isBlank()) {
            throw invalidUrl(source, connectionName);
        }
        if (!url.equals(url.trim()) || url.length() > MAX_JDBC_URL_LENGTH) {
            throw invalidUrl(source, connectionName);
        }
        if (!url.startsWith("jdbc:") || containsControlCharacter(url)) {
            throw invalidUrl(source, connectionName);
        }
    }

    private static HazelcastException invalidUrl(String source, String connectionName) {
        return new HazelcastException(
                "Invalid " + source + " for data connection '" + connectionName + "'");
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}

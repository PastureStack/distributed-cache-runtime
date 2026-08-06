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
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Properties;

import static com.hazelcast.dataconnection.impl.JdbcUrlPolicy.ALLOWED_URL_PROPERTY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class JdbcUrlPolicyTest {

    private static final String CONNECTION_NAME = "test-connection";

    @After
    public void clearAllowlist() {
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith(ALLOWED_URL_PROPERTY_PREFIX))
                .forEach(System::clearProperty);
    }

    @Test
    public void acceptsOnlyConstrainedLocalH2ByDefault() {
        assertThat(allowed("jdbc:h2:mem:test_db"))
                .isEqualTo("jdbc:h2:mem:test_db");
        assertThat(allowed("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1"))
                .isEqualTo("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1");
    }

    @Test
    public void rejectsRemoteAndExecutableH2UrlsByDefault() {
        String[] rejectedUrls = {
                "jdbc:postgresql://169.254.169.254:5432/secrets",
                "jdbc:h2:tcp://169.254.169.254:9092/secrets",
                "jdbc:h2:mem:test;INIT=RUNSCRIPT FROM 'http://169.254.169.254/latest/meta-data'",
                "jdbc:h2:file:./test",
                "jdbc:h2:mem:test;AUTO_SERVER=TRUE",
                " jdbc:h2:mem:test",
                "jdbc:h2:mem:test\n"
        };

        for (String url : rejectedUrls) {
            assertThatThrownBy(() -> allowed(url))
                    .as(url)
                    .isInstanceOf(HazelcastException.class);
        }
    }

    @Test
    public void acceptsOnlyExactIndexedAllowlistValue() {
        String trusted = "jdbc:postgresql://database.example.com:5432/application?sslmode=verify-full";
        System.setProperty(ALLOWED_URL_PROPERTY_PREFIX + '0', trusted);

        assertThat(allowed(trusted)).isSameAs(System.getProperty(ALLOWED_URL_PROPERTY_PREFIX + '0'));
        assertThatThrownBy(() -> allowed(trusted + ".attacker.example"))
                .isInstanceOf(HazelcastException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    public void invalidAllowlistEntryFailsClosedEvenWhenAnotherEntryMatches() {
        String trusted = "jdbc:postgresql://database.example.com:5432/application";
        System.setProperty(ALLOWED_URL_PROPERTY_PREFIX + '0', trusted);
        System.setProperty(ALLOWED_URL_PROPERTY_PREFIX + '1', "not-a-jdbc-url");

        assertThatThrownBy(() -> allowed(trusted))
                .isInstanceOf(HazelcastException.class)
                .hasMessageContaining(ALLOWED_URL_PROPERTY_PREFIX + '1');
    }

    @Test
    public void rejectsAlternateEndpointProperties() {
        String[] endpointOverrides = {
                "url",
                "serverName",
                "portNumber",
                "databaseName",
                "hikari.jdbcUrl",
                "hikari.dataSourceClassName",
                "hikari.driverClassName",
                "hikari.dataSource.serverName",
                "dataSource.hikari.jdbcUrl",
                "hikari.dataSource.hikari.serverName",
                "dataSource.host"
        };

        for (String key : endpointOverrides) {
            Properties properties = properties("jdbc:h2:mem:test");
            properties.setProperty(key, "169.254.169.254");

            assertThatThrownBy(() -> JdbcUrlPolicy.requireAllowed(properties, CONNECTION_NAME))
                    .as(key)
                    .isInstanceOf(HazelcastException.class)
                    .hasMessageContaining("endpoint override");
        }
    }

    @Test
    public void acceptsNonEndpointDriverAndPoolProperties() {
        Properties properties = properties("jdbc:h2:mem:test");
        properties.setProperty("user", "application");
        properties.setProperty("password", "secret");
        properties.setProperty("hikari.maximumPoolSize", "5");

        assertThat(JdbcUrlPolicy.requireAllowed(properties, CONNECTION_NAME))
                .isEqualTo("jdbc:h2:mem:test");
    }

    private static String allowed(String jdbcUrl) {
        return JdbcUrlPolicy.requireAllowed(properties(jdbcUrl), CONNECTION_NAME);
    }

    private static Properties properties(String jdbcUrl) {
        Properties properties = new Properties();
        properties.setProperty("jdbcUrl", jdbcUrl);
        return properties;
    }
}

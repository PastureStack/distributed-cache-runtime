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

package com.hazelcast.jet.sql;

import com.hazelcast.test.HazelcastSerialClassRunner;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static java.util.Arrays.asList;

@RunWith(HazelcastSerialClassRunner.class)
public class SqlStatefulDagTest extends SqlTestSupport {
    private static FileSystem fileSystem;
    private static Path testRoot;

    @BeforeClass
    public static void setup() throws IOException {
        assumeThatNoWindowsOS();
        assumeHadoopSupportsIbmPlatform();

        initialize(1, null);

        File directory = Files.createTempDirectory("sql-test-hdfs").toFile().getAbsoluteFile();
        directory.deleteOnExit();

        Configuration configuration = new Configuration();
        testRoot = new Path("hdfs://local" + directory.toURI().getPath());
        fileSystem = FileSystem.newInstance(testRoot.toUri(), configuration);
    }

    @Test
    public void testReadHadoop() throws IOException {
        store("/csv/file.csv", "id,name\n1,Alice\n2,Bob");

        String name = randomName();
        instance().getSql().execute(
                "CREATE MAPPING " + name + " (id INT, name VARCHAR) " +
                "TYPE File " +
                "OPTIONS (" +
                "  'format' = 'csv'," +
                "  'path' = '" + new Path(testRoot, "csv") + "'" +
                ");");

        for (int i = 0; i < 2; i++) {
            assertRowsAnyOrder("SELECT * FROM " + name, asList(
                    new Row(1, "Alice"),
                    new Row(2, "Bob")));
        }
    }

    @AfterClass
    public static void cleanup() throws IOException {
        if (fileSystem != null) {
            fileSystem.delete(testRoot, true);
            fileSystem.close();
        }
    }

    private static void store(String path, String content) throws IOException {
        Path target = resolve(path);
        fileSystem.mkdirs(target.getParent());
        try (FSDataOutputStream output = fileSystem.create(target)) {
            output.writeBytes(content);
        }
    }

    private static Path resolve(String path) {
        return new Path(testRoot, path.startsWith("/") ? path.substring(1) : path);
    }
}

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

import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class SecureFileAccessTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void readsLegitimateRegularFile() throws Exception {
        Path file = folder.newFile("config.yaml").toPath();
        Files.writeString(file, "value", UTF_8);

        assertThat(SecureFileAccess.readString(file.toString(), "configuration file")).isEqualTo("value");
    }

    @Test
    public void rejectsTraversalEvenWhenItResolvesInsideExistingDirectory() throws Exception {
        Path root = folder.newFolder("root").toPath();
        Path file = Files.writeString(root.resolve("config.yaml"), "value", UTF_8);
        String traversingPath = root.resolve("child").resolve("..").resolve(file.getFileName()).toString();

        assertThatThrownBy(() -> SecureFileAccess.readString(traversingPath, "configuration file"))
                .isInstanceOf(IOException.class);
    }

    @Test
    public void resolvesLegitimateChildBelowTrustedDirectory() throws Exception {
        Path root = folder.newFolder("libraries").toPath();
        Path file = Files.writeString(root.resolve("processor.jar"), "content", UTF_8);

        assertThat(SecureFileAccess.requireChildRegularFile(root, "processor.jar", "processor library"))
                .isEqualTo(file.toRealPath());
    }

    @Test
    public void rejectsChildEscapeAndAbsoluteChild() throws Exception {
        Path root = folder.newFolder("libraries").toPath();
        Path outside = folder.newFile("outside.jar").toPath();

        assertThatThrownBy(() -> SecureFileAccess.requireChildRegularFile(
                root, "../outside.jar", "processor library"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> SecureFileAccess.requireChildRegularFile(
                root, outside.toString(), "processor library"))
                .isInstanceOf(IOException.class);
    }

    @Test
    public void writesLegitimateFileAndRejectsUnsafeName() throws Exception {
        Path target = folder.getRoot().toPath().resolve("tracking.json");

        assertThat(SecureFileAccess.writeString(target, "{}", "tracking file"))
                .isEqualTo(target.toAbsolutePath());
        assertThat(Files.readString(target, UTF_8)).isEqualTo("{}");
        assertThatThrownBy(() -> SecureFileAccess.requireSafeFileName("../ports.txt", "port file"))
                .isInstanceOf(IOException.class);
    }

    @Test
    public void createsTemporaryFileWithSafePrefix() throws Exception {
        Path root = folder.newFolder("uploads").toPath();

        Path created = SecureFileAccess.createTempFile(root.toString(), "client-job", ".jar", "job upload");

        assertThat(created.getParent()).isEqualTo(root.toRealPath());
        assertThatThrownBy(() -> SecureFileAccess.createTempFile(
                root.toString(), "../job", ".jar", "job upload"))
                .isInstanceOf(IOException.class);
    }
}

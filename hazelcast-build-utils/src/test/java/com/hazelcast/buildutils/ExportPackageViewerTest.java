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

package com.hazelcast.buildutils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExportPackageViewerTest {

    @Test
    public void readsValidatedRegularFile() throws Exception {
        Path source = Files.createTempFile("export-packages", ".txt");
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            Files.writeString(source, "example.z;uses:=one,example.a", StandardCharsets.UTF_8);
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            ExportPackageViewer.main(new String[]{source.toString()});

            assertThat(output.toString(StandardCharsets.UTF_8))
                    .containsSubsequence("example.a", "example.z");
        } finally {
            System.setOut(originalOut);
            Files.deleteIfExists(source);
        }
    }

    @Test
    public void rejectsTraversalBeforeFilesystemAccess() {
        assertThatThrownBy(() -> ExportPackageViewer.main(new String[]{"../outside.txt"}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unsafe export package source");
    }
}

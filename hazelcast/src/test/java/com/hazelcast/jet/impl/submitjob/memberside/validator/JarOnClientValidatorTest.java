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

package com.hazelcast.jet.impl.submitjob.memberside.validator;

import com.hazelcast.jet.JetException;
import org.junit.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JarOnClientValidatorTest {

    @Test
    public void testValidateTempDirectoryPath() {
        assertThatThrownBy(() -> JarOnClientValidator.validateUploadDirectoryPath("foo"))
                .isInstanceOf(JetException.class)
                .hasMessageContaining("upload directory path is invalid");
    }

    @Test
    public void acceptsExistingUploadDirectory() throws Exception {
        JarOnClientValidator.validateUploadDirectoryPath(Files.createTempDirectory("job-upload").toString());
    }

    @Test
    public void rejectsTraversalAndUnsafeUploadFileName() {
        assertThatThrownBy(() -> JarOnClientValidator.validateUploadDirectoryPath("target/../target"))
                .isInstanceOf(JetException.class);
        assertThatThrownBy(() -> JarOnClientValidator.validateFileName("../job"))
                .isInstanceOf(JetException.class);
        assertThatThrownBy(() -> JarOnClientValidator.validateFileName("ab"))
                .isInstanceOf(JetException.class);
    }

    @Test
    public void acceptsSafeUploadFileName() {
        JarOnClientValidator.validateFileName("job-client");
    }

    @Test
    public void testValidateJobParameters() {
        assertThatThrownBy(() -> JarOnClientValidator.validateJobParameters(null))
                .isInstanceOf(JetException.class)
                .hasMessageContaining("jobParameters can not be null");
    }
}

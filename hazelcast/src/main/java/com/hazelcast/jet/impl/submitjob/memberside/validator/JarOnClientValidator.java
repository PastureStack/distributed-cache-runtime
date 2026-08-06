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
import com.hazelcast.jet.impl.submitjob.memberside.JobMetaDataParameterObject;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.hazelcast.internal.util.SecureFileAccess.requireExistingDirectory;
import static com.hazelcast.internal.util.SecureFileAccess.requireSafeFileName;

/**
 * Java client has validation but non-java clients may not perform validation.
 * This class performs the validation on the member side
 */
public final class JarOnClientValidator {

    private JarOnClientValidator() {
    }

    public static void validate(JobMetaDataParameterObject parameterObject) {
        validateUploadDirectoryPath(parameterObject.getUploadDirectoryPath());
        validateFileName(parameterObject.getFileName());
        validateJobParameters(parameterObject.getJobParameters());
    }

    static void validateUploadDirectoryPath(String  uploadDirectoryPath) {
        if (uploadDirectoryPath != null) {
            try {
                requireExistingDirectory(uploadDirectoryPath, "job upload directory");
            } catch (IOException e) {
                throw new JetException("The upload directory path is invalid", e);
            }
        }
    }

    static void validateFileName(String fileName) {
        try {
            String safeName = requireSafeFileName(fileName, "job upload file name");
            if (safeName.length() < 3) {
                throw new JetException("The upload file name must contain at least three characters");
            }
        } catch (JetException e) {
            throw e;
        } catch (IOException e) {
            throw new JetException("The upload file name is invalid", e);
        }
    }

    static void validateJobParameters(List<String> jobParameters) {
        // Check that parameter is not null
        if (Objects.isNull(jobParameters)) {
            throw new JetException("jobParameters can not be null");
        }
    }
}

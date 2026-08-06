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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Central filesystem boundary for paths supplied through configuration or
 * administrative input.
 */
public final class SecureFileAccess {

    private static final LinkOption NO_FOLLOW_LINKS = LinkOption.NOFOLLOW_LINKS;

    private SecureFileAccess() {
    }

    /**
     * Opens an existing, readable regular file without following symbolic links.
     */
    public static InputStream newInputStream(String configuredPath, String description) throws IOException {
        Path path = requireExistingRegularFile(configuredPath, description);
        return Files.newInputStream(path, NO_FOLLOW_LINKS);
    }

    /**
     * Reads an existing, readable regular file without following symbolic links.
     */
    public static String readString(String configuredPath, String description) throws IOException {
        Path path = requireExistingRegularFile(configuredPath, description);
        return Files.readString(path);
    }

    /**
     * Resolves an existing, readable regular file to an unambiguous physical path.
     */
    public static Path requireExistingRegularFile(String configuredPath, String description) throws IOException {
        Path normalized = normalizedPath(configuredPath, description);
        Path real = normalized.toRealPath();
        if (!normalized.equals(real)
                || !Files.isRegularFile(real, NO_FOLLOW_LINKS)
                || !Files.isReadable(real)) {
            throw invalidPath(description);
        }
        return real;
    }

    /**
     * Resolves an existing directory to an unambiguous physical path.
     */
    public static Path requireExistingDirectory(String configuredPath, String description) throws IOException {
        return requireExistingDirectory(normalizedPath(configuredPath, description), description);
    }

    /**
     * Creates a directory, while rejecting traversal and symbolic-link aliases.
     */
    public static Path prepareDirectory(String configuredPath, String description) throws IOException {
        Path normalized = normalizedPath(configuredPath, description);
        Path existingAncestor = normalized;
        while (existingAncestor != null && !Files.exists(existingAncestor, NO_FOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw invalidPath(description);
        }

        Path realAncestor = existingAncestor.toRealPath();
        if (!existingAncestor.equals(realAncestor)) {
            throw invalidPath(description);
        }

        Files.createDirectories(normalized);
        return requireExistingDirectory(normalized, description);
    }

    /**
     * Resolves a client-provided file below a trusted directory.
     */
    public static Path requireChildRegularFile(Path trustedDirectory, String childPath, String description)
            throws IOException {
        Path root = requireExistingDirectory(trustedDirectory.toString(), description + " directory");
        requireUnambiguousText(childPath, description);

        Path relative;
        try {
            relative = Path.of(childPath);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid " + description, e);
        }
        if (relative.isAbsolute()) {
            throw invalidPath(description);
        }

        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) {
            throw invalidPath(description);
        }
        Path real = candidate.toRealPath();
        if (!candidate.equals(real)
                || !real.startsWith(root)
                || !Files.isRegularFile(real, NO_FOLLOW_LINKS)
                || !Files.isReadable(real)) {
            throw invalidPath(description);
        }
        return real;
    }

    /**
     * Writes a file only when its parent and existing target are physical,
     * non-symbolic paths.
     */
    public static Path writeString(Path configuredPath, String contents, String description) throws IOException {
        Path target = requireWritableFile(configuredPath.toString(), description);
        OpenOption[] options = {
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                NO_FOLLOW_LINKS
        };
        Files.writeString(target, contents, options);
        return target;
    }

    /**
     * Opens a validated output file without following symbolic links.
     */
    public static OutputStream newOutputStream(Path configuredPath, String description) throws IOException {
        Path target = requireWritableFile(configuredPath.toString(), description);
        return Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                NO_FOLLOW_LINKS);
    }

    /**
     * Opens a validated output file for append without following symbolic links.
     */
    public static OutputStream newAppendingOutputStream(Path configuredPath, String description) throws IOException {
        Path target = requireWritableFile(configuredPath.toString(), description);
        return Files.newOutputStream(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE,
                NO_FOLLOW_LINKS);
    }

    /**
     * Creates a temporary file in a validated directory with a safe file-name prefix.
     */
    public static Path createTempFile(String configuredDirectory, String prefix, String suffix,
                                      String description) throws IOException {
        Path directory = requireExistingDirectory(configuredDirectory, description + " directory");
        String safePrefix = requireSafeFileName(prefix, description + " prefix");
        if (safePrefix.length() < 3) {
            throw invalidPath(description + " prefix");
        }
        return Files.createTempFile(directory, safePrefix, suffix);
    }

    /**
     * Accepts a single file name and rejects separators or traversal sequences.
     */
    public static String requireSafeFileName(String configuredName, String description) throws IOException {
        requireUnambiguousText(configuredName, description);
        Path name;
        try {
            name = Path.of(configuredName);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid " + description, e);
        }
        Path fileName = name.getFileName();
        if (name.isAbsolute() || fileName == null || !fileName.toString().equals(configuredName)) {
            throw invalidPath(description);
        }
        return configuredName;
    }

    private static Path requireWritableFile(String configuredPath, String description) throws IOException {
        Path normalized = normalizedPath(configuredPath, description);
        Path parent = normalized.getParent();
        if (parent == null) {
            throw invalidPath(description);
        }
        Path realParent = requireExistingDirectory(parent, description + " parent directory");
        if (!parent.equals(realParent) || !Files.isWritable(realParent)) {
            throw invalidPath(description);
        }

        if (Files.exists(normalized, NO_FOLLOW_LINKS)) {
            Path real = normalized.toRealPath();
            if (!normalized.equals(real)
                    || !Files.isRegularFile(real, NO_FOLLOW_LINKS)
                    || !Files.isWritable(real)) {
                throw invalidPath(description);
            }
        }
        return normalized;
    }

    private static Path requireExistingDirectory(Path normalized, String description) throws IOException {
        Path real = normalized.toRealPath();
        if (!normalized.equals(real)
                || !Files.isDirectory(real, NO_FOLLOW_LINKS)
                || !Files.isReadable(real)) {
            throw invalidPath(description);
        }
        return real;
    }

    private static Path normalizedPath(String configuredPath, String description) throws IOException {
        requireUnambiguousText(configuredPath, description);
        try {
            Path absolute = Path.of(configuredPath).toAbsolutePath();
            Path normalized = absolute.normalize();
            if (!absolute.equals(normalized)) {
                throw invalidPath(description);
            }
            return normalized;
        } catch (InvalidPathException e) {
            throw new IOException("Invalid " + description, e);
        }
    }

    private static void requireUnambiguousText(String configuredPath, String description) throws IOException {
        if (configuredPath == null
                || configuredPath.isBlank()
                || !configuredPath.equals(configuredPath.trim())
                || configuredPath.indexOf('\0') >= 0
                || configuredPath.contains("..")) {
            throw invalidPath(description);
        }
    }

    private static IOException invalidPath(String description) {
        return new IOException("Invalid or unsafe " + description);
    }
}

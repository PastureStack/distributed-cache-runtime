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

package com.hazelcast.internal.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.UrlXmlConfig;
import com.hazelcast.internal.tpcengine.util.OS;

import java.io.Closeable;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.hazelcast.internal.util.EmptyStatement.ignore;
import static com.hazelcast.internal.util.SecureFileAccess.requireExistingRegularFile;

/**
 * Provides loading service for a configuration.
 */
public final class ConfigLoader {

    public static final String REMOTE_CONFIG_ORIGINS_PROPERTY = "hazelcast.config.remote.origins";
    static final long MAX_CONFIG_BYTES = 16L * 1024 * 1024;

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_SCHEME = "file";
    private static final String JAR_SCHEME = "jar";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MAX_TCP_PORT = 65_535;
    private static final int REMOTE_TIMEOUT_MILLIS = 5_000;

    private ConfigLoader() {
    }

    public static Config load(final String path) throws IOException {
        final URL url = locateConfig(path);
        if (url == null) {
            return null;
        }
        return new UrlXmlConfig(url);
    }

    public static URL locateConfig(final String path) {
        if (path.isEmpty()) {
            return null;
        }
        URL url = asFile(path);
        if (url == null) {
            url = asURL(path);
        }
        if (url == null) {
            url = asResource(path);
        }
        if (url == null) {
            String extractedPath = extractPathOrNull(path);
            if (extractedPath == null) {
                return null;
            }
            url = asResource(extractedPath);
        }
        return url;
    }

    /**
     * Opens a declarative configuration resource after applying the configuration-source policy.
     * Local files and entries in local JAR files are accepted. HTTP(S) is denied by default and is
     * enabled only for exact origins listed in {@value #REMOTE_CONFIG_ORIGINS_PROPERTY}. Remote
     * redirects are never followed and every stream is limited to {@value #MAX_CONFIG_BYTES} bytes.
     *
     * @param url configuration resource URL
     * @return a bounded stream owned by the caller
     * @throws IOException if the resource is untrusted, unavailable, or too large
     */
    public static InputStream openConfig(URL url) throws IOException {
        if (url == null) {
            throw new IOException("Configuration URL must not be null");
        }

        URI uri = toUri(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IOException("Configuration URL has no scheme");
        }

        switch (scheme.toLowerCase(Locale.ROOT)) {
            case FILE_SCHEME:
                return openLocalFile(uri);
            case JAR_SCHEME:
                return openLocalJar(url);
            case HTTP_SCHEME:
            case HTTPS_SCHEME:
                return openRemoteConfig(uri);
            default:
                throw new IOException("Unsupported configuration URL scheme: " + scheme);
        }
    }

    private static URI toUri(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid configuration URL", e);
        }
    }

    private static InputStream openLocalFile(URI uri) throws IOException {
        requireLocalFileUri(uri);
        Path path = localPath(uri, "Invalid local configuration path");
        path = requireExistingRegularFile(path.toString(), "local configuration file");
        if (Files.size(path) > MAX_CONFIG_BYTES) {
            throw new IOException("Configuration resource exceeds " + MAX_CONFIG_BYTES + " bytes");
        }
        return new LimitedInputStream(Files.newInputStream(path), null);
    }

    private static InputStream openLocalJar(URL url) throws IOException {
        String external = url.toExternalForm();
        int separator = external.indexOf("!/");
        if (!external.startsWith("jar:") || separator < 0) {
            throw new IOException("Invalid JAR configuration URL");
        }

        URI archiveUri;
        try {
            archiveUri = URI.create(external.substring("jar:".length(), separator));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid JAR configuration URL", e);
        }
        requireLocalFileUri(archiveUri);

        String encodedEntryName = external.substring(separator + 2);
        String entryName;
        try {
            entryName = URLDecoder.decode(encodedEntryName.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid JAR configuration entry", e);
        }
        requireSafeJarEntry(entryName);

        Path archivePath = requireExistingRegularFile(
                localPath(archiveUri, "Invalid local JAR configuration path").toString(),
                "local JAR configuration file");
        JarFile jarFile = new JarFile(archivePath.toFile());
        try {
            JarEntry entry = jarFile.getJarEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Configuration entry not found in local JAR: " + entryName);
            }
            if (entry.getSize() > MAX_CONFIG_BYTES) {
                throw new IOException("Configuration resource exceeds " + MAX_CONFIG_BYTES + " bytes");
            }
            return new LimitedInputStream(jarFile.getInputStream(entry), jarFile);
        } catch (Exception e) {
            jarFile.close();
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Could not open local JAR configuration", e);
        }
    }

    private static Path localPath(URI uri, String errorMessage) throws IOException {
        try {
            return Path.of(uri);
        } catch (IllegalArgumentException e) {
            throw new IOException(errorMessage, e);
        }
    }

    private static void requireLocalFileUri(URI uri) throws IOException {
        if (!FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Only local file-backed configuration resources are allowed");
        }
        String authority = uri.getRawAuthority();
        if (authority != null && !authority.isEmpty()) {
            throw new IOException("Remote file authorities are not allowed for configuration resources");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IOException("Local configuration URLs must not contain a query or fragment");
        }
    }

    private static void requireSafeJarEntry(String entryName) throws IOException {
        if (entryName.isEmpty() || entryName.startsWith("/") || entryName.contains("\\")) {
            throw new IOException("Invalid JAR configuration entry");
        }
        for (String segment : entryName.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Invalid JAR configuration entry");
            }
        }
    }

    private static InputStream openRemoteConfig(URI uri) throws IOException {
        URI requestUri = requireAllowedRemoteOrigin(uri);

        HttpURLConnection connection = (HttpURLConnection) requestUri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(REMOTE_TIMEOUT_MILLIS);
        connection.setReadTimeout(REMOTE_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/xml, application/yaml, text/yaml, text/plain");

        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Remote configuration returned HTTP status " + status);
            }
            if (connection.getContentLengthLong() > MAX_CONFIG_BYTES) {
                throw new IOException("Configuration resource exceeds " + MAX_CONFIG_BYTES + " bytes");
            }
            return new LimitedInputStream(connection.getInputStream(), connection::disconnect);
        } catch (IOException e) {
            connection.disconnect();
            throw e;
        }
    }

    private static URI requireAllowedRemoteOrigin(URI uri) throws IOException {
        URI requestUri = normalizedRemoteUri(uri, false);
        for (URI origin : configuredRemoteOrigins()) {
            if (origin.getHost().equals(requestUri.getHost())
                    && origin.getScheme().equals(requestUri.getScheme())
                    && origin.getPort() == requestUri.getPort()) {
                return requestUri;
            }
        }
        throw new IOException("Remote configuration origin is not allowed: " + remoteOrigin(requestUri));
    }

    private static Set<URI> configuredRemoteOrigins() throws IOException {
        String configured = System.getProperty(REMOTE_CONFIG_ORIGINS_PROPERTY);
        Set<URI> origins = new HashSet<>();
        if (configured == null || configured.isBlank()) {
            return origins;
        }
        for (String value : configured.split(",", -1)) {
            if (value.isBlank() || !value.equals(value.trim())) {
                throw new IOException("Invalid origin in " + REMOTE_CONFIG_ORIGINS_PROPERTY);
            }
            URI origin;
            try {
                origin = URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid origin in " + REMOTE_CONFIG_ORIGINS_PROPERTY, e);
            }
            origins.add(normalizedRemoteUri(origin, true));
        }
        return origins;
    }

    private static URI normalizedRemoteUri(URI uri, boolean originOnly) throws IOException {
        String normalizedScheme = normalizedRemoteScheme(uri);
        requireRemoteAuthority(uri);
        requireNormalizedRemotePath(uri);
        if (originOnly) {
            requireOriginOnly(uri);
        }

        int port = uri.getPort();
        if (port == -1) {
            port = HTTP_SCHEME.equals(normalizedScheme) ? HTTP_PORT : HTTPS_PORT;
        }
        if (port < 1 || port > MAX_TCP_PORT) {
            throw new IOException("Invalid remote configuration port");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        try {
            return new URI(normalizedScheme, null, host, port,
                    originOnly ? null : uri.getPath(),
                    originOnly ? null : uri.getQuery(), null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid remote configuration URL", e);
        }
    }

    private static String remoteOrigin(URI uri) {
        String host = uri.getHost();
        if (host.indexOf(':') >= 0) {
            host = '[' + host + ']';
        }
        return uri.getScheme() + "://" + host + ':' + uri.getPort();
    }

    private static String normalizedRemoteScheme(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IOException("Remote configuration URLs must use HTTP or HTTPS");
        }
        String normalized = scheme.toLowerCase(Locale.ROOT);
        if (!HTTP_SCHEME.equals(normalized) && !HTTPS_SCHEME.equals(normalized)) {
            throw new IOException("Remote configuration URLs must use HTTP or HTTPS");
        }
        return normalized;
    }

    private static void requireRemoteAuthority(URI uri) throws IOException {
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IOException("Invalid remote configuration URL");
        }
    }

    private static void requireNormalizedRemotePath(URI uri) throws IOException {
        if (!uri.equals(uri.normalize())) {
            throw new IOException("Remote configuration URL contains non-normalized path segments");
        }
    }

    private static void requireOriginOnly(URI uri) throws IOException {
        String path = uri.getRawPath();
        boolean hasNonRootPath = path != null && !path.isEmpty() && !"/".equals(path);
        if (hasNonRootPath || uri.getRawQuery() != null) {
            throw new IOException("Remote configuration allowlist entries must contain only an origin");
        }
    }

    private static String extractPathOrNull(String path) {
        if (path.startsWith(CLASSPATH_PREFIX)) {
            return path.substring(CLASSPATH_PREFIX.length());
        }
        return null;
    }

    private static URL asFile(final String path) {
        File file = new File(path);
        if (file.exists()) {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException ignored) {
                ignore(ignored);
            }
        }
        return null;
    }

    private static URL asURL(final String path) {
        try {
            return URI.create(OS.ensureUnixSeparators(path)).toURL();
        } catch (IllegalArgumentException | MalformedURLException ignored) {
            ignore(ignored);
        }
        return null;
    }

    private static URL asResource(final String path) {
        URL url = null;
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            url = contextClassLoader.getResource(path);
        }
        if (url == null) {
            url = ConfigLoader.class.getClassLoader().getResource(path);
        }
        if (url == null) {
            url = ClassLoader.getSystemClassLoader().getResource(path);
        }
        return url;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final Closeable owner;
        private long count;

        private LimitedInputStream(InputStream inputStream, Closeable owner) {
            super(inputStream);
            this.owner = owner;
        }

        @Override
        public int read() throws IOException {
            if (count == MAX_CONFIG_BYTES) {
                int next = super.read();
                if (next == -1) {
                    return -1;
                }
                throw tooLarge();
            }
            int value = super.read();
            if (value != -1) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (count == MAX_CONFIG_BYTES) {
                return read() == -1 ? -1 : 1;
            }
            int boundedLength = (int) Math.min(length, MAX_CONFIG_BYTES - count);
            int read = super.read(bytes, offset, boundedLength);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        private IOException tooLarge() {
            try {
                close();
            } catch (IOException ignored) {
                ignore(ignored);
            }
            return new IOException("Configuration resource exceeds " + MAX_CONFIG_BYTES + " bytes");
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (owner != null) {
                    owner.close();
                }
            }
        }
    }
}

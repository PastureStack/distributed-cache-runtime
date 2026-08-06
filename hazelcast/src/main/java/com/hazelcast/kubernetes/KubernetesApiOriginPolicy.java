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

package com.hazelcast.kubernetes;

import com.hazelcast.config.InvalidConfigurationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Restricts Kubernetes API traffic to the in-cluster service or an explicitly trusted origin.
 */
final class KubernetesApiOriginPolicy {

    static final String ALLOWED_ORIGINS_PROPERTY = "hazelcast.kubernetes.allowed-api-origins";

    private static final String DEFAULT_API_ORIGIN = "https://kubernetes.default.svc";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int MAX_TCP_PORT = 65_535;

    private KubernetesApiOriginPolicy() {
    }

    static String requireAllowed(String configuredMaster) {
        URI candidate = canonicalOrigin(configuredMaster, "Kubernetes API origin");
        for (URI allowed : allowedOrigins()) {
            if (allowed.getHost().equals(candidate.getHost())
                    && allowed.getScheme().equals(candidate.getScheme())
                    && allowed.getPort() == candidate.getPort()) {
                return candidate.toString();
            }
        }
        throw new InvalidConfigurationException("Kubernetes API origin is not allowed: " + candidate
                + ". Add the exact origin to " + ALLOWED_ORIGINS_PROPERTY);
    }

    private static List<URI> allowedOrigins() {
        List<URI> origins = new ArrayList<>();
        origins.add(canonicalOrigin(DEFAULT_API_ORIGIN, "default Kubernetes API origin"));

        String configured = System.getProperty(ALLOWED_ORIGINS_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return origins;
        }
        for (String value : configured.split(",", -1)) {
            if (value.isBlank() || !value.equals(value.trim())) {
                throw invalidOrigin(ALLOWED_ORIGINS_PROPERTY, null);
            }
            origins.add(canonicalOrigin(value, ALLOWED_ORIGINS_PROPERTY));
        }
        return origins;
    }

    private static URI canonicalOrigin(String value, String source) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw invalidOrigin(source, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw invalidOrigin(source, null);
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw invalidOrigin(source, null);
        }
        requireAuthority(uri, source);
        requireOriginOnly(uri, source);

        int port = effectivePort(uri, scheme, source);

        try {
            return new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), port, null, null, null);
        } catch (URISyntaxException e) {
            throw invalidOrigin(source, e);
        }
    }

    private static void requireAuthority(URI uri, String source) {
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw invalidOrigin(source, null);
        }
    }

    private static void requireOriginOnly(URI uri, String source) {
        String path = uri.getRawPath();
        boolean hasNonRootPath = path != null && !path.isEmpty() && !"/".equals(path);
        if (uri.getRawQuery() != null || hasNonRootPath || !uri.equals(uri.normalize())) {
            throw invalidOrigin(source, null);
        }
    }

    private static int effectivePort(URI uri, String scheme, String source) {
        int port = uri.getPort();
        if (port == -1) {
            port = "http".equals(scheme) ? HTTP_PORT : HTTPS_PORT;
        }
        if (port < 1 || port > MAX_TCP_PORT) {
            throw invalidOrigin(source, null);
        }
        return port;
    }

    private static InvalidConfigurationException invalidOrigin(String source, Exception cause) {
        String message = "Invalid " + source + "; expected an HTTP(S) origin without credentials, path, query, or fragment";
        return cause == null
                ? new InvalidConfigurationException(message)
                : new InvalidConfigurationException(message, cause);
    }
}

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

package com.hazelcast.aws;

import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.spi.utils.RestClient;
import com.hazelcast.spi.exception.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.hazelcast.aws.AwsRequestUtils.createRestClient;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Responsible for connecting to AWS EC2 and ECS Metadata API.
 *
 * @see <a href="http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html">EC2 Instance Metatadata</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html">ECS Task IAM Role Metadata</a>
 * @see <a href="https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-metadata-endpoint.html">ECS Task Metadata</a>
 */
class AwsMetadataApi {
    private static final ILogger LOGGER = Logger.getLogger(AwsMetadataApi.class);
    private static final String EC2_METADATA_ENDPOINT = "http://169.254.169.254/latest/meta-data";
    private static final String ECS_METADATA_HOST = "169.254.170.2";
    private static final String ECS_METADATA_ORIGIN = "http://" + ECS_METADATA_HOST;
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final String EC2_METADATA_TOKEN_ENDPOINT = "http://169.254.169.254/latest/api/token";
    private static final Pattern ECS_TASK_METADATA_PATH = Pattern.compile(
            "/v[34]/[A-Za-z0-9._~-]+(?:/[A-Za-z0-9._~-]+)*");
    private static final Pattern ECS_CREDENTIALS_PATH = Pattern.compile(
            "/v2/credentials/[A-Za-z0-9._~-]+(?:/[A-Za-z0-9._~-]+)*");

    private static final String SECURITY_CREDENTIALS_URI = "/iam/security-credentials/";
    private static final long METADATA_TOKEN_TTL_SECONDS = 21600;

    private final String ec2MetadataEndpoint;
    private final String ec2MetadataTokenEndpoint;
    private final String ecsIamRoleEndpoint;
    private final String ecsTaskMetadataEndpoint;
    private final AwsConfig awsConfig;

    private String metadataToken;
    private Instant metadataExpiry;

    AwsMetadataApi(AwsConfig awsConfig) {
        this.ec2MetadataEndpoint = EC2_METADATA_ENDPOINT;
        this.ec2MetadataTokenEndpoint = EC2_METADATA_TOKEN_ENDPOINT;
        this.ecsIamRoleEndpoint = ecsIamRoleEndpoint(System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"));
        this.ecsTaskMetadataEndpoint = validateEcsTaskMetadataEndpoint(firstNonBlank(
                System.getenv("ECS_CONTAINER_METADATA_URI_V4"),
                System.getenv("ECS_CONTAINER_METADATA_URI")));
        this.awsConfig = awsConfig;
    }

    /**
     * For test purposes only.
     */
    AwsMetadataApi(String ec2MetadataEndpoint, String ecsIamRoleEndpoint, String ecsTaskMetadataEndpoint,
                   String ec2MetadataTokenEndpoint, AwsConfig awsConfig) {
        this.ec2MetadataEndpoint = ec2MetadataEndpoint;
        this.ec2MetadataTokenEndpoint = ec2MetadataTokenEndpoint;
        this.ecsIamRoleEndpoint = ecsIamRoleEndpoint;
        this.ecsTaskMetadataEndpoint = ecsTaskMetadataEndpoint;
        this.awsConfig = awsConfig;
    }

    String availabilityZoneEc2() {
        String uri = ec2MetadataEndpoint.concat("/placement/availability-zone/");
        return metadataClient(uri, awsConfig).get().getBody();
    }

    String availabilityZoneEcs() {
        return getTaskMetadata().get("AvailabilityZone").asString();
    }

    Optional<String> placementGroupEc2() {
        return getOptionalMetadata(ec2MetadataEndpoint.concat("/placement/group-name/"), "placement group");
    }

    Optional<String> placementPartitionNumberEc2() {
        return getOptionalMetadata(ec2MetadataEndpoint.concat("/placement/partition-number/"), "partition number");
    }

    String clusterEcs() {
        return getTaskMetadata().get("Cluster").asString();
    }

    /**
     * Resolves an optional metadata that exists for some instances only.
     * HTTP_OK and HTTP_NOT_FOUND responses are assumed valid. Any other
     * response code or an exception thrown during retries will yield
     * a warning log and an empty result will be returned.
     *
     * @param uri  Metadata URI
     * @param loggedName  Metadata name to be used when logging.
     * @return  The metadata if the endpoint exists, empty otherwise.
     */
    private Optional<String> getOptionalMetadata(String uri, String loggedName) {
        RestClient.Response response;
        try {
            response = metadataClient(uri, awsConfig)
                    .expectResponseCodes(HTTP_OK, HTTP_NOT_FOUND)
                    .get();
        } catch (Exception e) {
            // Failed to get a response with code OK or NOT_FOUND after retries
            LOGGER.warning(String.format("Could not resolve the %s metadata", loggedName));
            return Optional.empty();
        }
        int responseCode = response.getCode();
        if (responseCode == HTTP_OK) {
            return Optional.of(response.getBody());
        } else if (responseCode == HTTP_NOT_FOUND) {
            LOGGER.fine("No %s information is found.", loggedName);
            return Optional.empty();
        } else {
            throw new RuntimeException(String.format("Unexpected response code: %d", responseCode));
        }
    }

    private JsonObject getTaskMetadata() {
        if (ecsTaskMetadataEndpoint == null) {
            throw new IllegalStateException("The ECS task metadata endpoint is not available");
        }
        String uri = ecsTaskMetadataEndpoint.concat("/task");
        String response = createRestClient(uri, awsConfig).get().getBody();
        return Json.parse(response).asObject();
    }

    String defaultIamRoleEc2() {
        String uri = ec2MetadataEndpoint.concat(SECURITY_CREDENTIALS_URI);
        return metadataClient(uri, awsConfig).get().getBody();
    }

    AwsCredentials credentialsEc2(String iamRole) {
        String uri = ec2MetadataEndpoint.concat(SECURITY_CREDENTIALS_URI).concat(iamRole);
        String response = metadataClient(uri, awsConfig).get().getBody();
        return parseCredentials(response);
    }

    AwsCredentials credentialsEcs() {
        if (ecsIamRoleEndpoint == null) {
            throw new IllegalStateException("The ECS credentials endpoint is not available");
        }
        String response = createRestClient(ecsIamRoleEndpoint, awsConfig).get().getBody();
        return parseCredentials(response);
    }

    static String validateEcsTaskMetadataEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        if (!endpoint.equals(endpoint.trim())) {
            throw new IllegalArgumentException("ECS task metadata endpoint contains surrounding whitespace");
        }

        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ECS task metadata endpoint", e);
        }

        if (!isTrustedEcsMetadataOrigin(uri) || !isTrustedEcsMetadataPath(uri)) {
            throw new IllegalArgumentException("Untrusted ECS task metadata endpoint");
        }
        return uri.toASCIIString();
    }

    private static boolean isTrustedEcsMetadataOrigin(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (!ECS_METADATA_HOST.equals(uri.getHost())) {
            return false;
        }
        return uri.getPort() == -1 || uri.getPort() == HTTP_DEFAULT_PORT;
    }

    private static boolean isTrustedEcsMetadataPath(URI uri) {
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        String rawPath = uri.getRawPath();
        return rawPath != null
                && rawPath.equals(uri.normalize().getRawPath())
                && ECS_TASK_METADATA_PATH.matcher(rawPath).matches();
    }

    static String ecsIamRoleEndpoint(String relativeUri) {
        if (relativeUri == null || relativeUri.isBlank()) {
            return null;
        }
        if (!isTrustedEcsCredentialsPath(relativeUri)) {
            throw new IllegalArgumentException("Untrusted ECS credentials path");
        }
        return ECS_METADATA_ORIGIN + relativeUri;
    }

    private static boolean isTrustedEcsCredentialsPath(String relativeUri) {
        if (!relativeUri.equals(relativeUri.trim()) || !ECS_CREDENTIALS_PATH.matcher(relativeUri).matches()) {
            return false;
        }
        URI uri = URI.create(relativeUri);
        return uri.getRawAuthority() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && relativeUri.equals(uri.normalize().getRawPath());
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static AwsCredentials parseCredentials(String response) {
        JsonObject role = Json.parse(response).asObject();
        return AwsCredentials.builder()
            .setAccessKey(role.getString("AccessKeyId", null))
            .setSecretKey(role.getString("SecretAccessKey", null))
            .setToken(role.getString("Token", null))
            .build();
    }

    RestClient metadataClient(String url, AwsConfig awsConfig) {
        try {
            return createRestClient(url, awsConfig)
                .withHeader("X-aws-ec2-metadata-token", metadataToken());
        } catch (RestClientException ignored) {
            // rest client without token
            return createRestClient(url, awsConfig);
        }
    }

    String metadataToken() {
        if (!tokenValid()) {
            metadataToken = retrieveToken();
        }
        return metadataToken;
    }

    String retrieveToken() {
        String response = createRestClient(ec2MetadataTokenEndpoint, awsConfig)
            .withHeader("X-aws-ec2-metadata-token-ttl-seconds", Long.toString(METADATA_TOKEN_TTL_SECONDS))
            .put()
            .getBody();
        // we want to refresh token before it expires
        metadataExpiry = Instant.now().plusSeconds(METADATA_TOKEN_TTL_SECONDS / 2);
        return response;
    }

    boolean tokenValid() {
        return metadataExpiry != null && metadataExpiry.isAfter(Instant.now());
    }
}

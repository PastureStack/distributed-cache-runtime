# Security Maintenance

## Scope

The PastureStack maintenance delta intentionally preserves the Hazelcast 5.7.0 API while updating reviewed runtime and build-time dependencies with published security fixes:

The resulting reviewed Maven artifact version is `5.7.3-pasturestack.4`; it is a PastureStack maintenance release based on upstream `5.7.0`, not an upstream Hazelcast release. Hazelcast's cluster protocol parser accepts numeric product versions only, so generated runtime metadata deliberately reports `5.7.3`. The artifact version must be used by Maven coordinates, filenames, the artifact carrier, SBOM, and release evidence; the numeric runtime version must be used by cluster/member compatibility code.

| Component | Upstream 5.7.0 | PastureStack maintained version | Scope |
| --- | --- | --- | --- |
| Java release / cloud test VM | 17 / retired mixed-runtime fixtures | 25 LTS / Ubuntu 24.04 LTS | Compiler target, CI, and cloud integration runtime |
| Container build JDK | Unpinned release line | Temurin 25.0.4+7 on Ubuntu 24.04, digest pinned | Reproducible OCI build stage aligned with CI; build-only `unzip` preserves Maven ZIP checksum verification; a bounded 1.2 GiB Maven heap prevents javac OOM on the 2 GiB builder |
| Jackson 2 core and databind | 2.21.2 | 2.22.2 | Shaded runtime |
| Jackson 3 core, databind, and JR | 3.1.2 | 3.2.2 | Shaded runtime |
| Netty | 4.1.132.Final | 4.2.17.Final | Extension and Hadoop integration dependencies |
| Lettuce | 6.8.2.RELEASE | 7.7.0.RELEASE | Kafka Connect test dependency on the Netty 4.2 line |
| Apache Kafka client | 4.1.1 | 4.3.1 | Kafka connector runtime and compatibility tests |
| Confluent Platform test image | 8.1.1 | 8.3.1 | KRaft Kafka integration tests |
| JLine | 3.30.9 with Jansi | 4.3.1 with FFM | Command-line runtime |
| Protobuf Java / Python | 3.25.8 / 4.25.8 | 4.36.0 / 7.36.0 | Protobuf, gRPC, and Python extensions |
| Apache Avro | 1.11.5 | 1.12.2 | Serialization and connector runtime |
| Apache Parquet | 1.17.0 | 1.18.0 | Parquet connector runtime |
| Janino | 3.1.10 | 3.1.12 | SQL expression runtime |
| Checkstyle | 12.3.1 | 14.0.0 | Build-time source policy |
| gRPC Java / Python | 1.73.0 | 1.83.1 / 1.83.0 | gRPC and cloud extension dependencies |
| Aircompressor | 2.0.2 | 2.0.3 | Current Parquet 1.18 byte-array codec dependency; Aircompressor v3 uses a different artifact/package and MemorySegment API |
| Hazelcast test-job dependency | 5.0.5 | 5.7.3-pasturestack.4 | Command-line integration test fixtures |
| Maven Compiler Plugin | implicit | 3.15.0 | Reproducible test-fixture builds |
| Maven JAR Plugin | 3.1.2 | 3.5.1 | Reproducible test-fixture packaging |
| Maven Install Plugin | implicit | 3.1.4 | Latest stable Maven 3-compatible install lifecycle |
| Maven Dependency Plugin | 3.10.0 | 3.11.0 | Resolved runtime graph evidence |
| Python evidence verifier | runner default | 3.14.7 | Pinned CI-only evidence validation; not a runtime dependency |
| Kotlin Maven compiler strategy | daemon process | in-process | Bounded, single-JVM release builds |
| Apache MINA | 2.0.27 | 2.2.9 | ApacheDS test dependency |
| Apache Tomcat Embed Core | 11.0.20 | 11.0.25 | Test dependency |
| PostgreSQL JDBC | 42.7.11 | 42.7.13 | Database integration tests |
| Hibernate ORM | 5.6.15.Final | 7.4.5.Final | Distribution and Spring integration tests |
| LZ4 Java maintained fork | 1.10.1 | 1.11.2 | Elasticsearch extension runtime |
| Apache HTTP Core 5 | 5.3.5 / 5.3.4 | 5.4.3 | SQL, cloud, and Elasticsearch extension runtime |
| Apache HTTP Client 5 | 5.5 | 5.6.4 | SQL and extension runtime |
| Elasticsearch Java client | HLRC 7.17.29 | Java API Client 9.5.1 | Elasticsearch connector runtime |
| Apache Commons Configuration | 2.10.1 / 2.13.0 | 2.15.1 | SQL and extension runtime |
| OpenTelemetry API | 1.51.0 | 1.65.0 | Google Cloud Storage extension runtime |
| Log4j 2 | 2.25.4 | 2.26.1 | Runtime and test logging |
| Debezium | 1.9.8.Final | 3.6.1.Final | CDC runtime |
| Bouncy Castle | 1.82 | bcprov 1.85.2; bcpkix/bcutil 1.85 | Test and integration dependencies; each artifact is pinned to its latest Maven Central release |
| ZooKeeper | 3.8.4 | 3.9.5 | SQL integration tests |
| Square Wire runtime | 5.1.0 | 6.4.6 | Kafka/Schema Registry tests |
| Micrometer Core | 1.16.3 | 1.17.1 | Messaging integration tests |
| RabbitMQ Java client | 5.29.0 | 5.35.0 | Messaging integration tests |
| Plexus Utils | 3.6.0 | 4.1.0 | Maven build tooling |
| Logback | 1.3.15 | 1.6.3 | Test logging |
| Vert.x Core | 4.5.24 | 5.1.6 | Cloud integration tests |
| Apache Hadoop | 3.4.3 | 3.5.0 | Hadoop, Azure, and S3 connector runtime |
| Spring Boot | 4.1.0 | 4.1.1 | Runtime and integration tests |
| Spring Framework | 7.0.8 | 7.0.9 | Runtime and integration tests |
| Jetty test servers | Jetty 9.4.58 | removed | Schema Registry and Hadoop test infrastructure |

The Jackson updates address the High-severity findings reported against the original shaded runtime by the Trivy Java vulnerability database on 2026-07-22, including `GHSA-r7wm-3cxj-wff9`, `CVE-2026-54512`, and `CVE-2026-54513`. The 2026-08-07 full-source review also covers Netty, gRPC Java, Aircompressor, MINA, Tomcat, PostgreSQL JDBC, Hibernate ORM, LZ4 Java, and Spring advisories tracked by GitHub and the respective upstream security notices. The 2026-08-23 maintenance pass additionally updates the shared dependency-management boundary for Apache HTTP Core/Client, Commons Configuration, OpenTelemetry, Log4j, Bouncy Castle, ZooKeeper, Wire, Micrometer, RabbitMQ, Plexus Utils, Logback, and Vert.x. The obsolete Log4j 1 bridge was removed from the Elasticsearch 7 tests, the legacy MySQL connector coordinate was replaced with `com.mysql:mysql-connector-j`, and the unused Hadoop `jsonschema2pojo-core` transitive was excluded at its direct introducing dependency. The sole Commons Lang 2 test call was migrated to the already managed Commons Lang 3 API before excluding Commons Lang 2 from the same path. The final compatibility pass moves the product, fixtures, and CI target to Java 25 LTS, upgrades Netty and Lettuce together onto the 4.2-native line, replaces JLine's Jansi provider with the Java FFM provider, and migrates Protobuf serializers to the stable `Message` API so generated classes from both the previous V3 hierarchy and Protobuf Java 4 remain supported. Java gRPC remains at 1.83.1, while the Python runtime uses 1.83.0 because gRPC patch releases are language-specific and 1.83.1 is not published on PyPI. Parquet 1.18.0 still compiles against Aircompressor 2.0.3's byte-array API; Aircompressor v3 is not binary-compatible because it moved to a separate artifact, package, and MemorySegment API. The standalone command-line test fixtures now pin stable Maven plugin releases and use Java 25 so their build output matches the product runtime. Hibernate ORM 5.6 has no patched release and is end-of-life, so the distribution test was migrated to the current stable 7.4 line instead of suppressing the alert or deleting coverage. LZ4 Java 1.11.2 was accepted only after matching its Maven Central publication to the upstream security release, signed tag history, and verified commits. Development and test scope reduces production reachability, but it does not make vulnerable code safe to execute in CI or release builds.

Debezium was migrated from the end-of-life 1.9 line to 3.6.1.Final. The CDC implementation now uses the current schema-history SPI and configuration keys (`schema.history.internal`, `topic.prefix`, and the `*.include.list` / `*.exclude.list` family). PostgreSQL snapshot mode `NEVER` maps to the current `NO_DATA` mode, while custom snapshotters use the current Debezium snapshotter SPI and custom-name setting. This is a runtime migration, not an alert suppression.

The Elasticsearch connector was migrated from the end-of-life 7.17 High Level REST Client to the Elasticsearch Java API Client 9.5.1 and its REST 5 transport. Source, sink, bulk, scroll, slice, co-location, authentication, and request-option paths use the current API. The historical `hazelcast-jet-elasticsearch-7` artifactId remains only to preserve the published module coordinate; it no longer describes the client generation. The resolved connector graph contains no High Level REST Client, Elasticsearch server, Lucene, or legacy LZ4 artifact.

Jetty 9 was eliminated instead of forcing an unrelated Jetty 12 server migration into connectors or tests. Kafka and SQL Schema Registry tests now use Confluent's supported in-process `MockSchemaRegistry` client and retain schema-registration semantics without starting the Jetty-backed REST application. SQL Hadoop tests retain the external `hdfs://` connector path while Hadoop's ViewFS overload maps the isolated test root to a temporary local filesystem; this removes `MiniDFSCluster` and its Jetty server graph without replacing the Hadoop connector under test. Hadoop 3.5.0 also removes the `hadoop-azure -> jetty-util-ajax -> jetty-util` runtime path retained by Hadoop 3.4.3. The resolved Kafka, SQL, Hadoop distribution, Azure, and S3 connector graphs contain no `org.eclipse.jetty` artifact.

## Validation Boundaries

- Temurin 25 LTS compiles the 5,863-source core module. The focused SQL, Hadoop, Avro, Protobuf, gRPC, Kafka Connect, Python, and command-line compatibility checks pass. JLine 4 selects the FFM terminal provider under WSL. The Python runtime suite passes 6/6 with Protobuf 7.36.0 and gRPC Python 1.83.0.
- Kafka 4.3.1 passes real-container read/projection and write-to-topic checks against Confluent Platform 8.3.1. Spring Boot 4.1.1 with Spring Framework 7.0.9 passes all 15 module tests. The shaded tests JAR excludes dependency-owned `junit-platform.properties`, preventing a Netty test setting from silently forcing downstream suites into unsafe parallel execution.
- CycloneDX 1.6 aggregate SBOMs contain 354 runtime components / 355 dependency nodes and 659 all-scope components / 660 dependency nodes, no random serial number, and zero Jetty components. The release runtime SBOM independently contains 15 components / 16 dependency nodes and exactly matches its Maven runtime tree. Offline Trivy 0.74.0 scans recognize 355 and 660 aggregate Java packages respectively; the working tree, release JAR, release-runtime SBOM, aggregate SBOMs, and candidate OCI image all report zero Critical, High, Medium, or Low vulnerabilities against vulnerability DB `2026-08-24T00:58:29Z` and Java DB `2026-08-24T01:07:04Z`.
- The Elasticsearch non-Docker connector suite, serialization/wire tests, packaging, Checkstyle, Maven Enforcer, and dependency convergence pass. The real Elasticsearch 9.5.1 Testcontainers client test also passes against the migrated Java API Client.
- Debezium 3.6.1 integration tests pass against real MySQL and PostgreSQL containers. The MySQL source now assigns one bounded server ID per serialized source configuration, and both CDC modules use the Debezium-matched ANTLR 4.13.2 runtime.
- On Hadoop 3.5.0, the Hadoop core suite passes 57/57 tests and the SQL `hdfs://` suite passes all 14 currently discovered tests. The Kafka/Avro schema-evolution integration suite passes 13 discovered cases (one intentionally skipped) against Kafka while using the in-process Schema Registry mock. These executions verify that removing Jetty did not remove the connector behavior under test.
- The AWS, Azure, and GCP Terraform modules pass `fmt -check`, `init -backend=false`, and `validate` with Terraform 1.15.9. No cloud resources were provisioned.
- Local dependency, SBOM, JAR, and candidate-image evidence is current for this maintenance tree. GitHub alert closure and immutable release evidence still require the exact committed revision; stale remote alert counts do not describe the resolved local graph.

## Source Boundary Hardening

The maintained source closes the following boundaries identified by full-source CodeQL analysis:

- Namespace-aware DOM parsers now configure every external-entity and external-schema control directly and fail closed when the supported JDK provider cannot enforce one. The legacy compatibility property can no longer downgrade this document-builder boundary. Benign configuration XML remains accepted; a document containing a `DOCTYPE` and loopback external entity is rejected before any outbound connection.
- String-to-number query conversion now uses exact decimal range checks before returning `long`, `int`, `short`, or `byte` values. Out-of-range or precision-losing input remains a `Double`, matching the existing fallback contract instead of being silently saturated by a narrowing cast. Float conversion parses the target representation without narrowing a tainted `double`; finite exact values, infinity, and the existing `NaN` behavior are preserved. Serialization to a Java `short` retains the historical `DataOutput.writeShort(int)` low-order 16-bit contract through an explicit audited conversion.
- Configuration URLs are local-only by default. Local `file:` resources and local `jar:file:` entries are size-bounded and path-validated. Remote HTTP or HTTPS access requires an exact origin in the comma-separated `hazelcast.config.remote.origins` system property, rejects user information, fragments, non-normalized paths, and malformed origins, never follows redirects, uses finite connection and read timeouts, requires HTTP 200, and limits responses to 16 MiB.
- JDBC data connections accept only a constrained local H2 in-memory URL by default. Every other JDBC URL must exactly match a separately indexed `hazelcast.jdbc.allowed-url.<n>` system property. Both pooled and single-use paths apply the same policy, validate the complete allowlist before matching, reject malformed values, and reject alternate endpoint properties such as Hikari or nested data-source URL, host, server, port, database, driver, and data-source class overrides.
- Phone-home collection is disabled by default and cloud detection no longer probes HTTP metadata endpoints. Operators who explicitly enable it receive only environment-marker detection, eliminating implicit metadata-network requests.
- ECS credential retrieval accepts only the exact link-local origin `http://169.254.170.2`, validates both supported environment-variable forms, rejects user information, query strings, fragments, alternate ports, and non-normalized paths, and applies the same policy to relative credential paths before connecting.
- Multicast discovery accepts only a numeric IPv4 or IPv6 multicast literal, resolves it once, verifies that it is multicast, and passes the validated address to the sender. Hostnames, unicast, loopback, whitespace, and malformed inputs are rejected before discovery traffic is created.
- Filesystem paths originating in configuration, administrative commands, or upload metadata now pass through one physical-path boundary. Traversal segments, whitespace aliases, symbolic links, non-regular input files, unsafe child names, and unsafe output parents are rejected before any read, write, class loading, diagnostics, compatibility sampling, or job-upload operation. Legitimate existing files, directories, child JARs, and multipart uploads retain their existing behavior.
- JDBC connection lifecycle handling now uses a transparent proxy with an idempotent release callback instead of a handwritten forwarding class. This removes an unrelated query sink while retaining parameterized-query behavior, standard connection semantics, and exactly-once close handling.

The standalone release gates `scripts/check-xml-parser-hardening` and `scripts/check-numeric-conversion-hardening` compile the affected production classes and exercise legitimate and malicious cases without requiring a running cache service. The canonical focused Maven suite additionally covers configuration URL, filesystem paths, uploads, JDBC URL and lifecycle behavior, phone-home, ECS metadata, multicast, serialization, XML, regular-expression complexity, and numeric bounds. The isolated class-loader integration test connects to the exact member address and cluster name it creates instead of relying on environment-dependent discovery. Potentially long-running groups have independent hard timeouts, so one failed setup cannot conceal or block later suites. The GitHub release gate requires all 38 named suites to produce a Surefire report, requires at least the verified 385-test baseline, and rejects any failure, error, skipped test, missing suite, or reduced test set. Its CodeQL evidence parser resolves rule metadata from both SARIF driver and extension components, rejects unresolved metadata, and blocks every result with a security score of 7.0 or higher.

The reviewed core JAR build invokes Maven and the pinned JDK; it does not compile the repository's C source companions or package the optional Python extension. The gate records hashes for the tracked C headers, C sources, and prebuilt native resources, then requires the native bytes embedded in the JAR to match the tracked resources exactly. It also rejects Python extension resources in the core JAR. Python 3.14.7 is pinned only for evidence validation in CI. A C compiler therefore is not a hidden input to this artifact build, and the verifier Python is not a deployed runtime component.

All Spring Boot 3 / Spring Framework 6 compatibility profiles and the `hazelcast-spring-boot3` artifact have been removed. The maintained runtime is Spring Boot 4 / Spring Framework 7 only. XML schema names such as `hazelcast-spring-5.7.xsd` identify the Hazelcast schema version and are unrelated to a Spring Framework dependency line.

## Required Release Gates

A release is acceptable only when all of the following are true:

1. The upstream `v5.7.0` boundary recorded in [ORIGIN.md](ORIGIN.md) is an ancestor of the candidate, and every later commit remains on the linear PastureStack maintenance line.
2. Every reactor POM and command-line test fixture resolves the maintained artifact at 5.7.3-pasturestack.4, while generated cluster runtime metadata resolves to numeric version 5.7.3. Maven Wrapper 3.9.16 is downloaded only from Maven Central; its ZIP and tar.gz distributions are verified against separate committed SHA-256 values before execution. The source version gate requires Java 25, Ubuntu 24.04 LTS cloud test images, Checkstyle 14.0.0, Avro 1.12.2, Jackson 2.22.2 and 3.2.2, Janino 3.1.12, Parquet 1.18.0, JLine 4.3.1 with FFM, Protobuf Java 4.36.0 and Python 7.36.0, Netty 4.2.17.Final, Lettuce 7.7.0.RELEASE, Kafka 4.3.1, Confluent Platform 8.3.1, gRPC Java 1.83.1 and Python 1.83.0, Aircompressor 2.0.3, Hadoop 3.5.0, Maven Compiler Plugin 3.15.0, Maven JAR Plugin 3.5.1, Maven Install Plugin 3.1.4, Maven Dependency Plugin 3.11.0, an in-process Kotlin Maven compiler, MINA 2.2.9, Tomcat 11.0.25, PostgreSQL JDBC 42.7.13, Hibernate ORM 7.4.5.Final, LZ4 Java 1.11.2, Apache HTTP Core 5.4.3, Apache HTTP Client 5.6.4, Elasticsearch Java API Client 9.5.1, Commons Configuration 2.15.1, OpenTelemetry 1.65.0, Log4j 2.26.1, Debezium 3.6.1.Final, Bouncy Castle bcprov 1.85.2 with bcpkix/bcutil 1.85, ZooKeeper 3.9.5, Wire 6.4.6, Micrometer 1.17.1, RabbitMQ client 5.35.0, Plexus Utils 4.1.0, Logback 1.6.3, Vert.x 5.1.6, Spring Boot 4.1.1, and Spring Framework 7.0.9. It also verifies that Jansi, all Spring Boot 3 / Spring Framework 6 compatibility profiles and artifacts, legacy Debezium configuration keys, Elasticsearch HLRC, embedded Schema Registry server, Hadoop MiniDFSCluster, Jetty, and dependency-owned JUnit platform settings are absent from their governed outputs.
3. Maven's resolved dependency graph contains those reviewed versions, no older duplicate of the same artifacts, no Elasticsearch HLRC/server/Lucene legacy graph in the Elasticsearch connector, and no `org.eclipse.jetty` graph in the Kafka, SQL, Hadoop distribution, Azure, or S3 modules.
4. The produced file is `hazelcast-5.7.3-pasturestack.4.jar`; its embedded core Maven metadata reports 5.7.3-pasturestack.4, its generated cluster runtime metadata reports numeric version 5.7.3, and its embedded Jackson metadata reports Jackson 2.22.2 and Jackson 3.2.2. The full source commit and abbreviated revision embedded in `GeneratedBuildProperties` must match the OCI revision supplied to the build; unresolved placeholders are rejected during packaging.
5. Relevant core, Spring, database, and distribution integration tests pass on Java 25 LTS. The focused source-boundary suite must retain its expected suite and test counts so an accidentally undiscovered test cannot appear successful.
6. Both standalone source-boundary gates complete successfully on the release JDK.
7. `trivy fs --offline-scan --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings across the resolved source POMs.
8. `trivy rootfs --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings for the produced JAR.
9. Maven Help Plugin `3.5.2` generates the complete active-reactor effective POM, and the gate rejects any active direct dependency, managed dependency, build plugin, or managed plugin without a resolved version. CycloneDX Maven Plugin `2.9.3` then generates a CycloneDX `1.6` SBOM for the actual `hazelcast` runtime module from its resolved compile/runtime dependency graph. The gate compares every required SBOM coordinate with an independently generated Maven Dependency Plugin `3.11.0` runtime tree for the same module, independently validates the SBOM with CycloneDX CLI `0.33.1`, requires unique component identities and a populated dependency graph rooted at the reviewed runtime component, and submits that complete SBOM to Trivy for a separate zero-Critical/High dependency scan. All three Maven evidence-plugin JARs and POMs are fetched from Maven Central and checked against committed SHA-256 values before and after use. The source revision, every Maven input hash, the effective POM, the runtime tree, the CycloneDX SBOM, and their validation summaries are retained together. This intentionally excludes reactor test-support modules from the deployed SBOM and replaces filesystem-only JAR inventory, which cannot reconstruct dependencies relocated into a shaded artifact.
10. The JAR contains the upstream `LICENSE` and `NOTICE` material.
11. GitHub CodeQL reports zero current-source Critical and High findings for the release commit. Historical alerts against removed workflow snapshots are documented separately and do not replace this current-source gate.
12. The GHCR artifact package is public and can be fetched anonymously by manifest digest and layer digest.

The runtime image is an artifact carrier for deterministic downstream builds; it is not a standalone Hazelcast server image.

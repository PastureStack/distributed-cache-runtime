# Security Maintenance

## Scope

The PastureStack maintenance delta intentionally preserves the Hazelcast 5.7.0 API while updating reviewed runtime and build-time dependencies with published security fixes:

The resulting reviewed artifact version is `5.7.2`; it is a PastureStack maintenance release based on upstream `5.7.0`, not an upstream Hazelcast release. This version must be used consistently by the build output, artifact carrier, SBOM, and release evidence.

| Component | Upstream 5.7.0 | PastureStack maintained version | Scope |
| --- | --- | --- | --- |
| Jackson 2 core and databind | 2.21.2 | 2.21.5 LTS | Shaded runtime |
| Jackson 3 core, databind, and JR | 3.1.2 | 3.1.5 LTS | Shaded runtime |
| Netty | 4.1.132.Final | 4.1.137.Final | Extension and Hadoop integration dependencies |
| gRPC Java | 1.73.0 | 1.83.1 | gRPC and cloud extension dependencies |
| Aircompressor | 2.0.2 | 2.0.3 | Hadoop compression dependency |
| Hazelcast test-job dependency | 5.0.5 | 5.7.2 | Command-line integration test fixtures |
| Maven Compiler Plugin | implicit | 3.15.0 | Reproducible test-fixture builds |
| Maven JAR Plugin | 3.1.2 | 3.5.1 | Reproducible test-fixture packaging |
| Maven Install Plugin | implicit | 3.1.4 | Latest stable Maven 3-compatible install lifecycle |
| Kotlin Maven compiler strategy | daemon process | in-process | Bounded, single-JVM release builds |
| Apache MINA | 2.0.27 | 2.0.31 | ApacheDS test dependency |
| Apache Tomcat Embed Core | 11.0.20 | 11.0.24 | Test dependency |
| PostgreSQL JDBC | 42.7.11 | 42.7.13 | Database integration tests |
| Hibernate ORM | 5.6.15.Final | 7.4.5.Final | Distribution and Spring integration tests |
| LZ4 Java maintained fork | 1.10.1 | 1.11.2 | Elasticsearch extension runtime |
| Spring Boot 3 | 3.5.14 | 3.5.15 | Spring 6 integration tests |
| Spring Framework 6 | 6.2.18 | 6.2.19 | Spring 6 integration tests |
| Spring Boot 4 | 4.0.6 | 4.1.0 | Spring 7 integration tests |
| Spring Framework 7 | 7.0.7 | 7.0.8 | Spring 7 integration tests |

The Jackson updates address the High-severity findings reported against the original shaded runtime by the Trivy Java vulnerability database on 2026-07-22, including `GHSA-r7wm-3cxj-wff9`, `CVE-2026-54512`, and `CVE-2026-54513`. The 2026-08-07 full-source review also covers Netty, gRPC Java, Aircompressor, MINA, Tomcat, PostgreSQL JDBC, Hibernate ORM, LZ4 Java, and Spring advisories tracked by GitHub and the respective upstream security notices. Netty remains on its compatible 4.1 maintenance line rather than crossing to 4.2. Aircompressor 2.0.3 contains the upstream fix for `CVE-2025-67721`; the current 3.x line targets JDK 25 and cannot replace it in this release-17 project. The standalone command-line test fixtures now pin stable Maven plugin releases and use the intended compiler release setting so their build output is reproducible. Hibernate ORM 5.6 has no patched release and is end-of-life, so the distribution test was migrated to the current stable 7.4 line instead of suppressing the alert or deleting coverage. LZ4 Java 1.11.2 was accepted only after matching its Maven Central publication to the upstream security release, signed tag history, and verified commits. Development and test scope reduces production reachability, but it does not make vulnerable code safe to execute in CI or release builds.

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

## Required Release Gates

A release is acceptable only when all of the following are true:

1. The source boundary is the upstream `v5.7.0` commit recorded in [ORIGIN.md](ORIGIN.md), followed by exactly one PastureStack maintenance commit.
2. Every reactor POM and command-line test fixture resolves the maintained project at 5.7.2. Maven Wrapper 3.9.14 is downloaded only from Maven Central and is verified against the committed SHA-256 before execution. The source version gate reports Netty 4.1.137.Final, gRPC Java 1.83.1, Aircompressor 2.0.3, Maven Compiler Plugin 3.15.0, Maven JAR Plugin 3.5.1, Maven Install Plugin 3.1.4, an in-process Kotlin Maven compiler, MINA 2.0.31, Tomcat 11.0.24, PostgreSQL JDBC 42.7.13, Hibernate ORM 7.4.5.Final, LZ4 Java 1.11.2, Spring Boot 3.5.15 and 4.1.0, and Spring Framework 6.2.19 and 7.0.8.
3. Maven's resolved dependency graph contains those reviewed versions and no older duplicate of the same artifacts.
4. The produced file is `hazelcast-5.7.2.jar`; its embedded core Maven metadata reports 5.7.2, and its embedded Jackson metadata reports Jackson 2.21.5 and Jackson 3.1.5.
5. Relevant core, Spring, database, and distribution integration tests pass on a supported LTS JDK. The focused source-boundary suite must retain its expected suite and test counts so an accidentally undiscovered test cannot appear successful.
6. Both standalone source-boundary gates complete successfully on the release JDK.
7. `trivy fs --offline-scan --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings across the resolved source POMs.
8. `trivy rootfs --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings for the produced JAR.
9. CycloneDX Maven plugin `2.9.3` generates a CycloneDX `1.6` SBOM for the actual `hazelcast` runtime module from its resolved compile/runtime dependency graph. The gate compares every required SBOM coordinate with an independently generated Maven Dependency Plugin `3.10.0` runtime tree for the same module, independently validates the SBOM with CycloneDX CLI `0.32.0`, requires unique component identities and a populated dependency graph rooted at the reviewed runtime component, and submits that complete SBOM to Trivy for a separate zero-Critical/High dependency scan. This intentionally excludes reactor test-support modules and replaces filesystem-only JAR inventory, which cannot reconstruct dependencies relocated into a shaded artifact.
10. The JAR contains the upstream `LICENSE` and `NOTICE` material.
11. GitHub CodeQL reports zero current-source Critical and High findings for the release commit. Historical alerts against removed workflow snapshots are documented separately and do not replace this current-source gate.
12. The GHCR artifact package is public and can be fetched anonymously by manifest digest and layer digest.

The runtime image is an artifact carrier for deterministic downstream builds; it is not a standalone Hazelcast server image.

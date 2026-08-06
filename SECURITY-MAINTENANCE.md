# Security Maintenance

## Scope

The PastureStack maintenance delta intentionally preserves the Hazelcast 5.7.0 API while updating reviewed runtime and build-time dependencies with published security fixes:

The resulting reviewed artifact version is `5.7.0-pasturestack.2`; this version must be used consistently by the build output, artifact carrier, SBOM, and release evidence.

| Component | Upstream 5.7.0 | PastureStack maintained version | Scope |
| --- | --- | --- | --- |
| Jackson 2 core and databind | 2.21.2 | 2.21.5 LTS | Shaded runtime |
| Jackson 3 core, databind, and JR | 3.1.2 | 3.1.5 LTS | Shaded runtime |
| Netty | 4.1.132.Final | 4.1.137.Final | Extension and Hadoop integration dependencies |
| gRPC Java | 1.73.0 | 1.83.1 | gRPC and cloud extension dependencies |
| Aircompressor | 2.0.2 | 2.0.3 | Hadoop compression dependency |
| Hazelcast test-job dependency | 5.0.5 | 5.7.0 | Command-line integration test fixtures |
| Maven Compiler Plugin | implicit | 3.15.0 | Reproducible test-fixture builds |
| Maven JAR Plugin | 3.1.2 | 3.5.1 | Reproducible test-fixture packaging |
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

## Required Release Gates

A release is acceptable only when all of the following are true:

1. The source boundary is the upstream `v5.7.0` commit recorded in [ORIGIN.md](ORIGIN.md), followed by exactly one PastureStack maintenance commit.
2. The source version gate reports Netty 4.1.137.Final, gRPC Java 1.83.1, Aircompressor 2.0.3, test-job dependencies at 5.7.0, Maven Compiler Plugin 3.15.0, Maven JAR Plugin 3.5.1, MINA 2.0.31, Tomcat 11.0.24, PostgreSQL JDBC 42.7.13, Hibernate ORM 7.4.5.Final, LZ4 Java 1.11.2, Spring Boot 3.5.15 and 4.1.0, and Spring Framework 6.2.19 and 7.0.8.
3. Maven's resolved dependency graph contains those reviewed versions and no older duplicate of the same artifacts.
4. The embedded Maven metadata reports Jackson 2.21.5 and Jackson 3.1.5.
5. Relevant core, Spring, database, and distribution integration tests pass on a supported LTS JDK.
6. `trivy fs --offline-scan --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings across the resolved source POMs.
7. `trivy rootfs --scanners vuln --severity CRITICAL,HIGH` reports zero Critical and zero High findings for the produced JAR.
8. The JAR contains the upstream `LICENSE` and `NOTICE` material.
9. The GHCR artifact package is public and can be fetched anonymously by manifest digest and layer digest.

The runtime image is an artifact carrier for deterministic downstream builds; it is not a standalone Hazelcast server image.

# Distributed Cache Runtime 5.7.7

This release updates the relocated `org.json:json` dependency from
`20250107` to the official `20260719` release for
[CVE-2026-59171](https://github.com/stleary/JSON-java/security/advisories/GHSA-ghc9-756g-vrcv).
Dependency management applies the same version to tests, schema validation,
the resolved runtime SBOM and the shaded core JAR. The artifact version is
`5.7.7`; cluster protocol metadata remains `5.7.3`. Existing Jackson pins,
serialization APIs and security checks are retained.

Focused checks cover oversized integer, negative and decimal tokens through
JSONObject, JSONArray and XML, bounded exponent conversion, ordinary numeric
configuration, YAML conversion and member/client schema validation. The same
numeric checks also run against the relocated classes in the delivered JAR.
The build rejects missing, duplicate or old embedded org.json version metadata.
Publication passed the existing source, artifact, dependency and
CodeQL gates. Previous tags and artifacts remain immutable. This release
does not claim downstream Engine or Server deployment acceptance.

The current source scan also identified CVE-2026-102554 in managed Guava
33.7.1-jre. Its official 33.7.2-jre patch is applied to all inherited consumers,
including SQL/connectors and test dependencies. The core runtime's actual
resolved dependency tree and shaded artifact determine whether Guava is
packaged; a source floor is not reported as a core runtime vulnerability.
The existing SQL/parser and connector compile/test gates remain required.
Official advisory: https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94 .

Signed producer `9f364be88cf92b267158005fed1c424221058bf8` produced the
23,861,001-byte JAR, SHA-256
`a72ddfd7638527cbccd79d9d3e3611e2979cadd78e615381f230c07a2767b631`.
PR #23 merged the same tested tree. All 19 immutable public assets match the
original CI bytes, including an anonymous JAR download. No release-time rebuild
or repack occurred. CI passed 524 tests in 45 suites, zero failures/errors/skips,
and zero source/artifact/dependency findings or secrets. The 15-component,
16-node CycloneDX 1.6 graph exactly matches Maven's resolved runtime dependencies.
12,496 classes are byte-identical to 5.7.6; class changes are limited to 16
relocated org.json classes and generated build identity. The first failed
candidate's Guava scan remains preserved in Actions.

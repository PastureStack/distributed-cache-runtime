# Distributed Cache Runtime 5.7.7

This candidate updates the relocated `org.json:json` dependency from
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
Publication still requires the existing source, artifact, dependency and
CodeQL gates. Previous tags and artifacts remain immutable. This candidate
does not claim downstream Engine or Server deployment acceptance.

The current source scan also identified CVE-2026-102554 in managed Guava
33.7.1-jre. Its official 33.7.2-jre patch is applied to all inherited consumers,
including SQL/connectors and test dependencies. The core runtime's actual
resolved dependency tree and shaded artifact determine whether Guava is
packaged; a source floor is not reported as a core runtime vulnerability.
The existing SQL/parser and connector compile/test gates remain required.
Official advisory: https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94 .

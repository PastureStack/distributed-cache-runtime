# 5.7.5: Jackson security patch

Published GitHub release: [`v5.7.5`](https://github.com/PastureStack/distributed-cache-runtime/releases/tag/v5.7.5).
Signed tag source: `a9aea563870201462dc24f778a06279a08ed5841`.
`hazelcast-5.7.5.jar` is 23,852,246 bytes with SHA-256
`0f536a9c7bcd00f2369586fb6ca1606f7e45f3225e24795d10d38397051c8715`.
The actual published download and source-revision attachment match the
reviewed CI artifact. Public `v5.7.4` remains unchanged historical evidence.

- Update the actual parent properties and imported BOMs from Jackson 2.22.2 to
  2.22.3 and from Jackson 3.2.2 to 3.2.3. FasterXML lists fixes for
  CVE-2026-91776 (unbounded type-id cache) and CVE-2026-91777 (quadratic
  forward-reference resolution) in both
  [2.22.3](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.22.3) and
  [3.2.3](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.2.3).
- Require those exact versions in all four embedded Jackson core/databind
  Maven metadata entries. Update reactor coordinates, test-job fixtures,
  carrier paths, SBOM identity, and release gates to numeric artifact `5.7.5`.
- Preserve numeric Hazelcast cluster runtime `5.7.3`, business logic, and all
  other dependency pins from the candidate's `origin/main` base.

## Actual verification

[Security gate run `36815272664`](https://github.com/PastureStack/distributed-cache-runtime/actions/runs/36815272664)
passed 510 tests across all 40 named suites, with zero failures, errors or
skipped tests. The exact retained shaded JAR was published without a
release-time rebuild. Its four embedded Jackson core/databind metadata entries
report `2.22.3` / `3.2.3`; generated cluster runtime remains `5.7.3`, and its
full source commit and abbreviated revision match the signed release source.

The 38-project effective POM has no unresolved dependency/plugin versions.
The CycloneDX 1.6 runtime SBOM has 15 components / 16 dependency nodes and
matches all 15 Maven runtime coordinates. The original source, JAR and SBOM
Trivy 0.74.0 scans report zero Critical/High findings within their scanned
boundaries; the source secret scan reports zero findings.
[CodeQL run `36815270196`](https://github.com/PastureStack/distributed-cache-runtime/actions/runs/36815270196)
completed all four required analyses. Their exact PR-head SARIF results have
zero security-severity >=7 findings and zero unresolved result-rule metadata.

All 19 original CI files are attached byte-for-byte. To check the original
hash manifests, place the JAR under `dist/` and the evidence files under
`evidence/`; their recorded relative paths are intentionally unchanged.
The current downstream installer consumes the GitHub asset directly. No new
OCI/GHCR carrier is published, and no downstream Engine startup, Server image,
Passkey login or complete resource/role QA is inferred from these results.

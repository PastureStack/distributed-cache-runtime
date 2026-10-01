# 5.7.5 candidate: Jackson security patch

Status: source candidate only; not yet built, scanned, published, or validated
in a downstream runtime. Public `v5.7.4` remains unchanged historical evidence.

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

The existing release gate must still build the shaded JAR, run its focused
regression suites, verify resolved Maven/SBOM and packaged metadata, and scan
the actual artifact with current vulnerability data. Source-only checks do not
prove that CVEs are absent from a built artifact or any downstream Engine JAR.

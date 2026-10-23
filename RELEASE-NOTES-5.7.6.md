# Distributed Cache Runtime 5.7.6

This release rebuilds the maintained core JAR with the official Temurin
25.0.5+8 security release. Maven artifact coordinates and filenames move to
5.7.6; the cluster protocol and generated runtime version remain 5.7.3.
Managed application dependencies and public Java APIs are unchanged.

CI uses Python 3.14.9 with hash-locked pip 26.2.2 and certifi 2026.10.19.
Both the standalone and pip-bundled trust stores are checked. The optional
artifact-carrier build keeps its pinned Ubuntu 24.04 base, installs the
matching CA update, and verifies official JDK archives before replacing the
old build JDK. The final carrier contains the JAR and license files only.

Signed producer `bd071c12bc11808a5f91e8b5d0308decd79e5af9` produced the
23,852,245-byte JAR with SHA-256
`825b5fc9526a10549bcb580f2d6c03bf1f5667625661722c0406ea6f796e83f2`.
The anonymous release download matches those bytes and embeds the same source.
[CI](https://github.com/PastureStack/distributed-cache-runtime/actions/runs/39949973022)
passed 510 tests in 40 suites with zero failures, errors or skips, artifact
identity checks, and source/JAR/dependency scans with zero Critical/High findings.
The CycloneDX 1.6 SBOM's 15 components and 16 dependency nodes match Maven.
All required CodeQL checks passed before protected PR #18 merged the same tree.
These are the original CI artifacts; no release-time rebuild occurred.
No new OCI carrier, downstream deployment or full historical matrix is implied.
Previous release tags and assets remain immutable.

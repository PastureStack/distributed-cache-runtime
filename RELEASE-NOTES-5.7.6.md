# Distributed Cache Runtime 5.7.6 candidate

This candidate rebuilds the maintained core JAR with the official Temurin
25.0.5+8 security release. Maven artifact coordinates and filenames move to
5.7.6; the cluster protocol and generated runtime version remain 5.7.3.
Managed application dependencies and public Java APIs are unchanged.

CI uses Python 3.14.9 with hash-locked pip 26.2.2 and certifi 2026.10.19.
Both the standalone and pip-bundled trust stores are checked. The optional
artifact-carrier build keeps its pinned Ubuntu 24.04 base, installs the
matching CA update, and verifies official JDK archives before replacing the
old build JDK. The final carrier contains the JAR and license files only.

Publication requires the existing focused regression, artifact identity,
SBOM, and vulnerability gates. Candidate source is not proof of a published
or deployed artifact. Previous release tags and assets remain immutable.

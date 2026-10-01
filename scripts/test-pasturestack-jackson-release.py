#!/usr/bin/env python3
"""Offline regression fixtures for the Jackson pins and packaged metadata gate.

Exercises the build script's real gate commands, not a JAR build or a CVE scan.
"""

import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
EXPECTED = {
    "com.fasterxml.jackson.core/jackson-core": "2.22.3",
    "com.fasterxml.jackson.core/jackson-databind": "2.22.3",
    "tools.jackson.core/jackson-core": "3.2.3",
    "tools.jackson.core/jackson-databind": "3.2.3",
}
BUILD = (ROOT / "scripts/pasturestack-build-runtime").read_text(encoding="utf-8")


def gate_commands(marker):
    return "\n".join(line for line in BUILD.splitlines() if marker(line))


class JacksonReleaseGateTest(unittest.TestCase):
    def test_jackson_json_suites_are_selected_and_required_reports(self):
        workflow = (ROOT / ".github/workflows/security-release-gate.yml").read_text(encoding="utf-8")
        run = workflow.split("      - name: Run focused legitimate and malicious regression suite\n", 1)[1].split("\n      - name:", 1)[0]
        validator = workflow.split("      - name: Verify every required test suite was discovered\n", 1)[1].split("\n      - name:", 1)[0]
        selector = next(line.strip() for line in run.splitlines() if line.strip().startswith("core_tests='"))
        selected = selector.split("'", 2)[1].split(",")
        required = validator.split("core = '''", 1)[1].split("'''.split()", 1)[0].split()
        for suite in (
            "com.hazelcast.jet.impl.util.JsonUtilTest",
            "com.hazelcast.jet.json.impl.JsonUtilImplTest",
        ):
            self.assertEqual(selected.count(suite), 1)
            self.assertEqual(required.count(suite), 1)
        self.assertIn('all_tests="$core_tests,', run)
        self.assertIn('-Dtest="$all_tests"', run)
        self.assertIn("(pathlib.Path('hazelcast/target/surefire-reports'), name)", validator)
        self.assertIn("for name in core", validator)
        self.assertIn("if total < 385 or failures or errors or skipped:", validator)
        self.assertFalse(any("JsonMetadataCreationTest" in suite for suite in selected + required))

    def test_workflow_checks_exact_pr_head_and_retains_successful_artifact(self):
        workflow = (ROOT / ".github/workflows/security-release-gate.yml").read_text(encoding="utf-8")
        checkout = workflow.split("      - name: Check out candidate\n", 1)[1].split("\n      - name:", 1)[0]
        retention = workflow.split("      - name: Retain exact reviewed release artifact\n", 1)[1].split("\n      - name:", 1)[0]
        self.assertEqual(
            [line.strip() for line in checkout.splitlines() if line.strip().startswith("ref:")],
            ["ref: ${{ inputs.release_ref || github.event.pull_request.head.sha || github.sha }}"],
        )
        self.assertEqual(
            [line.strip() for line in retention.splitlines() if line.strip().startswith("if:")],
            ["if: success()"],
        )

    def run_gate(self, commands, folder, **variables):
        result = subprocess.run(
            ["bash", "-euo", "pipefail", "-c", commands],
            cwd=folder,
            env={**os.environ, **variables},
            capture_output=True,
            text=True,
            timeout=10,
        )
        return result.returncode

    def packaged_gate(self, versions):
        commands = gate_commands(
            lambda line: line.startswith("test ")
            and any("/" + coordinate + "/pom.properties" in line for coordinate in EXPECTED)
        )
        self.assertEqual(len(commands.splitlines()), len(EXPECTED))
        for coordinate, version in EXPECTED.items():
            self.assertIn('/' + coordinate + '/pom.properties")" = "' + version + '"', commands)
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name)
            for coordinate, version in versions.items():
                metadata = folder / "META-INF/maven" / coordinate / "pom.properties"
                metadata.parent.mkdir(parents=True, exist_ok=True)
                metadata.write_text("version=" + version + "\n", encoding="utf-8")
            return self.run_gate(commands, folder, metadata_dir=str(folder))

    def test_real_parent_boms_select_patched_lines(self):
        parent = ElementTree.parse(ROOT / "hazelcast-parent/pom.xml").getroot()
        properties = parent.find("m:properties", NS)
        self.assertEqual(properties.findtext("m:jackson2.version", namespaces=NS), "2.22.3")
        self.assertEqual(properties.findtext("m:jackson3.version", namespaces=NS), "3.2.3")
        boms = {
            entry.findtext("m:groupId", namespaces=NS): entry.findtext("m:version", namespaces=NS)
            for entry in parent.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
            if entry.findtext("m:artifactId", namespaces=NS) == "jackson-bom"
        }
        self.assertEqual(boms, {
            "com.fasterxml.jackson": "${jackson2.version}",
            "tools.jackson": "${jackson3.version}",
        })
        core = ElementTree.parse(ROOT / "hazelcast/pom.xml").getroot()
        jackson2 = [
            entry for entry in core.findall("m:dependencies/m:dependency", NS)
            if entry.findtext("m:groupId", namespaces=NS) == "com.fasterxml.jackson.core"
            and entry.findtext("m:artifactId", namespaces=NS) in {"jackson-core", "jackson-databind"}
        ]
        self.assertEqual(len(jackson2), 2)
        self.assertTrue(all(entry.findtext("m:version", namespaces=NS) == "${jackson2.version}"
                            for entry in jackson2))

    def test_artifact_coordinates_preserve_cluster_protocol(self):
        paths = set()
        remaining = [ROOT / "pom.xml"]
        while remaining:
            path = remaining.pop()
            if path in paths:
                continue
            paths.add(path)
            project = ElementTree.parse(path).getroot()
            remaining.extend(path.parent / module.text / "pom.xml"
                             for module in project.findall(".//m:modules/m:module", NS))
        paths.update((ROOT / "hazelcast/src/test/resources/com/hazelcast/client/console")
                     .glob("testjob-*/pom.xml"))
        self.assertTrue(paths)
        fixtures = 0
        for path in paths:
            project = ElementTree.parse(path).getroot()
            for parent in project.findall("m:parent", NS):
                if parent.findtext("m:groupId", namespaces=NS) == "com.hazelcast":
                    self.assertEqual(parent.findtext("m:version", namespaces=NS), "5.7.5", path)
            if project.findtext("m:groupId", namespaces=NS) == "com.hazelcast":
                version = project.findtext("m:version", namespaces=NS)
                if version is not None:
                    self.assertEqual(version, "5.7.5", path)
            if "/console/testjob-" in path.as_posix():
                fixtures += 1
                dependency = project.find("m:dependencies/m:dependency[m:groupId='com.hazelcast']", NS)
                self.assertIsNotNone(dependency, path)
                self.assertEqual(dependency.findtext("m:version", namespaces=NS), "5.7.5", path)
        self.assertEqual(fixtures, 3)
        parent = ElementTree.parse(ROOT / "hazelcast-parent/pom.xml").getroot()
        self.assertEqual(parent.findtext("m:properties/m:hazelcast.runtime.version", namespaces=NS), "5.7.3")

    def test_source_gate_rejects_each_unpatched_parent_property(self):
        commands = gate_commands(lambda line: line.startswith("grep -Fq ")
                                 and ("jackson2.version" in line or "jackson3.version" in line))
        self.assertEqual(len(commands.splitlines()), 2)
        for jackson2, jackson3, expected_status in (
            ("2.22.3", "3.2.3", 0), ("2.22.2", "3.2.3", 1), ("2.22.3", "3.2.2", 1)
        ):
            with self.subTest(jackson2=jackson2, jackson3=jackson3):
                with tempfile.TemporaryDirectory() as name:
                    folder = Path(name)
                    (folder / "hazelcast-parent").mkdir()
                    (folder / "hazelcast-parent/pom.xml").write_text(
                        f"<jackson2.version>{jackson2}</jackson2.version>\n"
                        f"<jackson3.version>{jackson3}</jackson3.version>\n", encoding="utf-8"
                    )
                    self.assertEqual(self.run_gate(commands, folder), expected_status)

    def test_packaged_gate_accepts_all_four_patched_properties(self):
        self.assertEqual(self.packaged_gate(EXPECTED), 0)

    def test_packaged_gate_rejects_each_unpatched_embedded_dependency(self):
        for coordinate in EXPECTED:
            with self.subTest(coordinate=coordinate):
                versions = {**EXPECTED, coordinate: "2.22.2" if coordinate.startswith("com.") else "3.2.2"}
                self.assertNotEqual(self.packaged_gate(versions), 0)

    def test_packaged_gate_rejects_missing_or_ambiguous_properties(self):
        for coordinate in EXPECTED:
            with self.subTest(coordinate=coordinate):
                self.assertNotEqual(self.packaged_gate({key: value for key, value in EXPECTED.items()
                                                       if key != coordinate}), 0)
                self.assertNotEqual(self.packaged_gate({**EXPECTED, coordinate: ""}), 0)
                self.assertNotEqual(self.packaged_gate({**EXPECTED, coordinate:
                                                       EXPECTED[coordinate] + "\nversion=" + EXPECTED[coordinate]}), 0)

    def test_maven_evidence_rejects_previous_artifact_identity(self):
        spec = importlib.util.spec_from_file_location(
            "maven_evidence", ROOT / "scripts/pasturestack-verify-maven-evidence.py"
        )
        verifier = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(verifier)
        self.assertEqual(verifier.EXPECTED_RUNTIME, ("com.hazelcast", "hazelcast", "5.7.5"))
        with tempfile.TemporaryDirectory() as name:
            path = Path(name) / "effective-pom.xml"
            for version in ("5.7.5", "5.7.4"):
                path.write_text(
                    '<projects xmlns="http://maven.apache.org/POM/4.0.0"><project>'
                    '<groupId>com.hazelcast</groupId><artifactId>hazelcast</artifactId>'
                    f'<version>{version}</version><dependencies><dependency>'
                    '<artifactId>jackson-databind</artifactId><version>3.2.3</version>'
                    '</dependency></dependencies><build><plugins><plugin>'
                    '<artifactId>maven-compiler-plugin</artifactId><version>3.15.0</version>'
                    '</plugin></plugins></build></project></projects>', encoding="utf-8"
                )
                if version == "5.7.5":
                    self.assertEqual(verifier.validate_effective_pom(path)["effective_pom_projects"], 1)
                else:
                    with self.assertRaisesRegex(ValueError, "expected runtime"):
                        verifier.validate_effective_pom(path)


if __name__ == "__main__":
    unittest.main()

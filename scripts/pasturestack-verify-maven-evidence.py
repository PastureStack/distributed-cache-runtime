#!/usr/bin/env python3
"""Validate the resolved Maven model and the runtime CycloneDX evidence."""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ElementTree


MAVEN_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
EXPECTED_RUNTIME = ("com.hazelcast", "hazelcast", "5.7.2")


def required_text(element, name):
    value = element.findtext("m:" + name, default="", namespaces=MAVEN_NAMESPACE).strip()
    if not value or "${" in value:
        raise ValueError("unresolved Maven " + name + ": " + value)
    return value


def validate_effective_pom(path):
    root = ElementTree.parse(path).getroot()
    projects = root.findall("m:project", MAVEN_NAMESPACE)
    if not projects:
        raise ValueError("effective POM does not contain reactor projects")

    identities = []
    counts = {
        "effective_direct_dependencies": 0,
        "effective_managed_dependencies": 0,
        "effective_plugins": 0,
        "effective_managed_plugins": 0,
    }
    paths = (
        ("effective_direct_dependencies", "m:dependencies/m:dependency"),
        (
            "effective_managed_dependencies",
            "m:dependencyManagement/m:dependencies/m:dependency",
        ),
        ("effective_plugins", "m:build/m:plugins/m:plugin"),
        (
            "effective_managed_plugins",
            "m:build/m:pluginManagement/m:plugins/m:plugin",
        ),
    )

    for project in projects:
        identities.append(
            (
                required_text(project, "groupId"),
                required_text(project, "artifactId"),
                required_text(project, "version"),
            )
        )
        for label, query in paths:
            entries = project.findall(query, MAVEN_NAMESPACE)
            counts[label] += len(entries)
            for entry in entries:
                required_text(entry, "artifactId")
                required_text(entry, "version")

    if len(identities) != len(set(identities)):
        raise ValueError("effective POM contains duplicate reactor coordinates")
    if identities.count(EXPECTED_RUNTIME) != 1:
        raise ValueError("effective POM does not contain the expected runtime exactly once")
    if counts["effective_direct_dependencies"] == 0:
        raise ValueError("effective POM has no resolved direct dependencies")
    if counts["effective_plugins"] == 0:
        raise ValueError("effective POM has no resolved build plugins")

    return {
        "effective_pom_projects": len(projects),
        **counts,
        "effective_unresolved_versions": 0,
    }


def load_json(path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def validate_runtime_sbom(sbom_path, dependency_tree_path):
    sbom = load_json(sbom_path)
    dependency_tree = load_json(dependency_tree_path)
    if sbom.get("specVersion") != "1.6":
        raise ValueError("runtime SBOM is not CycloneDX 1.6")

    components = sbom.get("components") or []
    dependencies = sbom.get("dependencies") or []
    root_component = sbom.get("metadata", {}).get("component") or {}
    sbom_root = (
        root_component.get("group"),
        root_component.get("name"),
        root_component.get("version"),
    )
    maven_root = (
        dependency_tree.get("groupId"),
        dependency_tree.get("artifactId"),
        dependency_tree.get("version"),
    )
    if maven_root != EXPECTED_RUNTIME or sbom_root != EXPECTED_RUNTIME:
        raise ValueError("runtime root coordinate does not match the reviewed release")

    component_refs = [item.get("bom-ref") for item in components]
    component_purls = [item.get("purl") for item in components]
    if not components or None in component_refs or None in component_purls:
        raise ValueError("runtime SBOM contains an incomplete component identity")
    if len(component_refs) != len(set(component_refs)):
        raise ValueError("runtime SBOM contains duplicate component references")
    if len(component_purls) != len(set(component_purls)):
        raise ValueError("runtime SBOM contains duplicate package URLs")
    for item in components:
        if not item.get("group") or not item.get("name") or not item.get("version"):
            raise ValueError("runtime SBOM contains an incomplete Maven coordinate")

    maven_coordinates = set()
    remaining = [dependency_tree]
    while remaining:
        node = remaining.pop()
        if node.get("scope") in {"compile", "runtime"} and str(
            node.get("optional")
        ).lower() != "true":
            maven_coordinates.add(
                (node.get("groupId"), node.get("artifactId"), node.get("version"))
            )
        remaining.extend(node.get("children") or [])

    sbom_coordinates = {
        (item.get("group"), item.get("name"), item.get("version"))
        for item in components
    }
    missing_from_sbom = sorted(maven_coordinates - sbom_coordinates)
    unexpected_in_sbom = sorted(sbom_coordinates - maven_coordinates)
    if missing_from_sbom:
        raise ValueError("runtime SBOM is missing Maven coordinates: " + repr(missing_from_sbom))
    if unexpected_in_sbom:
        raise ValueError(
            "runtime SBOM contains unexpected Maven coordinates: "
            + repr(unexpected_in_sbom)
        )

    root_ref = root_component.get("bom-ref")
    dependency_refs = {item.get("ref") for item in dependencies}
    allowed_refs = set(component_refs) | {root_ref}
    dangling_refs = sorted(
        child
        for item in dependencies
        for child in item.get("dependsOn") or []
        if child not in allowed_refs
    )
    if root_ref not in dependency_refs or dependency_refs != allowed_refs:
        raise ValueError("runtime SBOM dependency graph does not cover every component")
    if dangling_refs:
        raise ValueError("runtime SBOM contains dangling dependency references")
    if not any(item.get("dependsOn") for item in dependencies):
        raise ValueError("runtime SBOM dependency graph is empty")

    return {
        "sbom_spec": sbom["specVersion"],
        "sbom_components": len(components),
        "sbom_dependencies": len(dependencies),
        "maven_runtime_components": len(maven_coordinates),
        "sbom_runtime_missing": 0,
        "sbom_runtime_unexpected": 0,
        "sbom_dangling_dependency_refs": 0,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--effective-pom", required=True, type=Path)
    parser.add_argument("--sbom", required=True, type=Path)
    parser.add_argument("--dependency-tree", required=True, type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    args = parser.parse_args()

    summary = {
        **validate_effective_pom(args.effective_pom),
        **validate_runtime_sbom(args.sbom, args.dependency_tree),
    }
    output = "".join(f"{name}={value}\n" for name, value in summary.items())
    args.summary.write_text(output, encoding="utf-8", newline="\n")
    print(output, end="")


if __name__ == "__main__":
    main()

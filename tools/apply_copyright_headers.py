#!/usr/bin/env python3
"""
Apply copyright headers to Java source files in three groups.

Group 1: org/deltav/ files  -> BeaconStrategists header
Group 2: opennms-model-jakarta files -> Dual copyright header
Group 3: bsm/persistence/ files -> Dual copyright header
"""

import os
import sys
import glob
import subprocess

BEACON_HEADER = """\
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
"""

DUAL_HEADER = """\
/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
"""


def apply_header(file_path: str, new_header: str, skip_if_has_beacon: bool = False) -> str:
    """
    Read a Java file and apply the given header.

    Returns a string describing what was done: 'skipped', 'replaced', 'prepended'.
    """
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # If file already has the BeaconStrategists header, skip
    if skip_if_has_beacon and "Copyright (C) 2026 BeaconStrategists" in content:
        return "skipped"

    stripped = content.lstrip()

    if stripped.startswith("/*"):
        # Find the end of the opening block comment
        end_idx = stripped.find(" */")
        if end_idx == -1:
            # Try alternative: just find the first */ anywhere
            end_idx = stripped.find("*/")
            if end_idx == -1:
                # No closing found — leave file alone
                return "no-close"
            # end_idx points to start of */; skip past it
            end_idx += 2
        else:
            # end_idx points to start of ' */'; skip past ' */'
            end_idx += 3

        # Move past any trailing newline after the closing */
        rest = stripped[end_idx:]
        if rest.startswith("\n"):
            rest = rest[1:]

        new_content = new_header + rest
        action = "replaced"
    else:
        # No header — find the package declaration and prepend header
        new_content = new_header + stripped
        action = "prepended"

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(new_content)

    return action


def find_files_group1(base_dir: str):
    """Find all org/deltav/*.java files recursively under core/."""
    result = subprocess.run(
        ["find", "core/", "-path", "*/org/deltav/*.java"],
        cwd=base_dir,
        capture_output=True,
        text=True,
        check=True,
    )
    return [os.path.join(base_dir, p.strip()) for p in result.stdout.splitlines() if p.strip()]


def find_files_group2(base_dir: str):
    result = subprocess.run(
        ["find", "core/opennms-model-jakarta/src/", "-name", "*.java"],
        cwd=base_dir,
        capture_output=True,
        text=True,
        check=True,
    )
    return [os.path.join(base_dir, p.strip()) for p in result.stdout.splitlines() if p.strip()]


def find_files_group3(base_dir: str):
    result = subprocess.run(
        [
            "find",
            "core/daemon-boot-bsmd/src/main/java/org/opennms/netmgt/bsm/persistence/",
            "-name",
            "*.java",
        ],
        cwd=base_dir,
        capture_output=True,
        text=True,
        check=True,
    )
    return [os.path.join(base_dir, p.strip()) for p in result.stdout.splitlines() if p.strip()]


def process_group(label: str, files: list, header: str, skip_beacon: bool = False):
    counts = {"skipped": 0, "replaced": 0, "prepended": 0, "no-close": 0}
    for path in files:
        action = apply_header(path, header, skip_if_has_beacon=skip_beacon)
        counts[action] += 1
    print(
        f"{label}: {len(files)} files — replaced={counts['replaced']}, "
        f"prepended={counts['prepended']}, skipped={counts['skipped']}, "
        f"no-close={counts['no-close']}"
    )


def main():
    base_dir = "/Users/david/development/src/opennms/delta-v"

    print("Collecting file lists...")
    group1 = find_files_group1(base_dir)
    group2 = find_files_group2(base_dir)
    group3 = find_files_group3(base_dir)

    print(f"Group 1 (org/deltav/): {len(group1)} files")
    print(f"Group 2 (opennms-model-jakarta): {len(group2)} files")
    print(f"Group 3 (bsm/persistence/): {len(group3)} files")
    print()

    print("Processing Group 1: BeaconStrategists header on org/deltav/ files...")
    process_group("Group 1", group1, BEACON_HEADER, skip_beacon=True)

    print("Processing Group 2: Dual copyright header on opennms-model-jakarta files...")
    process_group("Group 2", group2, DUAL_HEADER)

    print("Processing Group 3: Dual copyright header on bsm/persistence/ files...")
    process_group("Group 3", group3, DUAL_HEADER)

    print()
    print("Done.")


if __name__ == "__main__":
    main()

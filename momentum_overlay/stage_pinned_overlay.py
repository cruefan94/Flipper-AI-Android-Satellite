#!/usr/bin/env python3
"""Generate a REVIEWABLE, non-destructive Momentum BLE overlay from pinned source.

Usage:
    python3 momentum_overlay/stage_pinned_overlay.py CLEAN_MOMENTUM_TREE OUTPUT_DIR

Neither the input Momentum tree nor this project's source files are mutated.
OUTPUT_DIR must not exist. Generated files are NOT a compiled or flashable build.
"""
from pathlib import Path
import os
import shutil
import sys
import tempfile

import preflight_pinned_source as pinned
import install_source_overlay as serial
import install_trusted_gap_hook as trust
import install_bt_ai_api as bt_api

SERVICE_REL = Path("targets/f7/ble_glue/services")
SERVICE_FILES = (
    "ai_satellite_service.c",
    "ai_satellite_service.h",
    "ai_satellite_queue.h",
    "ai_satellite_session.h",
    "ai_satellite_frame.h",
    "ai_satellite_replay.h",
    "ai_satellite_trust.h",
)
BT_PRIVATE = "applications/services/bt/bt_service/bt_i.h"
BT_MAIN = "applications/services/bt/bt_service/bt.c"

def prepare(root: Path) -> dict[str, bytes]:
    """Validate every patch entirely in memory; reject source drift."""
    problems = pinned.verify(root)
    if problems:
        raise ValueError("Pinned Momentum source mismatch: " + "; ".join(problems))
    originals = {}
    for name in pinned.EXPECTED_GIT_BLOBS:
        originals[name] = (root/name).read_text(encoding="utf-8")
    patches = dict(originals)
    patches[str(serial.SERIAL_REL)] = serial.transform_serial(patches[str(serial.SERIAL_REL)])
    patches[str(serial.SERIAL_H_REL)] = serial.transform_serial_header(patches[str(serial.SERIAL_H_REL)])
    for name, transform in trust.PATCHES.items():
        patches[name] = transform(patches[name])
    for name, transform in bt_api.PATCH.items():
        patches[name] = transform(patches[name])
    changes = {name: value.encode("utf-8") for name, value in patches.items()
               if value != originals[name]}
    service_root=Path(__file__).resolve().parent/SERVICE_REL
    for filename in SERVICE_FILES:
        original=service_root/filename
        if not original.is_file():
            raise FileNotFoundError(f"Missing experimental service source: {original}")
        changes[str(SERVICE_REL/filename)] = original.read_bytes()
    return changes

def stage(root: Path, destination: Path) -> int:
    root = root.resolve()
    destination = destination.absolute()
    if destination == root or root in destination.parents:
        raise ValueError("Staged output must be outside the input Momentum source tree")
    if destination.is_symlink() or destination.exists():
        raise FileExistsError(f"Output already exists; refusing overwrite: {destination}")
    if not destination.parent.is_dir():
        raise FileNotFoundError(f"Output parent does not exist: {destination.parent}")
    changes=prepare(root)
    work=Path(tempfile.mkdtemp(prefix=".flipper-ai-staging-",dir=destination.parent))
    try:
        for relative, data in changes.items():
            file=work/relative
            file.parent.mkdir(parents=True,exist_ok=True)
            file.write_bytes(data)
        (work/"OVERLAY_UNTESTED.txt").write_text(
            "SOURCE-ONLY EXPERIMENTAL MOMENTUM AI BLE PATCH\n"
            f"Required upstream commit: {pinned.PINNED_MOMENTUM_COMMIT}\n"
            "Do not flash. No firmware compilation or hardware verification.\n"
            "Generated files are intended for review, not an automatic deployment.\n",
            encoding="utf-8")
        work.rename(destination)
        print(f"Staged {len(changes)} patched or added files into {destination}")
        print("Upstream tree unchanged; firmware build and real BLE tests still required.")
        return len(changes)
    finally:
        if work.exists():
            shutil.rmtree(work)

if __name__=="__main__":
    if len(sys.argv)!=3:
        raise SystemExit("Usage: stage_pinned_overlay.py CLEAN_MOMENTUM_TREE OUTPUT_DIR")
    try:
        stage(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).absolute())
    except (ValueError,FileNotFoundError,FileExistsError,OSError) as exc:
        raise SystemExit(f"REFUSING to stage overlay: {exc}")

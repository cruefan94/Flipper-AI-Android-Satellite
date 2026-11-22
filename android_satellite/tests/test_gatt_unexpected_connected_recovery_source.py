#!/usr/bin/env python3
"""Source-level guard for a rare Android GATT callback recovery path.

This does not simulate Bluetooth or prove hardware reconnection.
"""
from pathlib import Path

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/flipperai/satellite/BleSatellite.java").read_text()
start = source.index('if(!transitionConnected()){')
end = source.index('events.status("Connected; discovering AI service")', start)
failure = source[start:end]
assert "recoverTemporaryFailure(-10);" in failure, "Invalid connected transition must schedule recovery"
assert "disconnect();" not in failure, "Do not strand an invalid connected transition"
assert "if(link!=gatt)return;" in source[source.index("onConnectionStateChange"):start], "Stale callbacks must be ignored"
recovery = source[source.index("private synchronized void scheduleRecovery("):source.index("private synchronized void openConnection(")]
for guard in ("userDisconnected", "token!=recoveryToken", "failedDevice!=lastDevice", "gatt!=null"):
    assert guard in recovery, f"Recovery cancellation guard missing: {guard}"
print("GATT unexpected-connected recovery source guard passed (hardware unverified)")

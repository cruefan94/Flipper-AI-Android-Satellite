#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../../" && pwd)
SRC="$ROOT/android_satellite/app/src/main/java/com/flipperai/satellite"
DEST=$(mktemp -d)
trap 'rm -rf "$DEST"' EXIT
javac -d "$DEST" "$SRC/SatelliteProtocol.java" "$SRC/FrameCodec.java" "$SRC/TransportState.java" "$SRC/BondGate.java" "$SRC/InferenceRouter.java" "$SRC/OfflineDiagnosticEngine.java" "$SRC/SatelliteRequestHandler.java" "$SRC/ProtocolSession.java" "$SRC/SessionEpoch.java" "$ROOT/android_satellite/tests/SatelliteCoreTest.java"
java -cp "$DEST" SatelliteCoreTest
javac -cp "$DEST" -d "$DEST" "$ROOT/android_satellite/tests/Ai1PayloadPolicyTest.java"
java -cp "$DEST" Ai1PayloadPolicyTest
javac -cp "$DEST" -d "$DEST" "$ROOT/android_satellite/tests/HardwareDiagnosticCoverageTest.java"
java -cp "$DEST" HardwareDiagnosticCoverageTest
javac -cp "$DEST" -d "$DEST" "$SRC/SelectedLocalEngine.java" "$ROOT/android_satellite/tests/SelectedLocalEngineTest.java"
java -cp "$DEST" SelectedLocalEngineTest
javac -cp "$DEST" -d "$DEST" "$SRC/ModelChecksumPolicy.java" "$ROOT/android_satellite/tests/ModelChecksumPolicyTest.java"
java -cp "$DEST" ModelChecksumPolicyTest
javac -cp "$DEST" -d "$DEST" "$SRC/ModelFileLibrary.java" "$ROOT/android_satellite/tests/ModelFileLibraryTest.java"
java -cp "$DEST" ModelFileLibraryTest
javac -cp "$DEST" -d "$DEST" "$ROOT/android_satellite/tests/SessionEpochTest.java"
java -cp "$DEST" SessionEpochTest
javac -cp "$DEST" -d "$DEST" "$SRC/WorkerEndpointPolicy.java" "$ROOT/android_satellite/tests/WorkerEndpointPolicyTest.java"
java -cp "$DEST" WorkerEndpointPolicyTest
javac -cp "$DEST" -d "$DEST" "$SRC/LocalModelEndpointPolicy.java" "$ROOT/android_satellite/tests/LocalModelEndpointPolicyTest.java"
java -cp "$DEST" LocalModelEndpointPolicyTest
javac -cp "$DEST" -d "$DEST" "$SRC/BleWriteGuard.java" "$ROOT/android_satellite/tests/BleWriteGuardTest.java"
java -cp "$DEST" BleWriteGuardTest
javac -cp "$DEST" -d "$DEST" "$ROOT/android_satellite/tests/TransportStateConcurrencyTest.java"
java -cp "$DEST" TransportStateConcurrencyTest
javac -cp "$DEST" -d "$DEST" "$SRC/SatelliteTransport.java" "$SRC/SatelliteTransportCoordinator.java" "$ROOT/android_satellite/tests/SatelliteTransportCoordinatorTest.java"
java -cp "$DEST" SatelliteTransportCoordinatorTest
javac -cp "$DEST" -d "$DEST" "$SRC/WifiRelayEndpointPolicy.java" "$SRC/WifiRelayEnvelope.java" "$ROOT/android_satellite/tests/WifiRelaySecurityTest.java"
java -cp "$DEST" WifiRelaySecurityTest
javac -cp "$DEST" -d "$DEST" "$SRC/ConsentEpoch.java" "$ROOT/android_satellite/tests/ConsentEpochTest.java"
java -cp "$DEST" ConsentEpochTest

python3 "$ROOT/android_satellite/tests/test_cloud_revocation_source.py"

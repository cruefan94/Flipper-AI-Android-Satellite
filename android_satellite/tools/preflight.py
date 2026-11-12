#!/usr/bin/env python3
"""Fail loudly on incomplete Android source synchronization; does not build an APK."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
package = root / 'app/src/main/java/com/flipperai/satellite'
needed = ['BleSatellite', 'MainActivity', 'SatelliteProtocol', 'ProtocolSession',
          'SatelliteRequestHandler', 'InferenceRouter', 'WorkerCloudEngine',
          'WorkerEndpointPolicy', 'OfflineDiagnosticEngine', 'TransportState',
          'BondGate', 'FrameCodec', 'GattProfilePolicy', 'SessionEpoch', 'SatelliteTransport', 'SatelliteTransportCoordinator', 'WifiRelayEndpointPolicy', 'WifiRelayEnvelope',
          'BleWriteGuard', 'ConsentEpoch', 'LocalModelEngine',
          'LocalModelEndpointPolicy', 'OfflineModelOrDiagnostics']
missing = [str(package / (name + '.java')) for name in needed
           if not (package / (name + '.java')).is_file()]
missing += [str(root / f) for f in ('settings.gradle', 'build.gradle',
           'app/build.gradle', 'app/src/main/AndroidManifest.xml',
           'app/src/main/res/xml/local_model_security.xml')
           if not (root / f).is_file()]
if missing:
    print('ERROR: Android source synchronization incomplete:', file=sys.stderr)
    print('\n'.join('  '+m for m in missing), file=sys.stderr)
    sys.exit(1)
print('Android source preflight passed: required source files present; compilation not yet verified')

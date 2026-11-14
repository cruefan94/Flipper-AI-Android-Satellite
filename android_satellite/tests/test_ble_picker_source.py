#!/usr/bin/env python3
"""Guard BLE picker reliability and one-place offline model installation."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/com/flipperai/satellite"
BLE=(SRC/"MainActivity.java").read_text()
ROWS=(SRC/"BlePickerRows.java").read_text()
MODELS=(SRC/"ModelsActivity.java").read_text()

class BlePickerSourceTests(unittest.TestCase):
    def test_picker_identity_not_matching_untrusted_display_names(self):
        self.assertIn("BlePickerRows.from(labels)",BLE)
        self.assertIn("rows.idAt(spinner.getSelectedItemPosition())",BLE)
        self.assertIn("next.positionFor(selectedId)",BLE)
        self.assertIn("String selectedId=previous.idAt(",BLE)
        self.assertNotIn('if(entry.getValue().equals(label))',BLE)
        self.assertNotIn("String label=spinner.getSelectedItem()",BLE)
        self.assertIn('total>1?title+" • "+slot+"/"+total:title',ROWS)
        self.assertIn("return selectedIndex>=1 && selectedIndex<=ids.size()",ROWS)
        self.assertNotIn('visible.add(e.getKey())',ROWS)

    def test_scan_refresh_is_batched_and_cleared_on_destroy(self):
        self.assertIn("if(changed && !nearbyRefreshScheduled)",BLE)
        self.assertIn("pickerUi.postDelayed(nearbyRefresh,300)",BLE)
        self.assertIn("pickerUi.removeCallbacks(nearbyRefresh)",BLE)
        self.assertIn("nearbyRefreshScheduled=false",BLE)
        self.assertIn("refreshList(nearbyDevices,nearbyLabels())",BLE)
        self.assertIn("boolean changed=!nearby.containsKey(address)",BLE)
        self.assertIn("scanEpoch++;",BLE)

    def test_models_are_managed_only_in_models_screen(self):
        self.assertIn('advancedButton("OPEN OFFLINE MODELS"',BLE)
        self.assertIn("SatelliteUi.open(this,ModelsActivity.class)",BLE)
        self.assertIn('EmbeddedModelStore.importFromDocument(this,selected)',MODELS)
        self.assertNotIn("PICK_EMBEDDED_MODEL",BLE)
        self.assertNotIn("EmbeddedModelStore.importFromDocument(",BLE)
        self.assertNotIn('advancedButton("Remove imported embedded model"',BLE)
        self.assertNotIn('advancedButton("Test embedded AI inference"',BLE)
        self.assertIn("new EmbeddedLlmEngine(",BLE)
        self.assertIn("new EmbeddedOrLocalEngine(embeddedModel,localModel)",BLE)
        self.assertIn("activeModelRevision=modelRevision();",BLE)
        self.assertIn("@Override protected void onResume()",BLE)
        self.assertIn('if(!revision.equals(activeModelRevision)',BLE)
        self.assertIn("inferenceWorker.execute(()->{",BLE)
        self.assertIn("embeddedModel.configure(EmbeddedModelStore.modelFile(this).getAbsolutePath())",BLE)
        self.assertIn("embeddedModel.clear()",BLE)

    def test_previous_models_screen_and_privacy_unchanged(self):
        self.assertIn("new ProgressBar(this)",MODELS)
        self.assertIn("Model installed (",MODELS)
        self.assertIn("EmbeddedModelStore.delete(this)",MODELS)
        self.assertIn("cloud.revokeActive();",BLE)
        self.assertIn("consentEpoch.current(consentGeneration)",BLE)

if __name__=="__main__":
    unittest.main()

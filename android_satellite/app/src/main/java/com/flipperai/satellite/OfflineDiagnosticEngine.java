package com.flipperai.satellite;

import java.util.Locale;

/** Deterministic on-device diagnostics. Not a language model and never operates hardware. */
public final class OfflineDiagnosticEngine implements InferenceRouter.OfflineEngine {
    @Override public boolean ready() { return true; }
    @Override public boolean supports(String prompt) {
        if(prompt==null)return false;
        String s=prompt.toLowerCase(Locale.ROOT);
        return s.contains("battery")||s.contains("charge")||s.contains("power")||
            s.contains("bluetooth")||s.contains("ble")||s.contains("pair")||
            s.contains("wifi")||s.contains("wi-fi")||s.contains("esp32")||
            s.contains("gpio")||s.contains("i2c")||s.contains("sensor")||
            s.contains("storage")||s.contains("sd card")||s.contains("memory");
    }
    @Override public String answer(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return "Enter a device symptom or HELP.";
        String s=prompt.toLowerCase(Locale.ROOT);
        if(s.contains("battery") || s.contains("charge") || s.contains("power"))
            return "Power checks: inspect battery level, charging cable, USB power and system logs. Do not open a lithium battery or short its terminals.";
        if(s.contains("bluetooth") || s.contains("ble") || s.contains("pair"))
            return "BLE checks: turn Bluetooth on, confirm Android Nearby Devices permission, pair the devices and verify the Momentum AI GATT profile is installed. Standard Flipper RPC is not the AI service.";
        if(s.contains("wifi") || s.contains("wi-fi") || s.contains("esp32"))
            return "Wi-Fi checks: verify ESP32-S2 board power and UART connection, 2.4 GHz SSID, board firmware and local network. Wi-Fi is separate from the experimental BLE satellite.";
        if(s.contains("gpio") || s.contains("i2c") || s.contains("sensor"))
            return "GPIO checks: confirm common ground, 3.3 V logic compatibility, pin assignments and sensor datasheet. Disconnect power before rewiring.";
        if(s.contains("storage") || s.contains("sd card") || s.contains("memory"))
            return "Storage checks: back up the microSD card, inspect free space and card contacts, and use built-in storage diagnostics. Do not format without a backup.";
        return "Offline diagnostic topics: battery, BLE pairing, Wi-Fi board, GPIO sensor and SD card. General conversational local LLM is not installed yet.";
    }
}

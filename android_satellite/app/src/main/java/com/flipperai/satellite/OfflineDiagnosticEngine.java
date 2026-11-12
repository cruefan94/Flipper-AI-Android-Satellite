package com.flipperai.satellite;

import java.util.Locale;

/** Read-only offline guidance. Never reads, transmits, replays, or modifies hardware. */
public final class OfflineDiagnosticEngine implements InferenceRouter.OfflineEngine {
    @Override public boolean ready() { return true; }

    private static String normalized(String prompt) {
        return prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
    }

    @Override public boolean supports(String prompt) {
        String s = normalized(prompt);
        return category(s) != 0;
    }

    private static int category(String s) {
        if(s.contains("sub-ghz") || s.contains("subghz") || s.contains("radio") || s.contains("rf signal")) return 1;
        if(s.contains("nfc") || s.contains("13.56 mhz")) return 2;
        if(s.contains("rfid") || s.contains("125 khz") || s.contains("125khz")) return 3;
        if(s.contains("infrared") || s.contains("ir remote") || s.contains("ir signal")) return 4;
        if(s.contains("gpio") || s.contains("i2c") || s.contains("sensor") || s.contains("uart")) return 5;
        if(s.contains("wifi") || s.contains("wi-fi") || s.contains("esp32")) return 6;
        if(s.contains("bluetooth") || s.contains("ble") || s.contains("pair") || s.contains("gatt")) return 7;
        if(s.contains("battery") || s.contains("charge") || s.contains("power")) return 8;
        if(s.contains("storage") || s.contains("sd card") || s.contains("memory")) return 9;
        return 0;
    }

    @Override public String answer(String prompt) {
        if(prompt == null || prompt.trim().isEmpty()) return "Enter a device symptom or HELP.";
        switch(category(normalized(prompt))) {
            case 1: return "Sub-GHz checks: inspect a user-selected saved capture's frequency, modulation and signal quality. Confirm local radio regulations. This guidance does not transmit, replay or decode protected credentials.";
            case 2: return "NFC checks: confirm tag type, antenna alignment and read status using Momentum's NFC app. Share only redacted tag metadata, never keys or private dumps.";
            case 3: return "RFID checks: confirm whether the tag uses supported low-frequency technology and hold it near the reader. Do not share identifiers or attempt unauthorized cloning.";
            case 4: return "Infrared checks: verify line of sight, transmitter orientation and battery level. Use the built-in IR app to test a device you own or are authorized to control.";
            case 5: return "GPIO checks: disconnect power before rewiring; confirm common ground, 3.3 V logic, pinout, UART baud and sensor datasheet. Never connect a 5 V signal directly to a 3.3 V pin.";
            case 6: return "Wi-Fi checks: inspect ESP32-S2 board power, UART wiring and firmware; confirm the 2.4 GHz network and your own access permissions. Wi-Fi is not the BLE AI channel.";
            case 7: return "BLE checks: enable Bluetooth and Android Nearby Devices permission, verify pairing and the dedicated encrypted AI GATT profile. Stock Momentum RPC alone is not the AI service.";
            case 8: return "Power checks: inspect battery level, charging cable, USB power and system logs. Never short or open lithium battery cells.";
            case 9: return "Storage checks: back up microSD data, inspect free space and card contacts, and use built-in storage diagnostics. Do not format without a backup.";
            default: return "Offline diagnostic topics: Sub-GHz, NFC, RFID, infrared, GPIO/UART, Wi-Fi, BLE, battery and microSD. These are rules, not language-model inference.";
        }
    }
}

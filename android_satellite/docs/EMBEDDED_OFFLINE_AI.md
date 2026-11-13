# Embedded offline AI — LiteRT-LM (Android Satellite)

## What has been implemented
- The APK includes Google's LiteRT-LM Android runtime, not a separately installed localhost server.
- A user can select an existing `.litertlm` file from **AI settings → Import embedded AI model**.
- The selected file is streamed (64 KiB at a time) to the app's private storage, then activated.
- **Test embedded AI inference** loads the weights and requests a real model-generated reply.
- Offline requests from the Flipper use the embedded engine when selected, followed by the optional localhost server, then built-in diagnostic rules. Cloud inference stays opt-in.
- The import persists after the app restarts. **Remove imported embedded model** clears the weights.

## How to try on an Android phone
1. Install a current debug APK produced from `develop/android-apk-build`.
2. Download the **Qwen3-0.6B.litertlm** INT8 file (~586 MB) from https://huggingface.co/litert-community/Qwen3-0.6B/tree/main . Check the model license and compatibility. Do **not** select the similarly named `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` file: its current external-weights version is documented to crash LiteRT-LM 0.18.x, which this app pins. A generic GGUF or TFLite file will not work.
3. Open the app → **Show / hide AI settings and diagnostics** → **Import embedded AI model (.litertlm)**, then choose the downloaded file from Android Files.
4. Ensure sufficient free space for an extra private copy of the model during import. A multi-GB model can take considerable storage and time.
5. Tap **Test embedded AI inference**; only a response beginning **Verified LIVE embedded offline model response:** establishes that inference worked on the phone.
6. Leave cloud permission unchecked to keep requests off the internet. Bluetooth GATT and actual Flipper hardware integration need to be tested separately.

## Important limitations
- The APK intentionally contains no weights; the model download requires internet *only for download*. Model inference itself runs without internet.
- The initial backend is CPU for broader device compatibility; GPU support and performance testing are follow-up work.
- Supported file format is `.litertlm` only; different model architectures may need conversion or updated runtime support.
- Model import and inference must execute on the Android inference worker; no native model processing on the main thread.
- Build and JVM/source tests cannot prove successful inference or BLE hardware operation. A physical Android device with a compatible model and Flipper is still required.
- Previous OpenAI-compatible localhost server remains available as an alternative offline backend.
- No AI output is automatically executed as a Flipper hardware command.

## Upstream references
- Android LiteRT-LM API: https://ai.google.dev/edge/litert-lm/android
- Kotlin runtime: https://github.com/google-ai-edge/LiteRT-LM
- Model catalog: https://huggingface.co/litert-community

Model files may have independent licenses. Check them before distributing weights.

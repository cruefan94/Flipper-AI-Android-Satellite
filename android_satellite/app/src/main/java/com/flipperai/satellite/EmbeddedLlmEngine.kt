package com.flipperai.satellite

import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/**
 * First-party Android integration of Google's third-party LiteRT-LM runtime.
 * Weights are imported into app-private files separately; no network access,
 * cloud API keys or autonomous Flipper commands are used by this engine.
 *
 * All costly calls run on MainActivity's single inferenceWorker, never UI.
 * The model and conversation are owned together, closed exactly once.
 */
class EmbeddedLlmEngine(private val cacheDirectory: String) : InferenceRouter.OfflineEngine {
    private var selectedPath: String? = null
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @Synchronized
    fun configure(path: String) {
        val file = File(path)
        require(file.isFile && file.length() > 0L && file.name.endsWith(".litertlm")) {
            "Select a valid imported .litertlm model file"
        }
        releaseLoaded()
        selectedPath = file.absolutePath
    }

    @Synchronized
    fun clear() {
        releaseLoaded()
        selectedPath = null
    }

    @Synchronized
    override fun ready(): Boolean {
        val path = selectedPath ?: return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    @Synchronized
    override fun answer(prompt: String): String {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "On-device AI inference must run on the inference worker"
        }
        PromptLimits.validate(prompt)
        val path = selectedPath ?: throw IllegalStateException("Import an offline .litertlm model first")
        if (!File(path).isFile) throw IllegalStateException("Selected offline model is missing")
        if (engine == null) {
            val candidate = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = cacheDirectory
                )
            )
            // Do not close an engine whose initialize() failed (SDK contract).
            candidate.initialize()
            try {
                val newConversation = candidate.createConversation()
                engine = candidate
                conversation = newConversation
            } catch (t: Throwable) {
                candidate.close()
                throw t
            }
        }
        val reply = checkNotNull(conversation).sendMessage(prompt).toString().trim()
        if (reply.isEmpty()) throw IllegalStateException("Embedded offline model returned no text")
        return reply
    }

    // Must be called on inferenceWorker if inference might be running.
    private fun releaseLoaded() {
        val oldConversation = conversation
        conversation = null
        val oldEngine = engine
        engine = null
        try {
            oldConversation?.close()
        } finally {
            oldEngine?.close()
        }
    }
}

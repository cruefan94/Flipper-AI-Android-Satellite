package com.flipperai.satellite;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

/** In-process, no-HTTP MediaPipe LLM backend. Model weights are user-supplied and
 * remain in app-private files. This backend never invokes cloud inference.
 * Operations are blocking and MUST be called on a worker thread.
 */
public final class EmbeddedModelEngine implements InferenceRouter.OfflineEngine, AutoCloseable {
    private final Context context;
    private LlmInference inference;
    private String modelLabel="";
    public EmbeddedModelEngine(Context applicationContext) {
        if(applicationContext==null)throw new IllegalArgumentException("context");
        context=applicationContext.getApplicationContext();
    }
    /** Load a trusted MediaPipe-compatible .task model from our private directory. */
    public synchronized void load(File model) throws Exception {
        if(model==null || !model.isFile() || !model.getName().endsWith(".task"))
            throw new IllegalArgumentException("A compatible .task model is required");
        File base=new File(context.getFilesDir(),"ai_models").getCanonicalFile();
        File canonical=model.getCanonicalFile();
        if(!canonical.getParentFile().equals(base))
            throw new SecurityException("Model must be imported into app-private storage");
        if(canonical.length()<1024*1024)
            throw new IllegalArgumentException("Model appears incomplete");
        close();
        LlmInference.LlmInferenceOptions opts=LlmInference.LlmInferenceOptions.builder()
            .setModelPath(canonical.getAbsolutePath())
            .setMaxTokens(512)
            .build();
        inference=LlmInference.createFromOptions(context,opts);
        modelLabel=canonical.getName();
    }
    public synchronized String selectedModel() { return modelLabel; }
    @Override public synchronized boolean ready() { return inference!=null; }
    @Override public synchronized String answer(String prompt) throws Exception {
        if(inference==null)throw new IllegalStateException("Import and load an offline model first");
        PhonePromptPolicy.requireValid(prompt);
        String reply=inference.generateResponse(prompt);
        if(reply==null || reply.trim().isEmpty())
            throw new IllegalStateException("Embedded model returned an empty answer");
        return reply.trim();
    }
    @Override public synchronized void close() {
        if(inference!=null) {
            inference.close();
            inference=null;
        }
        modelLabel="";
    }
}

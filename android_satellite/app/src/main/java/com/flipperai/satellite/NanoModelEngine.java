package com.flipperai.satellite;

import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.TimeUnit;

/** Native on-device Gemini Nano inference via AICore. No external prompt endpoint. */
public final class NanoModelEngine {
  private final GenerativeModelFutures client=GenerativeModelFutures.from(Generation.INSTANCE.getClient());
  public int status() throws Exception {return client.checkStatus().get(20,TimeUnit.SECONDS);}
  public boolean available() throws Exception {return status()==FeatureStatus.AVAILABLE;}
  public String answer(String prompt) throws Exception {
    if(prompt==null||prompt.trim().isEmpty())throw new IllegalArgumentException("Enter a question.");
    if(prompt.length()>1500)throw new IllegalArgumentException("Prompt too long (max 1500 characters).");
    if(!available())throw new IllegalStateException("Gemini Nano is not available on this device.");
    GenerateContentResponse response=client.generateContent(
      new GenerateContentRequest.Builder(new TextPart(prompt)).build()).get(90,TimeUnit.SECONDS);
    if(response.getCandidates().isEmpty())throw new IllegalStateException("No model response.");
    String result=response.getCandidates().get(0).getText();
    if(result==null||result.trim().isEmpty())throw new IllegalStateException("Empty model response.");
    return result.trim();
  }
}

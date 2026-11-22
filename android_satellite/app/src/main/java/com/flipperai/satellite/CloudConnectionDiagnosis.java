package com.flipperai.satellite;

/** Diagnostic labels only. Never put arbitrary network exceptions on screen.
 * Exception messages might contain endpoint URLs, tokens or TLS details.
 */
public final class CloudConnectionDiagnosis {
  private CloudConnectionDiagnosis() {}

  public static String explain(Throwable problem) {
    if(problem==null)return "Online AI failed. Check the Worker deployment and Android internet connection.";
    final String detail=problem.getMessage();
    if(detail==null)return "Online AI failed. Check the Worker deployment and Android internet connection.";
    if(detail.equals("Cloud Worker not configured"))
      return "Online AI not configured: save your HTTPS Worker /ask address and device token.";
    if(detail.equals("Cloud authorization revoked"))
      return "Online AI permission ended: return to the Bluetooth screen and enable cloud consent.";
    if(detail.equals("Cloud request already in progress"))
      return "Online AI is busy: another cloud request is still running.";
    if(detail.startsWith("Worker HTTP 401:"))
      return "Worker HTTP 401: the private device token was rejected. Check the Worker secret.";
    if(detail.startsWith("Worker HTTP 403:"))
      return "Worker HTTP 403: Cloudflare denied access. Check model plan and Worker permissions.";
    if(detail.startsWith("Worker HTTP 404:"))
      return "Worker HTTP 404: the /ask route is missing or the Worker URL is incorrect.";
    if(detail.startsWith("Worker HTTP 429:"))
      return "Worker HTTP 429: request limit reached. Wait before trying again.";
    if(detail.startsWith("Worker HTTP 502:"))
      return "Worker HTTP 502: the online AI model or gateway did not return a valid answer.";
    if(detail.startsWith("Worker HTTP 503:") || detail.startsWith("Worker HTTP 504:"))
      return "Worker HTTP 503/504: cloud model unavailable or not configured. Check AI binding, quota and Worker deployment.";
    if(detail.startsWith("Worker returned HTTP "))
      return "Worker returned an HTTP error. Confirm the Worker is deployed and /ask is enabled.";
    if(detail.startsWith("Cloud network or TLS request failed."))
      return "Cannot reach the HTTPS Worker. Check internet, correct workers.dev address and TLS.";
    if(detail.equals("Cloud response has no reply"))
      return "Worker answered but no AI reply was returned. Check model output or Worker logs.";
    if(detail.equals("Cloud response exceeds limit"))
      return "Worker answer was too large. Check the online AI response limit.";
    return "Online AI request failed. Check your Worker /ask deployment, model and quota.";
  }
}

package com.flipperai.satellite;

/** User-safe Cloudflare /ask error diagnostics: never includes token, URL, or response body. */
public final class WorkerHttpStatus {
  private WorkerHttpStatus() {}
  public static String describe(int status) {
    switch(status) {
      case 401:
        return "Worker HTTP 401: authorization rejected. Check the configured device token against the deployed Worker secret; do not paste it into logs.";
      case 403:
        return "Worker HTTP 403: access forbidden. Check Worker access rules and device authorization.";
      case 404:
        return "Worker HTTP 404: /ask endpoint not found. Check the Worker deployment URL and route.";
      case 429:
        return "Worker HTTP 429: rate limit reached. Retry later; do not repeatedly send the same request.";
      case 502:
        return "Worker HTTP 502: upstream AI service or Worker gateway failed. Check private Worker diagnostics and Gemini upstream configuration.";
      case 503:
      case 504:
        return "Worker HTTP "+status+": cloud service temporarily unavailable. Retry later or use offline mode.";
      default:
        return "Worker returned HTTP "+status;
    }
  }
}

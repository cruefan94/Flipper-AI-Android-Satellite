package com.flipperai.satellite;
import java.net.URI;
/** Pure Java Cloudflare Worker URL and token policy; no network side effects. */
public final class WorkerEndpointPolicy {
  private WorkerEndpointPolicy() {}
  public static String validate(String rawUrl,String deviceToken) {
    if(rawUrl==null || deviceToken==null)throw new IllegalArgumentException("Missing cloud configuration");
    URI uri;
    try {uri=URI.create(rawUrl.trim());}
    catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid cloud Worker URL");}
    if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null ||
       uri.getHost()==null || uri.getFragment()!=null || uri.getQuery()!=null ||
       !"/ask".equals(uri.getPath()) || uri.getPort()!=-1)
       throw new IllegalArgumentException("Use an HTTPS Worker URL ending in /ask (default port only)");
    if(deviceToken.length()<16 || deviceToken.length()>256 || !deviceToken.matches("[\\x21-\\x7E]+"))
       throw new IllegalArgumentException("Device token must be 16–256 printable ASCII characters");
    return uri.toString();
  }
}

package com.flipperai.satellite;

import java.net.URI;

/** Never allow localhost inference settings to resolve to another host.
 * The only supported transport is HTTP to the IPv4 loopback interface.
 * No DNS names, credentials, redirects, fragments or query parameters.
 */
public final class LocalModelEndpointPolicy {
  private LocalModelEndpointPolicy(){}
  public static String validate(String input) {
    try {
      if(input==null)throw new IllegalArgumentException("Enter a localhost model URL");
      URI u=new URI(input.trim());
      if(!"http".equals(u.getScheme()) || !"127.0.0.1".equals(u.getHost()) ||
         u.getRawUserInfo()!=null || u.getRawQuery()!=null || u.getRawFragment()!=null ||
         u.getPort()<1024 || u.getPort()>65535 ||
         !"/v1/chat/completions".equals(u.getRawPath()))
        throw new IllegalArgumentException("Local model must use http://127.0.0.1:PORT/v1/chat/completions");
      return u.toASCIIString();
    } catch(java.net.URISyntaxException e) {
      throw new IllegalArgumentException("Invalid localhost model URL",e);
    }
  }
}

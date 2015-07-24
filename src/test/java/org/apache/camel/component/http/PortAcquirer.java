package org.apache.camel.component.http;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * For tests where we need to ensure each one gets a different available port.
 */
public class PortAcquirer {
  static int getUnboundPort() throws IOException {
    return getUnboundPort(10);
  }

  static int getUnboundPort(int maxAttempts) throws IOException {
    while (true) {
      ServerSocket ss;
      try {
        ss = new ServerSocket(0);
        ss.setReuseAddress(true);
        ss.close();
        return ss.getLocalPort();
      } catch (IOException e) {
        if (--maxAttempts <= 0) {
          throw e;
        }
      }
    }
  }
}

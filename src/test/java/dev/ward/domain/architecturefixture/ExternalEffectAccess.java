package dev.ward.domain.architecturefixture;

import java.io.IOException;
import java.net.Socket;

/**
 * Architecture fixture that the boundary rules must reject: it starts processes and opens sockets.
 */
public final class ExternalEffectAccess {
  /** Starts a process, which the domain must not do. */
  public Process launchProcess() throws IOException {
    return Runtime.getRuntime().exec("ward-fixture");
  }

  /** Opens a socket, which the domain must not do. */
  public Socket openSocket() throws IOException {
    return new Socket("localhost", 1);
  }
}

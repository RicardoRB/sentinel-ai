package dev.sentinel.domain.architecturefixture;

import java.io.IOException;
import java.net.Socket;

public final class ExternalEffectAccess {
  public Process launchProcess() throws IOException {
    return Runtime.getRuntime().exec("sentinel-fixture");
  }

  public Socket openSocket() throws IOException {
    return new Socket("localhost", 1);
  }
}

package dev.sentinel.infrastructure.cli;

import javax.inject.Inject;
import javax.inject.Named;
import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

  private final String version;

  public VersionProvider() {
    final String implementation = VersionProvider.class.getPackage().getImplementationVersion();
    this.version = implementation == null || implementation.isBlank() ? "dev" : implementation;
  }

  @Inject
  public VersionProvider(@Named("sentinel.version") final String version) {
    this.version = version;
  }

  @Override
  public String[] getVersion() {
    return new String[] {"sentinel " + version};
  }
}

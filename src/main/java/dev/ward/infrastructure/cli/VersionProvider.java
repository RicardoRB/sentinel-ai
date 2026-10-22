package dev.ward.infrastructure.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Named;
import picocli.CommandLine.IVersionProvider;

/** Supplies the Ward version for {@code --version} and the report header. */
public class VersionProvider implements IVersionProvider {

  private static final Logger LOGGER = Logger.getLogger(VersionProvider.class.getName());
  private static final String RESOURCE = "version.properties";
  private static final String FALLBACK = "dev";
  private static final String KEY = "version=";

  private final String version;

  /** Creates a provider for the version of this build. */
  public VersionProvider() {
    this(current());
  }

  /** Creates a provider for {@code version}. */
  @Inject
  public VersionProvider(@Named("ward.version") final String version) {
    this.version = version;
  }

  /** Reads the build version from a filtered classpath resource, which native images retain. */
  public static String current() {
    return read(VersionProvider.class.getResourceAsStream(RESOURCE));
  }

  static String read(final InputStream resource) {
    if (resource == null) {
      LOGGER.log(Level.FINE, "event=version-resource-missing fallback=dev");
      return FALLBACK;
    }
    final String content;
    try (InputStream stream = resource) {
      content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException exception) {
      LOGGER.log(Level.FINE, "event=version-resource-unreadable fallback=dev");
      return FALLBACK;
    }
    final String version =
        content
            .lines()
            .filter(line -> line.startsWith(KEY))
            .map(line -> line.substring(KEY.length()).strip())
            .findFirst()
            .orElse("");
    return version.isEmpty() || version.startsWith("${") ? FALLBACK : version;
  }

  @Override
  public String[] getVersion() {
    return new String[] {"ward " + version};
  }
}

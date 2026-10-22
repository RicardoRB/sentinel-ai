package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VersionProviderTest {

  @Test
  void currentResolvesFilteredBuildVersion() {
    assertThat(VersionProvider.current()).matches("\\d+\\.\\d+\\.\\d+.*");
  }

  @Test
  void readReturnsDeclaredVersion() {
    assertThat(VersionProvider.read(properties("version=1.2.3 \n"))).isEqualTo("1.2.3");
  }

  @Test
  void readFallsBackToDevWhenVersionIsUnavailable() {
    assertThat(VersionProvider.read(null)).isEqualTo("dev");
    assertThat(VersionProvider.read(properties(""))).isEqualTo("dev");
    assertThat(VersionProvider.read(properties("version=\n"))).isEqualTo("dev");
    assertThat(VersionProvider.read(properties("version=${project.version}\n"))).isEqualTo("dev");
  }

  @Test
  void readFallsBackToDevWhenResourceIsUnreadable() throws IOException {
    try (InputStream failing =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("unreadable");
          }
        }) {
      assertThat(VersionProvider.read(failing)).isEqualTo("dev");
    }
  }

  private static InputStream properties(final String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}

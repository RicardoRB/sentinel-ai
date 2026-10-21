package dev.sentinel.infrastructure.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.sentinel.domain.update.ReleaseVersion;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GithubLatestReleaseLookupTest {
  private static final String PATH = "/releases/latest";
  private static final int FOUND = 302;
  private static final int OK = 200;
  private static final ReleaseVersion V1_4_0 = new ReleaseVersion(1, 4, 0);
  private static final long SLOW_MILLIS = 1_000;
  private static final Duration SHORT = Duration.ofMillis(100);
  private static final Duration NORMAL = Duration.ofSeconds(2);
  private static final String TAG = "https://github.com/RicardoRB/sentinel-ai/releases/tag/v1.4.0";

  private final AtomicReference<String> method = new AtomicReference<>();
  private HttpServer server;
  private final AtomicInteger status = new AtomicInteger();
  private final AtomicReference<String> location = new AtomicReference<>();
  private final AtomicLong delayMillis = new AtomicLong();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        PATH,
        exchange -> {
          method.set(exchange.getRequestMethod());
          try {
            Thread.sleep(delayMillis.get());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          if (location.get() != null) {
            exchange.getResponseHeaders().add("Location", location.get());
          }
          exchange.sendResponseHeaders(status.get(), -1);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private GithubLatestReleaseLookup lookup(final Duration timeout) {
    return new GithubLatestReleaseLookup(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + PATH), timeout);
  }

  @Test
  void readsTheTagFromTheRedirectTarget() {
    status.set(FOUND);
    location.set(TAG);

    assertThat(lookup(NORMAL).latest()).contains(V1_4_0);
    assertThat(method).hasValue("HEAD");
  }

  @Test
  void unexpectedResponsesAreUnknown() {
    status.set(OK);
    assertThat(lookup(NORMAL).latest()).isEmpty();

    status.set(FOUND);
    location.set("https://github.com/RicardoRB/sentinel-ai/releases");
    assertThat(lookup(NORMAL).latest()).isEmpty();

    location.set(null);
    assertThat(lookup(NORMAL).latest()).isEmpty();
  }

  @Test
  void slowServerTimesOutAsUnknown() {
    status.set(FOUND);
    location.set(TAG);
    delayMillis.set(SLOW_MILLIS);

    assertThat(lookup(SHORT).latest()).isEmpty();
  }

  @Test
  void unreachableServerIsUnknown() {
    server.stop(0);

    assertThat(lookup(NORMAL).latest()).isEmpty();
  }
}

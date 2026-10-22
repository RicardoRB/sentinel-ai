package dev.ward.infrastructure.update;

import dev.ward.domain.update.LatestReleaseLookup;
import dev.ward.domain.update.ReleaseVersion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the latest release tag from the redirect target of GitHub's {@code /releases/latest} page,
 * which avoids the rate-limited REST API.
 */
public final class GithubLatestReleaseLookup implements LatestReleaseLookup {
  private static final Logger LOGGER = Logger.getLogger(GithubLatestReleaseLookup.class.getName());
  private static final URI LATEST =
      URI.create("https://github.com/RicardoRB/ward-ai/releases/latest");
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final int REDIRECT_MIN = 300;
  private static final int REDIRECT_MAX = 399;

  private final URI latest;
  private final Duration timeout;

  /** Creates a lookup against the Ward GitHub repository. */
  public GithubLatestReleaseLookup() {
    this(LATEST, TIMEOUT);
  }

  /** Creates a lookup against {@code latest}, giving up after {@code timeout}. */
  public GithubLatestReleaseLookup(final URI latest, final Duration timeout) {
    this.latest = latest;
    this.timeout = timeout;
  }

  @Override
  public Optional<ReleaseVersion> latest() {
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build()) {
      final HttpResponse<Void> response =
          client.send(
              HttpRequest.newBuilder(latest)
                  .method("HEAD", HttpRequest.BodyPublishers.noBody())
                  .timeout(timeout)
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      return releaseFrom(response);
    } catch (HttpTimeoutException e) {
      return unknown("timeout");
    } catch (IOException e) {
      return unknown("io-failure");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return unknown("interrupted");
    }
  }

  private static Optional<ReleaseVersion> releaseFrom(final HttpResponse<Void> response) {
    final int status = response.statusCode();
    if (status < REDIRECT_MIN || status > REDIRECT_MAX) {
      return unknown("unexpected-response");
    }
    final Optional<ReleaseVersion> version =
        response
            .headers()
            .firstValue("Location")
            .map(location -> location.substring(location.lastIndexOf('/') + 1))
            .flatMap(ReleaseVersion::parse);
    return version.isPresent() ? version : unknown("unexpected-response");
  }

  private static Optional<ReleaseVersion> unknown(final String category) {
    LOGGER.log(Level.FINE, () -> "event=update-lookup status=unknown reason=" + category);
    return Optional.empty();
  }
}

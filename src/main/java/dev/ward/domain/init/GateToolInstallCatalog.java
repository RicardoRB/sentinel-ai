package dev.ward.domain.init;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Pure catalog of supported installation methods and remediation links for gate executables. */
public final class GateToolInstallCatalog {
  private static final Map<String, Entry> ENTRIES =
      Map.of(
          "trivy",
              entry(
                  "trivy",
                  "https://trivy.dev/latest/getting-started/installation/",
                  method("brew", "install", "trivy")),
          "gitleaks",
              entry(
                  "gitleaks",
                  "https://github.com/gitleaks/gitleaks#installing",
                  method("brew", "install", "gitleaks")),
          "semgrep",
              new Entry(
                  "semgrep",
                  List.of(
                      method("brew", "install", "semgrep"), method("pipx", "install", "semgrep")),
                  "https://semgrep.dev/docs/getting-started/"),
          "zap-baseline.py",
              entry("zap-baseline.py", "https://www.zaproxy.org/docs/docker/baseline-scan/"),
          "vacuum",
              entry(
                  "vacuum",
                  "https://quobix.com/vacuum/installing/",
                  method("brew", "install", "vacuum")),
          "oasdiff",
              entry(
                  "oasdiff",
                  "https://github.com/oasdiff/oasdiff#installation",
                  method("brew", "install", "oasdiff")));

  private GateToolInstallCatalog() {}

  /** Returns how to install {@code executable}, if the catalog knows it. */
  public static Optional<Entry> forExecutable(final String executable) {
    return Optional.ofNullable(ENTRIES.get(executable));
  }

  /**
   * Returns the manual installation guidance for {@code executable}, or {@code null} when unknown.
   */
  public static String installHint(final String executable) {
    return forExecutable(executable).map(Entry::manualGuidance).orElse(null);
  }

  private static Entry entry(
      final String executable, final String guidance, final InstallMethod... methods) {
    return new Entry(executable, List.of(methods), guidance);
  }

  private static InstallMethod method(final String manager, final String... arguments) {
    return new InstallMethod(
        manager, Stream.concat(List.of(manager).stream(), List.of(arguments).stream()).toList());
  }

  /**
   * How to install one gate executable.
   *
   * @param executable the executable name on the path
   * @param methods package-manager commands, in order of preference
   * @param manualGuidance where to read installation instructions
   */
  public record Entry(String executable, List<InstallMethod> methods, String manualGuidance) {
    /** Copies the methods. */
    public Entry {
      methods = List.copyOf(methods);
    }
  }

  /**
   * One package-manager command that installs an executable.
   *
   * @param manager the package manager executable
   * @param arguments the arguments passed to it
   */
  public record InstallMethod(String manager, List<String> arguments) {
    /** Copies the arguments. */
    public InstallMethod {
      arguments = List.copyOf(arguments);
    }
  }
}

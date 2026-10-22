package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders the GitHub Actions workflow that runs the {@code ci} profile and uploads SARIF. */
final class CiWorkflowTemplate {
  static final String GITHUB = "github";
  static final String DEVELOPMENT_VERSION = "dev";
  static final String BEGIN = "# ward-owned: github-ci v1 begin";
  static final String END = "# ward-owned: github-ci v1 end";
  private static final String FILE = ".github/workflows/ward.yml";
  private static final String GITLEAKS_VERSION = "8.30.1";
  private static final String TRIVY_VERSION = "0.76.0";
  private static final String SEMGREP_VERSION = "1.181.0";
  private static final String OPENSPEC_VERSION = "1.14.1";
  private static final String STEP = "      - name: ";

  private CiWorkflowTemplate() {}

  /**
   * Returns the workflow for {@code provider}, installing the tools the selected gates run.
   *
   * @throws WardException if {@code provider} is not supported
   */
  static OwnedSection render(
      final String provider,
      final Project project,
      final List<InitGateOption> gates,
      final String wardVersion) {
    if (!GITHUB.equals(provider)) {
      throw new WardException(
          "Unknown CI provider '" + provider + "'. Supported providers: " + GITHUB);
    }
    final Set<String> ids = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final StringBuilder body = new StringBuilder(header(ids, project));
    body.append(tools(ids));
    if (ids.contains("trivy")) {
      body.append(
          STEP
              + "Restore trivy cache\n"
              + "        uses: actions/cache/restore@v4\n"
              + "        with:\n"
              + "          path: ~/.cache/trivy\n"
              + "          key: trivy-${{ github.run_id }}\n"
              + "          restore-keys: trivy-\n");
    }
    body.append(ward(wardVersion, ids.contains("dependency-check")));
    if (ids.contains("trivy")) {
      body.append(
          STEP
              + "Save trivy cache\n"
              + "        if: always()\n"
              + "        uses: actions/cache/save@v4\n"
              + "        with:\n"
              + "          path: ~/.cache/trivy\n"
              + "          key: trivy-${{ github.run_id }}\n");
    }
    return new OwnedSection(project.root().resolve(FILE), BEGIN, END, body.toString(), true);
  }

  private static String header(final Set<String> ids, final Project project) {
    return "name: Ward\n\n"
        + "on:\n"
        + "  pull_request:\n"
        + "  push:\n"
        + "    branches: [main]\n\n"
        + "permissions:\n"
        + "  contents: read\n\n"
        + "jobs:\n"
        + "  quality:\n"
        + "    name: Ward ci profile\n"
        + "    runs-on: ubuntu-24.04\n"
        + "    permissions:\n"
        + "      contents: read\n"
        + "      security-events: write\n"
        + "    steps:\n"
        + "      - uses: actions/checkout@v5\n"
        // gitleaks scans the whole history.
        + (ids.contains("gitleaks") ? "        with:\n          fetch-depth: 0\n" : "")
        + STEP
        + "Set up JDK\n"
        + "        uses: actions/setup-java@v5\n"
        + "        with:\n"
        + "          distribution: temurin\n"
        // shortcut: always JDK 25, which compiles older releases; read the project's toolchain if a
        // build pins a newer one.
        + "          java-version: '25'\n"
        + "          cache: "
        + (project.buildTool() == BuildTool.GRADLE ? "gradle" : "maven")
        + "\n";
  }

  private static String tools(final Set<String> ids) {
    final List<String> install = new ArrayList<>();
    if (ids.contains("gitleaks")) {
      install.add(
          "          curl -sSfL"
              + " \"https://github.com/gitleaks/gitleaks/releases/download/v"
              + GITLEAKS_VERSION
              + "/gitleaks_"
              + GITLEAKS_VERSION
              + "_linux_x64.tar.gz\" | sudo tar -xz -C /usr/local/bin gitleaks\n");
    }
    if (ids.contains("trivy")) {
      install.add(
          "          curl -sSfL"
              + " https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh"
              + " | sudo sh -s -- -b /usr/local/bin v"
              + TRIVY_VERSION
              + "\n");
    }
    if (ids.contains("semgrep")) {
      install.add("          pip install semgrep==" + SEMGREP_VERSION + "\n");
    }
    if (ids.contains("openspec") || ids.contains("openspec-archived")) {
      install.add("          npm install -g @fission-ai/openspec@" + OPENSPEC_VERSION + "\n");
    }
    // The runner's system Python refuses global pip installs.
    final String python =
        ids.contains("semgrep")
            ? STEP
                + "Set up Python\n"
                + "        uses: actions/setup-python@v6\n"
                + "        with:\n"
                + "          python-version: '3.13'\n"
            : "";
    return install.isEmpty()
        ? ""
        : python
            + STEP
            + "Install gate tools\n        run: |\n          set -eu\n"
            + String.join("", install);
  }

  private static String ward(final String version, final boolean nvdKey) {
    return STEP
        + "Install Ward\n"
        + "        env:\n"
        + (DEVELOPMENT_VERSION.equals(version) ? "" : "          WARD_VERSION: v" + version + "\n")
        + "          WARD_INSTALL_DIR: ${{ runner.temp }}/ward/bin\n"
        + "        run: |\n"
        + "          curl -fsSL"
        + " https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh\n"
        + "          echo \"$WARD_INSTALL_DIR\" >> \"$GITHUB_PATH\"\n"
        + STEP
        + "Run quality gates\n"
        + (nvdKey ? "        env:\n          NVD_API_KEY: ${{ secrets.NVD_API_KEY }}\n" : "")
        + "        run: ward check --profile ci --format sarif --output ward.sarif\n"
        // Fork tokens cannot write security events; no file means Ward could not run at all.
        + STEP
        + "Upload SARIF to code scanning\n"
        + "        if: >-\n"
        + "          always()\n"
        + "          && github.event_name == 'pull_request'\n"
        + "          && github.event.pull_request.head.repo.full_name == github.repository\n"
        + "          && hashFiles('ward.sarif') != ''\n"
        + "        uses: github/codeql-action/upload-sarif@v4\n"
        + "        with:\n"
        + "          sarif_file: ward.sarif\n"
        + "          category: ward\n";
  }
}

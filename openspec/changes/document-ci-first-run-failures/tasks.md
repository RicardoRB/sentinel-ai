# Tasks

## 1. GitHub Actions guidance

- [ ] 1.1 Update the GitHub Actions example in `website/src/content/docs/guides/ci-cd.mdx` with `fetch-depth: 0`, native Sentinel installation, and explicit Trivy plus optional Dependency-Check cache restore/save steps; verify the YAML matches the documented action syntax and the existing repository workflow's ordering.
- [ ] 1.2 Document that build-tool gates must resolve dependencies before offline Trivy, and explain the trade-off and stricter alternatives; verify the commands and rationale agree with `sentinel.toml` and the existing Trivy configuration.

## 2. First-run troubleshooting

- [ ] 2.1 Add a concise “Common CI failures” section covering Gitleaks history, caches saved after failures, Maven Central `429` responses, Dependency-Check credentials and runtime, and native-binary installation; verify every issue from the acceptance criteria has a cause and actionable remediation.
- [ ] 2.2 Clarify that project runtimes and standalone gate tools remain separate prerequisites from the Sentinel native binary; verify the guidance does not imply that the installer provisions Java, Maven, Node, Python, or security tools.

## 3. Documentation validation

- [ ] 3.1 Build the documentation site from `website/` and verify the CI/CD page renders without broken links or Markdown/YAML errors.
- [ ] 3.2 Review the final guide against the issue acceptance criteria and `.github/workflows/verify.yml`; verify no Java, CLI, configuration, or workflow source files were changed.

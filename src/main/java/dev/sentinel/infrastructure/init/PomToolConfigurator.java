package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.CompatibilityFinding;
import dev.sentinel.domain.init.CompatibilityInspection;
import dev.sentinel.domain.init.CompatibilityPlan;
import dev.sentinel.domain.init.CompatibilityResolver;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.ManagedPluginCatalog;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Adds only missing, pinned Maven tool declarations required by selected init gates. */
public final class PomToolConfigurator implements BuildToolConfiguration {
  private static final String CHECKSTYLE = "maven-checkstyle-plugin";
  private static final String PMD = "maven-pmd-plugin";
  private static final String SPOTBUGS = "spotbugs-maven-plugin";
  private static final String SONAR = "sonar-maven-plugin";
  private static final String ARCHUNIT = "archunit-junit5";
  private static final String JACOCO = "jacoco-maven-plugin";
  private static final String DEPENDENCY_CHECK = "dependency-check-maven";
  private static final String PIT = "pitest-maven";
  private static final String ENFORCER = "maven-enforcer-plugin";
  private static final String SPOTLESS = "spotless-maven-plugin";
  private static final String LICENSE = "license-maven-plugin";
  private static final String JAPICMP = "japicmp-maven-plugin";

  private static final String SPOTLESS_PLUGIN =
      """
              <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.44.4</version>
                <configuration>
                  <java>
                    <googleJavaFormat/>
                  </java>
                </configuration>
              </plugin>
            """;
  private static final String LICENSE_PLUGIN =
      """
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>license-maven-plugin</artifactId>
                <version>2.5.0</version>
                <configuration>
                  <failOnBlacklist>true</failOnBlacklist>
                  <excludedLicenses>
                    <excludedLicense>GNU General Public License (GPL)</excludedLicense>
                    <excludedLicense>GNU Affero General Public License (AGPL)</excludedLicense>
                  </excludedLicenses>
                </configuration>
              </plugin>
            """;
  private static final String JAPICMP_PLUGIN =
      """
              <plugin>
                <groupId>com.github.siom79.japicmp</groupId>
                <artifactId>japicmp-maven-plugin</artifactId>
                <version>0.23.1</version>
                <configuration>
                  <oldVersion>
                    <dependency>
                      <groupId>${project.groupId}</groupId>
                      <artifactId>${project.artifactId}</artifactId>
                      <version>RELEASE</version>
                      <type>jar</type>
                    </dependency>
                  </oldVersion>
                  <parameter>
                    <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
                  </parameter>
                </configuration>
              </plugin>
            """;

  private static final String CHECKSTYLE_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.6.0</version>
              </plugin>
            """;
  private static final String SPOTBUGS_PLUGIN =
      """
              <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>4.9.7.0</version>
              </plugin>
            """;
  private static final String SONAR_PLUGIN =
      """
              <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>5.1.0.4751</version>
              </plugin>
            """;
  private static final String ARCHUNIT_DEPENDENCY =
      """
              <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>1.3.0</version>
                <scope>test</scope>
              </dependency>
            """;
  private static final String JACOCO_PLUGIN =
      """
              <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.13</version>
                <configuration>
                  <rules>
                    <rule>
                      <element>BUNDLE</element>
                      <limits>
                        <limit>
                          <counter>LINE</counter>
                          <value>COVEREDRATIO</value>
                          <minimum>0.80</minimum>
                        </limit>
                      </limits>
                    </rule>
                  </rules>
                </configuration>
              </plugin>
            """;
  private static final String DEPENDENCY_CHECK_PLUGIN =
      """
              <plugin>
                <groupId>org.owasp</groupId>
                <artifactId>dependency-check-maven</artifactId>
                <version>12.1.0</version>
                <configuration>
                  <failBuildOnCVSS>7</failBuildOnCVSS>
                </configuration>
              </plugin>
            """;
  static final String PIT_PLUGIN =
      """
              <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.17.4</version>
                <dependencies>
                  <dependency>
                    <groupId>org.pitest</groupId>
                    <artifactId>pitest-junit5-plugin</artifactId>
                    <version>1.2.1</version>
                  </dependency>
                </dependencies>
              </plugin>
            """;
  private static final String ENFORCER_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                  <rules>
                    <dependencyConvergence/>
                  </rules>
                  <fail>true</fail>
                </configuration>
              </plugin>
            """;
  private static final String PMD_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.26.0</version>
              </plugin>
            """;

  /** Tools in the order they are added to pom.xml. */
  private static final List<Tool> TOOLS =
      List.of(
          Tool.plugin(
              "checkstyle",
              CHECKSTYLE,
              CHECKSTYLE_PLUGIN,
              preset -> PresetPluginSnippets.checkstyle()),
          Tool.plugin("pmd", PMD, PMD_PLUGIN, preset -> PresetPluginSnippets.pmd()),
          Tool.plugin("spotbugs", SPOTBUGS, SPOTBUGS_PLUGIN, PresetPluginSnippets::spotbugs),
          Tool.plugin("sonar", SONAR, SONAR_PLUGIN),
          Tool.plugin("coverage", JACOCO, JACOCO_PLUGIN, PresetPluginSnippets::jacoco),
          Tool.plugin("dependency-check", DEPENDENCY_CHECK, DEPENDENCY_CHECK_PLUGIN),
          new Tool("archunit", ARCHUNIT, ARCHUNIT_DEPENDENCY, null, true),
          Tool.plugin("mutation", PIT, PIT_PLUGIN, PresetPluginSnippets::pit),
          Tool.plugin(
              "enforcer", ENFORCER, ENFORCER_PLUGIN, preset -> PresetPluginSnippets.enforcer()),
          Tool.plugin("format", SPOTLESS, SPOTLESS_PLUGIN),
          Tool.plugin("license", LICENSE, LICENSE_PLUGIN),
          Tool.plugin("api-compat", JAPICMP, JAPICMP_PLUGIN));

  private final CompatibilityInspection compatibilityInspection;
  private final boolean compatibilityEnabled;
  private final CompatibilityResolver compatibilityResolver = new CompatibilityResolver();

  @Inject
  public PomToolConfigurator(final CompatibilityInspection compatibilityInspection) {
    this.compatibilityInspection = compatibilityInspection;
    this.compatibilityEnabled = true;
  }

  /** Legacy construction retained for focused configurator fixtures. */
  public PomToolConfigurator() {
    this.compatibilityInspection = null;
    this.compatibilityEnabled = false;
  }

  @Override
  public PomChange apply(final Project project, final List<InitGateOption> gates) {
    return apply(project, gates, null);
  }

  @Override
  public PomChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates, preset, new Object());
  }

  @Override
  public PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Object approval) {
    if (!compatibilityEnabled) {
      return applyLegacy(project, gates, preset);
    }
    final Path pom = project.root().resolve("pom.xml");
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    try {
      final String original = Files.readString(pom);
      String updated = original;
      final List<String> tools = new ArrayList<>();
      final List<String> warnings = new ArrayList<>();
      final List<String> managed =
          TOOLS.stream()
              .filter(tool -> selected.contains(tool.gate()))
              .map(Tool::artifact)
              .filter(ManagedPluginCatalog.entries()::containsKey)
              .toList();
      final CompatibilityPlan plan =
          compatibilityResolver.resolve(
              compatibilityInspection.inspect(project), ManagedPluginCatalog.entries(), managed);
      if (plan.hasUnresolvableFindings()) {
        throw new SentinelException(
            plan.findings().stream()
                .filter(
                    finding ->
                        "UNKNOWN".equals(finding.outcome().name())
                            || "UNRESOLVABLE".equals(finding.outcome().name())
                            || "INCOMPATIBLE".equals(finding.outcome().name()))
                .map(CompatibilityFinding::message)
                .collect(Collectors.joining(" ")));
      }
      final Map<String, CompatibilityFinding> findings =
          plan.findings().stream()
              .collect(Collectors.toMap(CompatibilityFinding::artifactId, finding -> finding));
      for (final Tool tool : TOOLS) {
        if (!selected.contains(tool.gate())) {
          continue;
        }
        updated =
            applySelectedTool(
                tool, preset, findings.get(tool.artifact()), approval, updated, tools, warnings);
      }
      if (!tools.isEmpty()) {
        PomDeclarationValidator.validate(updated, addedDeclarations(original, updated, selected));
        Files.writeString(
            pom, updated, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      }
      return new PomChange(pom, original, tools, warnings);
    } catch (IOException e) {
      throw new SentinelException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }

  private PomChange applyLegacy(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    final Path pom = project.root().resolve("pom.xml");
    final Set<String> selected = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    try {
      final String original = Files.readString(pom);
      String updated = original;
      final List<String> tools = new ArrayList<>();
      final List<String> warnings = new ArrayList<>();
      for (final Tool tool : TOOLS) {
        if (!selected.contains(tool.gate())) {
          continue;
        }
        if (containsArtifact(updated, tool.artifact())) {
          if (preset != null && tool.presetSnippet() != null) {
            warnings.add(
                tool.artifact() + " already declared; " + preset.id() + " settings not applied");
          }
        } else {
          updated = tool.addTo(updated, preset, null);
          tools.add(tool.artifact());
        }
      }
      if (!tools.isEmpty()) {
        PomDeclarationValidator.validate(updated, addedDeclarations(original, updated, selected));
        Files.writeString(
            pom, updated, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      }
      return new PomChange(pom, original, tools, warnings);
    } catch (IOException e) {
      throw new SentinelException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }

  @Override
  public void rollback(final PomChange change) {
    if (change == null || !change.changed()) {
      return;
    }
    try {
      Files.writeString(
          change.file(),
          change.originalContent(),
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new SentinelException("Could not restore " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private String applySelectedTool(
      final Tool tool,
      final QualityPreset preset,
      final CompatibilityFinding finding,
      final Object approval,
      final String currentPom,
      final List<String> tools,
      final List<String> warnings) {
    String updated = currentPom;
    if (finding != null && !containsArtifact(updated, tool.artifact())) {
      updated = tool.addTo(updated, preset, finding.selectedVersion());
      tools.add(tool.artifact() + " " + finding.selectedVersion());
    } else if (finding != null && finding.changesVersion()) {
      final boolean accepted =
          approval instanceof Predicate<?> rawPredicate
              && ((Predicate<Object>) rawPredicate).test(finding);
      if (accepted) {
        updated = updateVersion(updated, tool.artifact(), finding, tools, warnings);
      } else {
        warnings.add(
            "Compatibility update declined for "
                + tool.artifact()
                + "; selected gate was not reconfigured.");
      }
    } else if (containsArtifact(updated, tool.artifact())) {
      if (preset != null && tool.presetSnippet() != null) {
        warnings.add(
            tool.artifact() + " already declared; " + preset.id() + " settings not applied");
      }
    } else {
      updated = tool.addTo(updated, preset, null);
      tools.add(tool.artifact());
    }
    return updated;
  }

  /** Declarations for the selected tools that this run added to the pom. */
  private static List<PomDeclarationValidator.Declaration> addedDeclarations(
      final String original, final String updated, final Set<String> selected) {
    return TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .filter(tool -> !containsArtifact(original, tool.artifact()))
        .filter(tool -> containsArtifact(updated, tool.artifact()))
        .map(tool -> new PomDeclarationValidator.Declaration(tool.artifact(), tool.dependency()))
        .toList();
  }

  private static boolean containsArtifact(final String pom, final String artifactId) {
    return pom.contains("<artifactId>" + artifactId + "</artifactId>");
  }

  private static String updateVersion(
      final String pom,
      final String artifact,
      final CompatibilityFinding finding,
      final List<String> tools,
      final List<String> warnings) {
    final Optional<String> updated =
        PomXml.replacePluginVersion(
            pom, artifact, finding.currentVersion(), finding.selectedVersion());
    if (updated.isEmpty()) {
      warnings.add(
          "Compatibility update skipped for "
              + artifact
              + " because it is not declared in the project-level build; selected gate was not"
              + " reconfigured.");
      return pom;
    }
    tools.add(artifact + " " + finding.currentVersion() + " -> " + finding.selectedVersion());
    return updated.get();
  }

  /**
   * A Maven tool added for a selected gate. Its preset snippet, when present, replaces the default
   * snippet under a quality preset.
   */
  private record Tool(
      String gate,
      String artifact,
      String snippet,
      Function<QualityPreset, String> presetSnippet,
      boolean dependency) {
    static Tool plugin(final String gate, final String artifact, final String snippet) {
      return new Tool(gate, artifact, snippet, null, false);
    }

    static Tool plugin(
        final String gate,
        final String artifact,
        final String snippet,
        final Function<QualityPreset, String> presetSnippet) {
      return new Tool(gate, artifact, snippet, presetSnippet, false);
    }

    String addTo(final String pom, final QualityPreset preset, final String selectedVersion) {
      final String chosen =
          preset == null || presetSnippet == null ? snippet : presetSnippet.apply(preset);
      final String versioned =
          selectedVersion == null
              ? chosen
              : chosen.replaceFirst(
                  "<version>[^<]+</version>",
                  Matcher.quoteReplacement("<version>" + selectedVersion + "</version>"));
      return dependency ? PomXml.addDependency(pom, versioned) : PomXml.addPlugin(pom, versioned);
    }
  }
}

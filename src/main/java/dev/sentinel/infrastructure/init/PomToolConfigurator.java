package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Adds only missing, pinned Maven tool declarations required by selected init gates. */
public final class PomToolConfigurator implements BuildToolConfiguration {
  private static final String CHECKSTYLE = "maven-checkstyle-plugin";
  private static final String SPOTBUGS = "spotbugs-maven-plugin";
  private static final String SONAR = "sonar-maven-plugin";
  private static final String ARCHUNIT = "archunit-junit5";
  private static final String JACOCO = "jacoco-maven-plugin";
  private static final String DEPENDENCY_CHECK = "dependency-check-maven";
  private static final String PIT = "pitest-maven";
  private static final String ENFORCER = "maven-enforcer-plugin";

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
  private static final String PIT_PLUGIN =
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

  public PomChange apply(Project project, List<InitGateOption> gates) {
    Path pom = project.root().resolve("pom.xml");
    try {
      String original = Files.readString(pom);
      String updated = original;
      List<String> tools = new ArrayList<>();
      boolean needsArchUnit = gates.stream().anyMatch(gate -> gate.id().equals("archunit"));
      if (gates.stream().anyMatch(gate -> gate.id().equals("checkstyle"))
          && !containsArtifact(updated, CHECKSTYLE)) {
        updated = addPlugin(updated, CHECKSTYLE_PLUGIN);
        tools.add(CHECKSTYLE);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("spotbugs"))
          && !containsArtifact(updated, SPOTBUGS)) {
        updated = addPlugin(updated, SPOTBUGS_PLUGIN);
        tools.add(SPOTBUGS);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("sonar"))
          && !containsArtifact(updated, SONAR)) {
        updated = addPlugin(updated, SONAR_PLUGIN);
        tools.add(SONAR);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("coverage"))
          && !containsArtifact(updated, JACOCO)) {
        updated = addPlugin(updated, JACOCO_PLUGIN);
        tools.add(JACOCO);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("dependency-check"))
          && !containsArtifact(updated, DEPENDENCY_CHECK)) {
        updated = addPlugin(updated, DEPENDENCY_CHECK_PLUGIN);
        tools.add(DEPENDENCY_CHECK);
      }
      if (needsArchUnit && !containsArtifact(updated, ARCHUNIT)) {
        updated = addDependency(updated, ARCHUNIT_DEPENDENCY);
        tools.add(ARCHUNIT);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("mutation"))
          && !containsArtifact(updated, PIT)) {
        updated = addPlugin(updated, PIT_PLUGIN);
        tools.add(PIT);
      }
      if (gates.stream().anyMatch(gate -> gate.id().equals("compliance"))
          && !containsArtifact(updated, ENFORCER)) {
        updated = addPlugin(updated, ENFORCER_PLUGIN);
        tools.add(ENFORCER);
      }
      if (!tools.isEmpty()) {
        Files.writeString(
            pom, updated, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      }
      return new PomChange(pom, original, tools);
    } catch (IOException e) {
      throw new SentinelException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }

  @Override
  public void rollback(PomChange change) {
    if (change == null || !change.changed()) return;
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

  private static boolean containsArtifact(String pom, String artifactId) {
    return pom.contains("<artifactId>" + artifactId + "</artifactId>");
  }

  private static String addPlugin(String pom, String plugin) {
    if (pom.contains("</plugins>")) return pom.replaceFirst("</plugins>", plugin + "  </plugins>");
    if (pom.contains("</build>")) {
      return pom.replaceFirst("</build>", "  <plugins>\n" + plugin + "  </plugins>\n</build>");
    }
    if (!pom.contains("</project>")) {
      throw new SentinelException("Cannot safely update pom.xml: missing </project> element.");
    }
    return pom.replaceFirst(
        "</project>",
        "  <build>\n    <plugins>\n" + plugin + "    </plugins>\n  </build>\n</project>");
  }

  private static String addDependency(String pom, String dependency) {
    if (pom.contains("</dependencies>"))
      return pom.replaceFirst("</dependencies>", dependency + "  </dependencies>");
    if (!pom.contains("</project>")) {
      throw new SentinelException("Cannot safely update pom.xml: missing </project> element.");
    }
    return pom.replaceFirst(
        "</project>", "  <dependencies>\n" + dependency + "  </dependencies>\n</project>");
  }
}

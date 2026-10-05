package dev.sentinel.application;

import dev.sentinel.domain.config.SentinelException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Adds only missing, pinned Maven tool declarations required by selected init gates. */
public final class PomToolConfigurator {
    private static final String CHECKSTYLE = "maven-checkstyle-plugin";
    private static final String SPOTBUGS = "spotbugs-maven-plugin";
    private static final String SONAR = "sonar-maven-plugin";
    private static final String ARCHUNIT = "archunit-junit5";

    private static final String CHECKSTYLE_PLUGIN = """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.6.0</version>
              </plugin>
            """;
    private static final String SPOTBUGS_PLUGIN = """
              <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>4.9.7.0</version>
              </plugin>
            """;
    private static final String SONAR_PLUGIN = """
              <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>5.1.0.4751</version>
              </plugin>
            """;
    private static final String ARCHUNIT_DEPENDENCY = """
              <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>1.3.0</version>
                <scope>test</scope>
              </dependency>
            """;

    public record PomChange(Path file, String original, List<String> tools) {
        public PomChange {
            tools = List.copyOf(tools);
        }

        public boolean changed() {
            return !tools.isEmpty();
        }
    }

    public PomChange configure(Path projectRoot, List<InitSetupCatalog.GateOption> gates) {
        Path pom = projectRoot.resolve(ProjectDetector.POM);
        try {
            String original = Files.readString(pom);
            String updated = original;
            List<String> tools = new ArrayList<>();
            boolean needsArchUnit = gates.stream().anyMatch(gate ->
                    gate.id().equals("architecture") || gate.id().equals("archunit"));
            if (gates.stream().anyMatch(gate -> gate.id().equals("checkstyle")) && !containsArtifact(updated, CHECKSTYLE)) {
                updated = addPlugin(updated, CHECKSTYLE_PLUGIN);
                tools.add(CHECKSTYLE);
            }
            if (gates.stream().anyMatch(gate -> gate.id().equals("spotbugs")) && !containsArtifact(updated, SPOTBUGS)) {
                updated = addPlugin(updated, SPOTBUGS_PLUGIN);
                tools.add(SPOTBUGS);
            }
            if (gates.stream().anyMatch(gate -> gate.id().equals("sonar")) && !containsArtifact(updated, SONAR)) {
                updated = addPlugin(updated, SONAR_PLUGIN);
                tools.add(SONAR);
            }
            if (needsArchUnit && !containsArtifact(updated, ARCHUNIT)) {
                updated = addDependency(updated, ARCHUNIT_DEPENDENCY);
                tools.add(ARCHUNIT);
            }
            if (!tools.isEmpty()) {
                Files.writeString(pom, updated, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
            return new PomChange(pom, original, tools);
        } catch (IOException e) {
            throw new SentinelException("Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
        }
    }

    public void rollback(PomChange change) {
        if (change == null || !change.changed()) return;
        try {
            Files.writeString(change.file(), change.original(), StandardOpenOption.TRUNCATE_EXISTING,
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
        return pom.replaceFirst("</project>", "  <build>\n    <plugins>\n" + plugin
                + "    </plugins>\n  </build>\n</project>");
    }

    private static String addDependency(String pom, String dependency) {
        if (pom.contains("</dependencies>")) return pom.replaceFirst("</dependencies>", dependency + "  </dependencies>");
        if (!pom.contains("</project>")) {
            throw new SentinelException("Cannot safely update pom.xml: missing </project> element.");
        }
        return pom.replaceFirst("</project>", "  <dependencies>\n" + dependency + "  </dependencies>\n</project>");
    }
}

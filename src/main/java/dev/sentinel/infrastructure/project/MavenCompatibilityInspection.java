package dev.sentinel.infrastructure.project;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.CompatibilityInspection;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads sanitized Java, Maven, compiler, framework, and managed-plugin facts from a Maven project.
 */
public final class MavenCompatibilityInspection implements CompatibilityInspection {
  private static final Pattern MAVEN_VERSION = Pattern.compile("Apache Maven\\s+([0-9][^\\s]*)");
  private final CommandExecutor commands;

  @Inject
  public MavenCompatibilityInspection(final CommandExecutor commands) {
    this.commands = commands;
  }

  @Override
  public CompatibilityProjectSnapshot inspect(final Project project) {
    final Path pom = project.root().resolve(FileSystemProjectInspection.POM);
    try {
      final Document document = secureFactory().newDocumentBuilder().parse(pom.toFile());
      final Element root = document.getDocumentElement();
      final Map<String, String> properties = properties(root);
      final Map<String, String> plugins = plugins(root);
      final Optional<Integer> release = compilerRelease(root, properties);
      final Optional<String> maven = mavenVersion(project);
      return new CompatibilityProjectSnapshot(
          project,
          new ToolchainSnapshot(
              release.orElse(Runtime.version().feature()), maven, release, project.framework()),
          plugins);
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new SentinelException(
          "Could not inspect Maven compatibility metadata: " + e.getMessage(), e);
    }
  }

  private Optional<String> mavenVersion(final Project project) {
    final String executable = project.mavenWrapperAvailable() ? "./mvnw" : "mvn";
    final CommandResult result = commands.execute(List.of(executable, "--version"), project.root());
    if (!result.succeeded()) {
      return Optional.empty();
    }
    final Matcher matcher = MAVEN_VERSION.matcher(result.stdout());
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static Map<String, String> properties(final Element root) {
    final Map<String, String> values = new LinkedHashMap<>();
    final NodeList properties = root.getElementsByTagName("properties");
    if (properties.getLength() == 0) {
      return values;
    }
    final NodeList children = properties.item(0).getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      final Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        values.put(child.getNodeName(), child.getTextContent().trim());
      }
    }
    return values;
  }

  private static Optional<Integer> compilerRelease(
      final Element root, final Map<String, String> properties) {
    for (final String name :
        new String[] {"maven.compiler.release", "maven.compiler.source", "java.version"}) {
      final String value = directText(root, name).orElse(properties.get(name));
      final Integer parsed = parseJavaVersion(value);
      if (parsed != null) {
        return Optional.of(parsed);
      }
    }
    return Optional.empty();
  }

  private static Map<String, String> plugins(final Element root) {
    final Map<String, String> versions = new LinkedHashMap<>();
    final NodeList pluginNodes = root.getElementsByTagName("plugin");
    for (int index = 0; index < pluginNodes.getLength(); index++) {
      final Node node = pluginNodes.item(index);
      final String artifact = childText(node, "artifactId").orElse("");
      final String version = childText(node, "version").orElse("");
      if (!artifact.isBlank() && !version.isBlank() && !version.startsWith("${")) {
        versions.put(artifact, version);
      }
    }
    return versions;
  }

  private static Optional<String> directText(final Element root, final String name) {
    final NodeList nodes = root.getElementsByTagName(name);
    return nodes.getLength() == 0
        ? Optional.empty()
        : Optional.of(nodes.item(0).getTextContent().trim());
  }

  private static Optional<String> childText(final Node parent, final String name) {
    final NodeList children = parent.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      final Node child = children.item(index);
      if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
        return Optional.of(child.getTextContent().trim());
      }
    }
    return Optional.empty();
  }

  private static Integer parseJavaVersion(final String value) {
    if (value == null || value.isBlank() || value.startsWith("${")) {
      return null;
    }
    final String normalized = value.startsWith("1.") ? value.substring(2) : value;
    try {
      return Integer.parseInt(normalized.replaceAll("[^0-9].*", ""));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }
}

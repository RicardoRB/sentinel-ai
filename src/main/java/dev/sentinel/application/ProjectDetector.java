package dev.sentinel.application;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Detects a Maven/Java project by looking for {@code pom.xml} in the given directory or
 * the nearest parent. Only Maven + Java is supported for now.
 */
public class ProjectDetector {

    public static final String POM = "pom.xml";
    private static final String SPRING_BOOT_GROUP_ID = "org.springframework.boot";

    public Optional<Project> detect(Path start) {
        for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(POM))) {
                return Optional.of(new Project(dir, Language.JAVA, BuildTool.MAVEN,
                        detectFramework(dir.resolve(POM))));
            }
        }
        return Optional.empty();
    }

    /** Spring Boot is detected when the pom references any {@code org.springframework.boot} groupId. */
    private Framework detectFramework(Path pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null); // default handler prints "[Fatal Error]" noise to stderr
            Document document = builder.parse(pom.toFile());
            NodeList groupIds = document.getElementsByTagName("groupId");
            for (int i = 0; i < groupIds.getLength(); i++) {
                if (SPRING_BOOT_GROUP_ID.equals(groupIds.item(i).getTextContent().trim())) {
                    return Framework.SPRING_BOOT;
                }
            }
        } catch (Exception e) {
            // An unreadable pom is still a Maven project; we just cannot identify the framework.
        }
        return Framework.NONE;
    }
}

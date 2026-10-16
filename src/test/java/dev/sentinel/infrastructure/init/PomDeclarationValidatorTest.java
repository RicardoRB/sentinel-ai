package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.init.PomDeclarationValidator.Declaration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PomDeclarationValidatorTest {
  private static final Declaration PIT = new Declaration("pitest-maven", false);
  private static final Declaration ARCHUNIT = new Declaration("archunit-junit5", true);

  @Test
  void acceptsValidProjectLevelDeclarations() {
    final String pom =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <build>
            <plugins>
              <plugin>
                <artifactId>pitest-maven</artifactId>
                <version>1.17.4</version>
                <dependencies><dependency><artifactId>x</artifactId></dependency></dependencies>
                <configuration><mutationThreshold>60</mutationThreshold></configuration>
              </plugin>
            </plugins>
          </build>
          <dependencies>
            <dependency><artifactId>archunit-junit5</artifactId><scope>test</scope></dependency>
          </dependencies>
        </project>
        """;

    assertThatCode(() -> PomDeclarationValidator.validate(pom, List.of(PIT, ARCHUNIT)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsElementMavenDoesNotAllowInsidePlugin() {
    final String pom =
        "<project><build><plugins><plugin><artifactId>pitest-maven</artifactId>"
            + "<dependencies/><mutationThreshold>60</mutationThreshold>"
            + "</plugin></plugins></build></project>";

    assertThatThrownBy(() -> PomDeclarationValidator.validate(pom, List.of(PIT)))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("pitest-maven")
        .hasMessageContaining("<mutationThreshold>")
        .hasMessageContaining("inside <plugin>");
  }

  @Test
  void rejectsElementMavenDoesNotAllowInsideDependency() {
    final String pom =
        "<project><dependencies><dependency><artifactId>archunit-junit5</artifactId>"
            + "<configuration/></dependency></dependencies></project>";

    assertThatThrownBy(() -> PomDeclarationValidator.validate(pom, List.of(ARCHUNIT)))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("archunit-junit5")
        .hasMessageContaining("inside <dependency>");
  }

  @Test
  void rejectsMalformedXmlWithPosition() {
    final String pom = "<project>\n<build><plugins></build>\n</project>";

    assertThatThrownBy(() -> PomDeclarationValidator.validate(pom, List.of()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("not well-formed XML (line 2");
  }

  @Test
  void rejectsDoctypeBeforeResolvingExternalEntities() {
    final String pom =
        "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE project [<!ENTITY leak SYSTEM \"http://192.0.2.1/secret\">]>\n"
            + "<project><name>&leak;</name></project>";

    assertThatThrownBy(() -> PomDeclarationValidator.validate(pom, List.of()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("DOCTYPE declarations are not supported");
  }

  @Test
  void rejectsAddedDeclarationOutsideProjectLevelSection() {
    final String pom =
        "<project><profiles><profile><build><plugins><plugin>"
            + "<artifactId>pitest-maven</artifactId></plugin></plugins></build></profile>"
            + "</profiles><build><plugins/></build></project>";

    assertThatThrownBy(() -> PomDeclarationValidator.validate(pom, List.of(PIT)))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining(
            "pitest-maven declaration is not in the project-level <build/plugins>");
  }
}

package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.init.PomElements.Element;
import java.util.List;
import org.junit.jupiter.api.Test;

class PomElementsTest {
  @Test
  void listsOnlyDirectChildrenOfNestedElements() {
    final String pom =
        "<project><build><plugins><plugin><artifactId>a</artifactId>"
            + "<dependencies><dependency><artifactId>nested</artifactId></dependency>"
            + "</dependencies></plugin></plugins></build></project>";
    final Element plugin = plugin(pom);

    assertThat(PomElements.children(pom, plugin))
        .extracting(Element::name)
        .containsExactly("artifactId", "dependencies");
  }

  @Test
  void listsSelfClosingChildrenWithEmptyText() {
    final String pom =
        "<project><build><plugins><plugin><extensions/><artifactId> a </artifactId>"
            + "<inherited /></plugin></plugins></build></project>";
    final List<Element> children = PomElements.children(pom, plugin(pom));

    assertThat(children)
        .extracting(Element::name)
        .containsExactly("extensions", "artifactId", "inherited");
    assertThat(children).extracting(Element::selfClosing).containsExactly(true, false, true);
    assertThat(PomElements.text(pom, children.get(0))).isEmpty();
    assertThat(PomElements.text(pom, children.get(1))).isEqualTo("a");
  }

  @Test
  void ignoresCommentsBetweenChildren() {
    final String pom =
        "<project><build><plugins><plugin><!-- <version>0</version> -->"
            + "<artifactId>a</artifactId><!-- </plugin> --><version>1</version>"
            + "</plugin></plugins></build></project>";
    final List<Element> children = PomElements.children(pom, plugin(pom));

    assertThat(children).extracting(Element::name).containsExactly("artifactId", "version");
    assertThat(PomElements.text(pom, children.get(1))).isEqualTo("1");
  }

  @Test
  void rejectsMismatchedClosingTag() {
    final String pom = "<project><build></plugins></build></project>";

    assertThatThrownBy(() -> PomElements.project(pom))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("malformed");
  }

  private static Element plugin(final String pom) {
    final Element build = PomElements.child(pom, PomElements.project(pom), "build").orElseThrow();
    final Element plugins = PomElements.child(pom, build, "plugins").orElseThrow();
    return PomElements.child(pom, plugins, "plugin").orElseThrow();
  }
}

package dev.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonTreeTest {

  @Test
  void navigatesObjectsArraysAndScalars() {
    JsonTree root = JsonTree.parse("{\"a\":[{\"n\":2,\"ok\":true,\"s\":\"x\"}]}");

    assertThat(root.get("a").size()).isEqualTo(1);
    assertThat(root.get("a").get(0).get("n").asInt()).isEqualTo(2);
    assertThat(root.get("a").get(0).get("ok").asBoolean()).isTrue();
    assertThat(root.get("a").get(0).get("s").asString()).isEqualTo("x");
    assertThat(root.has("missing")).isFalse();
  }

  @Test
  void rejectsTrailingTextAndMissingFields() {
    assertThatThrownBy(() -> JsonTree.parse("{\"a\":1}\nnoise")).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> JsonTree.parse("{\"a\":1}").get("b"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void equalityIgnoresObjectFieldOrder() {
    assertThat(JsonTree.parse("{\"a\":1,\"b\":2}")).isEqualTo(JsonTree.parse("{\"b\":2,\"a\":1}"));
  }
}

package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

class MultiSelectMenuTest {
  private final MultiSelectMenu menu = new MultiSelectMenu();
  private final StringWriter rendered = new StringWriter();

  @Test
  void togglesItemsAndConfirmsSelection() {
    assertThat(
            menu.select(
                "Choose",
                List.of("first", "second"),
                script(' ', 27, '[', 'B', ' ', '\n'),
                output()))
        .containsExactly(0, 1);
    assertThat(rendered.toString()).contains("[x] 1) first", "[x] 2) second");
  }

  @Test
  void cursorWrapsFromFirstToLast() {
    assertThat(
            menu.select(
                "Choose", List.of("first", "last"), script(27, '[', 'A', ' ', '\n'), output()))
        .containsExactly(1);
  }

  @Test
  void cancelsAndReportsEof() {
    assertThatThrownBy(() -> menu.select("Choose", List.of("one"), () -> 'q', output()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("cancelled by the user");
    assertThatThrownBy(() -> menu.select("Choose", List.of("one"), () -> -1, output()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("input ended");
  }

  @Test
  void rejectsEmptySelectionWithActionableMessage() {
    final AtomicInteger index = new AtomicInteger();
    assertThatThrownBy(
            () ->
                menu.select(
                    "Choose",
                    List.of("one"),
                    "Select at least one integration, or choose none.",
                    () -> index.getAndIncrement() == 0 ? '\n' : -1,
                    output()))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("Select at least one integration");
  }

  private PrintWriter output() {
    return new PrintWriter(rendered, true);
  }

  private static IntSupplier script(int... keys) {
    final AtomicInteger index = new AtomicInteger();
    return () -> index.get() < keys.length ? keys[index.getAndIncrement()] : -1;
  }
}

package dev.sentinel.domain;

import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.SentinelException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandLineTokenizerTest {

    @Test
    void splitsOnWhitespace() {
        assertThat(CommandLineTokenizer.tokenize("  ./mvnw   test -q ")).containsExactly("./mvnw", "test", "-q");
    }

    @Test
    void keepsQuotedArgumentsTogether() {
        assertThat(CommandLineTokenizer.tokenize("./mvnw test \"-Dtest=A, B\" 'x y'"))
                .containsExactly("./mvnw", "test", "-Dtest=A, B", "x y");
    }

    @Test
    void doesNotInterpretShellSyntax() {
        assertThat(CommandLineTokenizer.tokenize("echo $HOME && rm -rf * | cat"))
                .containsExactly("echo", "$HOME", "&&", "rm", "-rf", "*", "|", "cat");
    }

    @Test
    void emptyQuotedArgumentIsKept() {
        assertThat(CommandLineTokenizer.tokenize("cmd \"\"")).containsExactly("cmd", "");
    }

    @Test
    void unterminatedQuoteIsRejected() {
        assertThatThrownBy(() -> CommandLineTokenizer.tokenize("cmd \"oops")).isInstanceOf(SentinelException.class);
    }
}

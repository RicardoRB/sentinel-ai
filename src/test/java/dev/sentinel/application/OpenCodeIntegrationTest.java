package dev.sentinel.application;

import dev.sentinel.domain.agent.IntegrationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeIntegrationTest {
    @Test
    void integratesAndRemovesOnlyOwnedContent() throws Exception {
        Path root = Files.createTempDirectory("sentinel-opencode");
        OpenCodeIntegration integration = new OpenCodeIntegration();
        IntegrationResult created = integration.integrate(root, false);
        assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
        assertThat(Files.exists(root.resolve(".opencode/commands/sentinel-check.md"))).isTrue();
        assertThat(integration.integrate(root, false).status()).isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
        assertThat(integration.integrate(root, true).status()).isEqualTo(IntegrationResult.Status.REMOVED);
    }

    @Test
    void refusesToOverwriteUnownedCommand() throws Exception {
        Path target = Files.createTempDirectory("sentinel-opencode")
                .resolve(".opencode/commands/sentinel-check.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "user-owned");
        assertThat(new OpenCodeIntegration().integrate(target.getParent().getParent().getParent(), false).status())
                .isEqualTo(IntegrationResult.Status.CONFLICT);
    }
}

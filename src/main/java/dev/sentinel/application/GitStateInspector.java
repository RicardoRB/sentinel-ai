package dev.sentinel.application;

import dev.sentinel.domain.process.CommandExecutor;

import java.nio.file.Path;
import java.util.List;

public final class GitStateInspector {
    private final CommandExecutor executor;
    public GitStateInspector(CommandExecutor executor) { this.executor = executor; }

    public GitState inspect(Path root) {
        var repository = executor.execute(List.of("git", "rev-parse", "--is-inside-work-tree"), root);
        if (!repository.succeeded()) return new GitState(false, "", false, "Not a Git repository.");
        var branch = executor.execute(List.of("git", "branch", "--show-current"), root);
        var status = executor.execute(List.of("git", "status", "--porcelain"), root);
        return new GitState(true, branch.stdout().trim(), !status.stdout().isBlank(),
                status.stdout().isBlank() ? "Working tree clean." : "Working tree has uncommitted changes.");
    }
}

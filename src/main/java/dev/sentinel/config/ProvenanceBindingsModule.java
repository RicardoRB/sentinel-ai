package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.provenance.ArchiveReplay;
import dev.sentinel.domain.provenance.GitRepository;
import dev.sentinel.infrastructure.provenance.GitRepositoryAdapter;
import dev.sentinel.infrastructure.provenance.OpenSpecArchiveReplayAdapter;

@Module
interface ProvenanceBindingsModule {
  @Binds
  GitRepository gitRepository(GitRepositoryAdapter implementation);

  @Binds
  ArchiveReplay archiveReplay(OpenSpecArchiveReplayAdapter implementation);
}

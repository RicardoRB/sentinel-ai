package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.provenance.ArchiveReplay;
import dev.ward.domain.provenance.GitRepository;
import dev.ward.infrastructure.provenance.GitRepositoryAdapter;
import dev.ward.infrastructure.provenance.OpenSpecArchiveReplayAdapter;

@Module
interface ProvenanceBindingsModule {
  @Binds
  GitRepository gitRepository(GitRepositoryAdapter implementation);

  @Binds
  ArchiveReplay archiveReplay(OpenSpecArchiveReplayAdapter implementation);
}

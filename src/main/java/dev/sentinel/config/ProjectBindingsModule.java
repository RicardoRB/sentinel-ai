package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.CompatibilityInspection;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningStore;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.project.ProjectInspection;
import dev.sentinel.domain.spec.ProjectPaths;
import dev.sentinel.infrastructure.agent.ProcessAgentRunnerFactory;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import dev.sentinel.infrastructure.learning.JsonLearningStore;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.project.MavenCompatibilityInspection;
import dev.sentinel.infrastructure.spec.FileSystemProjectPaths;

@Module
interface ProjectBindingsModule {
  @Binds
  SentinelConfigurationReader configurationReader(TomlConfigurationReader implementation);

  @Binds
  ProjectInspection projectInspection(FileSystemProjectInspection implementation);

  @Binds
  CompatibilityInspection compatibilityInspection(MavenCompatibilityInspection implementation);

  @Binds
  EnvironmentInspection environmentInspection(SystemEnvironmentInspection implementation);

  @Binds
  GitStateInspection gitStateInspection(GitStateInspector implementation);

  @Binds
  AgentRunnerFactory agentRunnerFactory(ProcessAgentRunnerFactory implementation);

  @Binds
  LearningStore learningStore(JsonLearningStore implementation);

  @Binds
  JsonCodec jsonCodec(ForyJsonCodec implementation);

  @Binds
  ProjectPaths projectPaths(FileSystemProjectPaths implementation);
}

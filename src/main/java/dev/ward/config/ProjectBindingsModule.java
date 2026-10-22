package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.agent.AgentRunnerFactory;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.learning.LearningStore;
import dev.ward.domain.loop.GitStateInspection;
import dev.ward.domain.project.ProjectInspection;
import dev.ward.domain.spec.ProjectPaths;
import dev.ward.infrastructure.agent.ProcessAgentRunnerFactory;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.doctor.SystemEnvironmentInspection;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.learning.JsonLearningStore;
import dev.ward.infrastructure.loop.GitStateInspector;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.MavenCompatibilityInspection;
import dev.ward.infrastructure.spec.FileSystemProjectPaths;

@Module
interface ProjectBindingsModule {
  @Binds
  WardConfigurationReader configurationReader(TomlConfigurationReader implementation);

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

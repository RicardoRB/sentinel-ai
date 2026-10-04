package dev.sentinel.cli;

import com.google.inject.Injector;
import picocli.CommandLine;

/** Lets Picocli obtain commands (and their collaborators) from the Guice injector. */
public class GuiceCommandFactory implements CommandLine.IFactory {

    private final Injector injector;

    public GuiceCommandFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public <K> K create(Class<K> type) throws Exception {
        try {
            return injector.getInstance(type);
        } catch (com.google.inject.ConfigurationException e) {
            return CommandLine.defaultFactory().create(type);
        }
    }
}

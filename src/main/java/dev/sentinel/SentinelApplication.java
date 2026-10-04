package dev.sentinel;

import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.sentinel.cli.CommandLineRunnerImpl;
import dev.sentinel.config.SentinelModule;

public class SentinelApplication {

    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new SentinelModule());
        System.exit(injector.getInstance(CommandLineRunnerImpl.class).run(args));
    }
}

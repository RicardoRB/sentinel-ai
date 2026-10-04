package dev.sentinel.cli;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

    private final String version;

    @Inject
    public VersionProvider(@Named("sentinel.version") String version) {
        this.version = version;
    }

    @Override
    public String[] getVersion() {
        return new String[]{"sentinel " + version};
    }
}

package dev.sentinel.domain.init;

import java.util.List;

/** A user-selectable quality gate and the command/environment facts presented by init. */
public record InitGateOption(String id, String description, List<String> command,
                             boolean available, String availabilityMessage) {
    public InitGateOption {
        command = List.copyOf(command);
    }

    public InitGateOption(String id, List<String> command, boolean available, String availabilityMessage) {
        this(id, id, command, available, availabilityMessage);
    }
}

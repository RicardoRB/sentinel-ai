package dev.sentinel;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** Option names a Picocli command accepts, read from its annotations and mixins. */
final class CliOptionNames {
  private static final List<String> STANDARD_HELP = List.of("-h", "--help", "-V", "--version");

  private CliOptionNames() {}

  static Set<String> of(final Class<?>... commands) {
    final Set<String> names = new TreeSet<>();
    for (final Class<?> command : commands) {
      names.addAll(declared(command));
    }
    return Set.copyOf(names);
  }

  private static Set<String> declared(final Class<?> type) {
    final Set<String> names = new TreeSet<>();
    final Command command = type.getAnnotation(Command.class);
    if (command != null && command.mixinStandardHelpOptions()) {
      names.addAll(STANDARD_HELP);
    }
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      for (final Field field : current.getDeclaredFields()) {
        names.addAll(fieldOptions(field));
      }
    }
    return names;
  }

  private static Set<String> fieldOptions(final Field field) {
    final Option option = field.getAnnotation(Option.class);
    if (option != null) {
      return Set.of(option.names());
    }
    return field.isAnnotationPresent(Mixin.class) ? declared(field.getType()) : Set.of();
  }
}

package dev.sentinel.infrastructure.cli.init;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Binds {@link TerminalLibc} methods to libc downcalls through a JDK dynamic proxy. */
final class DowncallBinder {
  private DowncallBinder() {}

  static TerminalLibc bind() {
    final Linker linker = Linker.nativeLinker();
    final Map<String, MethodHandle> functions = new ConcurrentHashMap<>();
    for (final Method method : TerminalLibc.class.getDeclaredMethods()) {
      if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
        functions.put(
            method.getName(),
            linker.downcallHandle(
                linker.defaultLookup().find(symbol(method.getName())).orElseThrow(),
                descriptor(method)));
      }
    }
    return (TerminalLibc)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {TerminalLibc.class},
            (instance, method, arguments) ->
                functions.get(method.getName()).invokeWithArguments(arguments));
  }

  private static String symbol(final String methodName) {
    return methodName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static FunctionDescriptor descriptor(final Method method) {
    final MemoryLayout[] arguments =
        Stream.of(method.getParameterTypes())
            .map(DowncallBinder::layout)
            .toArray(MemoryLayout[]::new);
    return FunctionDescriptor.of(layout(method.getReturnType()), arguments);
  }

  private static MemoryLayout layout(final Class<?> type) {
    if (type == int.class) {
      return ValueLayout.JAVA_INT;
    }
    return type == long.class ? ValueLayout.JAVA_LONG : ValueLayout.ADDRESS;
  }
}

package dev.sentinel.domain.architecturefixture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;

/** Fixture that the native-image rules must reject: it builds a class at runtime. */
public final class NativeImageIncompatibleAccess {
  /** Builds a proxy class at runtime, which native images cannot contain. */
  public Runnable spinProxy(final MethodHandle handle) {
    return MethodHandleProxies.asInterfaceInstance(Runnable.class, handle);
  }
}

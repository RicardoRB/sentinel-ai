package dev.sentinel.domain.architecturefixture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;

public final class NativeImageIncompatibleAccess {
  public Runnable spinProxy(final MethodHandle handle) {
    return MethodHandleProxies.asInterfaceInstance(Runnable.class, handle);
  }
}

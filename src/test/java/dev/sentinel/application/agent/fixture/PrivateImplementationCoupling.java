package dev.sentinel.application.agent.fixture;

import dev.sentinel.application.gate.internal.HiddenImplementation;

/**
 * Architecture fixture that the boundary rules must reject: it reaches into another feature's
 * internal package.
 */
public final class PrivateImplementationCoupling {
  public HiddenImplementation hidden;
}

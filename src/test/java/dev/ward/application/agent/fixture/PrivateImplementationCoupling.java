package dev.ward.application.agent.fixture;

import dev.ward.application.gate.internal.HiddenImplementation;

/**
 * Architecture fixture that the boundary rules must reject: it reaches into another feature's
 * internal package.
 */
public final class PrivateImplementationCoupling {
  public HiddenImplementation hidden;
}

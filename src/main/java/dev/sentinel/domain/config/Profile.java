package dev.sentinel.domain.config;

import java.util.List;

public record Profile(String name, List<String> gates) {
  public Profile {
    gates = List.copyOf(gates);
  }
}

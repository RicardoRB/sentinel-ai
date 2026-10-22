#!/bin/sh
# Builds the small Maven project recorded by ward-demo.tape and warms the Maven caches.
# Usage: ward-demo-setup.sh <empty-directory>   (needs `ward` on PATH and network once)
set -eu

repo="$(cd "$(dirname "$0")/../.." && pwd)"
demo="$1"
mkdir -p "$demo/src/main/java/com/acme" "$demo/src/test/java/com/acme"
cd "$demo"
cp "$repo/mvnw" .
mkdir -p .mvn/wrapper
cp "$repo/.mvn/wrapper/maven-wrapper.properties" .mvn/wrapper/

cat > pom.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.acme</groupId>
  <artifactId>orders</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.13.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.4</version>
      </plugin>
    </plugins>
  </build>
</project>
EOF

cat > src/main/java/com/acme/OrderService.java <<'EOF'
package com.acme;

/** Computes order expiry times. */
public final class OrderService {

  private static final long SECONDS_PER_HOUR = 3600;

  /**
   * Returns the expiry time of an order created at the given second.
   *
   * @param createdAt creation time in epoch seconds
   * @return expiry time in epoch seconds
   */
  public long expiresAt(final long createdAt) {
    return createdAt + SECONDS_PER_HOUR;
  }
}
EOF

cat > src/test/java/com/acme/OrderServiceTest.java <<'EOF'
package com.acme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderServiceTest {

  @Test
  void expiresOneHourAfterCreation() {
    assertEquals(3600L, new OrderService().expiresAt(0L));
  }
}
EOF

printf 'target/\n' > .gitignore
git init -q
ward init --preset standard --integration none --yes > /dev/null
# Download every plugin before recording so the demo runs at real speed.
ward check --profile pre-push > /dev/null

# The change an agent might write: a hardcoded value next to an unused constant.
sed -i.bak 's/createdAt + SECONDS_PER_HOUR/createdAt + 3600/' src/main/java/com/acme/OrderService.java
rm src/main/java/com/acme/OrderService.java.bak
git add -A
git -c user.name=demo -c user.email=demo@example.com commit -qm "Agent change"

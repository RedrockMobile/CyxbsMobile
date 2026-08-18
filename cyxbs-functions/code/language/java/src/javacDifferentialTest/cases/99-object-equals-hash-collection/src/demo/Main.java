package demo;

import java.util.HashMap;
import java.util.Map;

class Key {
  private final int value;

  Key(int value) {
    this.value = value;
  }

  @Override public boolean equals(Object other) {
    return this == other;
  }

  @Override public int hashCode() {
    return value * 31;
  }

  @Override public String toString() {
    return "Key(" + value + ")";
  }
}

public class Main {
  public static void main() {
    Map<Key, String> values = new HashMap<>();
    Key lookup = new Key(3);
    values.put(lookup, "found");
    System.out.println(values.get(lookup) + ":" + lookup.toString());
  }
}

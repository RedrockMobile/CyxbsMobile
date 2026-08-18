package demo;

import java.util.HashSet;
import java.util.Set;

public class Main {
  public static void main() {
    Set<String> values = new HashSet<>();
    System.out.println(values.add("x"));
    System.out.println(values.add("x"));
    values.add(null);
    System.out.println(values.size() + ":" + values.contains(null));
  }
}

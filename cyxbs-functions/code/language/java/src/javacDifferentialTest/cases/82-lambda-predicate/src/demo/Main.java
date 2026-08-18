package demo;

import java.util.function.Predicate;

public class Main {
  static boolean test(Predicate<String> predicate, String value) {
    return predicate.test(value);
  }

  public static void main() {
    System.out.println(test(value -> value.length() > 3, "java"));
    System.out.println(test(value -> value.startsWith("k"), "java"));
  }
}

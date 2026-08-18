package demo;

import java.util.function.Function;

public class Main {
  static <T, R> R map(T value, Function<T, R> function) {
    return function.apply(value);
  }

  public static void main() {
    Integer length = map("language", value -> value.length());
    String text = map(Integer.valueOf(8), value -> "n=" + value);
    System.out.println(length + ":" + text);
  }
}

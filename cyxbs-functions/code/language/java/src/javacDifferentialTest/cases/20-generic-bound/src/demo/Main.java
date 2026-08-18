package demo;

public class Main {
  static <T extends Number> int twice(T value) { return value.intValue() * 2; }

  public static void main() {
    System.out.println(twice(Integer.valueOf(6)));
  }
}

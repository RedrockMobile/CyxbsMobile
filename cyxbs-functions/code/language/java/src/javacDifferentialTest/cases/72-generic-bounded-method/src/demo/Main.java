package demo;

public class Main {
  static <T extends Number> int integerValue(T value) {
    return value.intValue();
  }

  public static void main() {
    System.out.println(integerValue(Integer.valueOf(12)) + integerValue(Long.valueOf(30L)));
  }
}

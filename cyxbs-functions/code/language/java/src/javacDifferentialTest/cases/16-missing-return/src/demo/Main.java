package demo;

public class Main {
  static int value(boolean condition) {
    if (condition) {
      return 1;
    }
  }

  public static void main() {
    System.out.println(value(true));
  }
}

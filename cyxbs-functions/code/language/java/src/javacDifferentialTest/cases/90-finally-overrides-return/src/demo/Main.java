package demo;

public class Main {
  static int value() {
    try {
      return 1;
    } finally {
      return 2;
    }
  }

  public static void main() {
    System.out.println(value());
  }
}

package demo;

public class Main {
  static int parse(int value) {
    try {
      if (value == 0) throw new IllegalArgumentException("zero");
      return 10 / value;
    } catch (IllegalArgumentException exception) {
      return -1;
    } finally {
      System.out.print("done:");
    }
  }

  public static void main() {
    System.out.println(parse(0));
  }
}

package demo;

public class Main {
  static String pick(long value) { return "long"; }
  static String pick(Integer value) { return "boxed"; }

  public static void main() {
    System.out.println(pick(1));
  }
}

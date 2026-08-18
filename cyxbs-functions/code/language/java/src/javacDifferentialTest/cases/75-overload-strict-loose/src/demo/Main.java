package demo;

public class Main {
  static String choose(long value) { return "long"; }
  static String choose(Integer value) { return "boxed"; }

  public static void main() {
    System.out.println(choose(1));
    System.out.println(choose(Integer.valueOf(1)));
  }
}

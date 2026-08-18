package demo;

public class Main {
  static String choose(int first, int second) { return "fixed"; }
  static String choose(int... values) { return "varargs-" + values.length; }

  public static void main() {
    System.out.println(choose(1, 2));
    System.out.println(choose(1, 2, 3));
  }
}

package demo;

public class Main {
  static String choose(Object value) { return "object"; }
  static String choose(String value) { return "string"; }

  public static void main() {
    System.out.println(choose(null));
  }
}

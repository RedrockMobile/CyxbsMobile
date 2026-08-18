package demo;

public class Main {
  static String pick(Object value) { return "object"; }
  static String pick(String value) { return "string"; }

  public static void main() {
    String value = null;
    System.out.println(pick(value));
  }
}

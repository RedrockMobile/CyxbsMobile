package demo;

public class Main {
  public static void main() {
    String value = "JavaScript";
    System.out.println(value.length());
    System.out.println(value.substring(4, 10));
    System.out.println(value.indexOf("Script"));
    System.out.println(value.startsWith("Java") + ":" + value.endsWith("ipt"));
  }
}

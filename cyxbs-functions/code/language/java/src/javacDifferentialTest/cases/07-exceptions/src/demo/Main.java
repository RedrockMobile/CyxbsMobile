package demo;

public class Main {
  public static void main() {
    try {
      int value = 1 / 0;
      System.out.println(value);
    } catch (ArithmeticException exception) {
      System.out.println("caught");
    } finally {
      System.out.println("finally");
    }
  }
}

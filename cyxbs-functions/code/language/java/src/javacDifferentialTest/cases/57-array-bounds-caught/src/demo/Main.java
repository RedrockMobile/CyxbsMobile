package demo;

public class Main {
  public static void main() {
    try {
      int[] values = {1};
      System.out.println(values[2]);
    } catch (ArrayIndexOutOfBoundsException exception) {
      System.out.println("bounds");
    }
  }
}

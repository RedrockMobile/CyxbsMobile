package demo;

public class Main {
  static int next(int value) {
    System.out.print(value);
    return value;
  }

  public static void main() {
    int[][] values = new int[next(2)][next(3)];
    System.out.println(":" + values.length + ":" + values[0].length);
  }
}

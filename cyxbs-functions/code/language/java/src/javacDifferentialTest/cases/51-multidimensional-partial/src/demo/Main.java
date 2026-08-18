package demo;

public class Main {
  public static void main() {
    int[][] values = new int[2][];
    values[0] = new int[]{1, 2};
    System.out.println(values.length + ":" + values[0].length + ":" + (values[1] == null));
  }
}

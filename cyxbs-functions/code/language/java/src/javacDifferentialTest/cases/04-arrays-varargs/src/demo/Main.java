package demo;

public class Main {
  static int sum(int... values) {
    int result = 0;
    for (int index = 0; index < values.length; index++) {
      result += values[index];
    }
    return result;
  }

  public static void main() {
    int[][] matrix = {{1, 2}, {3, 4}};
    System.out.println(sum(matrix[0][0], matrix[0][1], matrix[1][0], matrix[1][1]));
  }
}

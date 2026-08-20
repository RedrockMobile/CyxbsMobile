package demo;

public class Main {
  private static int dimension;

  public static void case121() {
    int[] values = new int[3];
    System.out.println(values[0] + ":" + values[2]);
  }

  public static void case122() {
    String[] values = new String[2];
    System.out.println((values[0] == null) + ":" + (values[1] == null));
  }

  public static void case123() {
    int[][] values = new int[2][3];
    System.out.println(values.length + ":" + values[0].length + ":" + values[1].length);
  }

  public static void case124() {
    String[][] values = new String[2][];
    values[1] = new String[]{"ready"};
    System.out.println((values[0] == null) + ":" + values[1][0]);
  }

  public static void case125() {
    dimension = 0;
    int[][] values = new int[nextDimension()][nextDimension()];
    System.out.println(values.length + ":" + values[0].length + ":" + dimension);
  }

  public static void case126() {
    int[] first = new int[]{1, 2};
    int[] second = first;
    second[1] = 9;
    System.out.println(first[1]);
  }

  public static void case127() {
    Object[] values = new String[2];
    values[0] = "ok";
    System.out.println(values[0]);
  }

  public static void case128() {
    Object[] values = new String[1];
    try {
      values[0] = Integer.valueOf(1);
    } catch (ArrayStoreException exception) {
      System.out.println("ArrayStoreException");
    }
  }

  public static void case129() {
    int[] values = new int[]{2, 3};
    System.out.println(sum(values) + ":" + sum(4, 5, 6));
  }

  public static void case130() {
    int[][] values = new int[][]{{1, 2}, {3, 4}};
    System.out.println(values[0][1] + values[1][0]);
  }

  private static int nextDimension() {
    dimension++;
    return dimension;
  }

  private static int sum(int... values) {
    int result = 0;
    for (int value : values) {
      result += value;
    }
    return result;
  }
}

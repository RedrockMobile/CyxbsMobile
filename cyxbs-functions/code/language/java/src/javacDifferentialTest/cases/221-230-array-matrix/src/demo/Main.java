package demo;

public class Main {
  private static int nextValue;

  private static int next() {
    nextValue++;
    return nextValue;
  }

  private static int sum(int... values) {
    int result = 0;
    for (int value : values) {
      result += value;
    }
    return result;
  }

  public static void run221() {
    int[] values = new int[3];
    System.out.println(values[0] + ":" + values[2]);
  }

  public static void run222() {
    boolean[] values = new boolean[2];
    System.out.println(values[0] + ":" + values[1]);
  }

  public static void run223() {
    String[] values = new String[2];
    System.out.println((values[0] == null) + ":" + (values[1] == null));
  }

  public static void run224() {
    int[][] values = {{1, 2}, {3, 4}};
    System.out.println(values[0][1] + values[1][0]);
  }

  public static void run225() {
    int[][] values = new int[2][];
    values[1] = new int[]{7, 8};
    System.out.println((values[0] == null) + ":" + values[1][1]);
  }

  public static void run226() {
    int[] first = {1, 2};
    int[] second = first;
    second[0] = 9;
    System.out.println(first[0]);
  }

  public static void run227() {
    nextValue = 0;
    int[] values = {next(), next(), next()};
    System.out.println(values[0] * 100 + values[1] * 10 + values[2]);
  }

  public static void run228() {
    int result = 0;
    for (int value : new int[]{2, 4, 6}) {
      result += value;
    }
    System.out.println(result);
  }

  public static void run229() {
    String[] strings = new String[1];
    Object[] objects = strings;
    objects[0] = "ok";
    System.out.println(strings[0]);
  }

  public static void run230() {
    int[] values = {3, 4, 5};
    System.out.println(sum(values));
  }
}

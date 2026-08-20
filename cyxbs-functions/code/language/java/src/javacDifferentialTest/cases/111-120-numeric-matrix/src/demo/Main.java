package demo;

public class Main {
  public static void case111() {
    int value = 2147483647 + 1;
    System.out.println(value);
  }

  public static void case112() {
    long value = 9223372036854775807L + 1L;
    System.out.println(value);
  }

  public static void case113() {
    System.out.println(-1 >>> 28);
  }

  public static void case114() {
    System.out.println(-1L >>> 60);
  }

  public static void case115() {
    byte value = (byte) 120;
    value += 10;
    System.out.println(value);
  }

  public static void case116() {
    char value = '\uffff';
    value++;
    int result = value;
    System.out.println(result);
  }

  public static void case117() {
    float value = 16777216.0f;
    value += 1.0f;
    System.out.println(value == 16777216.0f);
  }

  public static void case118() {
    double value = 0.0 / 0.0;
    System.out.println((value == value) + ":" + (value != value));
  }

  public static void case119() {
    int left = 7;
    long right = 5L;
    long result = left * right + 3;
    System.out.println(result);
  }

  public static void case120() {
    System.out.println(((int) 1.0e100) + ":" + ((int) -1.0e100));
  }
}

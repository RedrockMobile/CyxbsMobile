package demo;

public class Main {
  public static void run211() {
    int value = 2147483647;
    System.out.println(value + 1);
  }

  public static void run212() {
    int quotient = -7 / 3;
    int remainder = -7 % 3;
    System.out.println(quotient + ":" + remainder);
  }

  public static void run213() {
    int minimum = -2147483647 - 1;
    System.out.println(minimum / -1);
  }

  public static void run214() {
    System.out.println((1 << 32) + ":" + (1 << 33));
  }

  public static void run215() {
    System.out.println((1L << 64) + ":" + (1L << 65));
  }

  public static void run216() {
    System.out.println(-1 >>> 1);
  }

  public static void run217() {
    char value = '\uffff';
    value += 2;
    int widened = value;
    System.out.println(widened);
  }

  public static void run218() {
    byte value = (byte) 127;
    value += 2;
    System.out.println(value);
  }

  public static void run219() {
    char left = '\uffff';
    byte right = (byte) 1;
    System.out.println(left + right);
  }

  public static void run220() {
    double value = 0.0 / 0.0;
    System.out.println((value == value) + ":" + (value < 1.0) + ":" + (value != value));
  }
}

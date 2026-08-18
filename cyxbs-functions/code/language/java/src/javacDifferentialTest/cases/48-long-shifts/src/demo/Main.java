package demo;

public class Main {
  public static void main() {
    long sign = -8L;
    long left = 1L << 40;
    System.out.println((sign >> 2) + ":" + (sign >>> 62) + ":" + left);
  }
}

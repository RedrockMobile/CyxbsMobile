package demo;

public class Main {
  public static void main() {
    int overflow = 2147483647 + 1;
    long shifted = -1L >>> 1;
    byte narrowed = (byte) 130;
    char wrapped = (char) -1;
    System.out.println(overflow + ":" + shifted + ":" + narrowed + ":" + (int) wrapped);
  }
}

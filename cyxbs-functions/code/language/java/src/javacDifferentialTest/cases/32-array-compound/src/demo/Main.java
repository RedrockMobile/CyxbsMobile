package demo;

public class Main {
  public static void main() {
    byte[] bytes = new byte[1];
    char[] chars = new char[1];
    bytes[0] = (byte) 127;
    bytes[0] += 2;
    chars[0]--;
    System.out.println(bytes[0] + ":" + (int) chars[0]);
  }
}

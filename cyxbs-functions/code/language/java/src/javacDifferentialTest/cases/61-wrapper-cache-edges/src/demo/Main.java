package demo;

public class Main {
  public static void main() {
    Integer lowFirst = Integer.valueOf(127);
    Integer lowSecond = Integer.valueOf(127);
    Integer highFirst = Integer.valueOf(128);
    Integer highSecond = Integer.valueOf(128);
    System.out.println((lowFirst == lowSecond) + ":" + (highFirst == highSecond));
  }
}

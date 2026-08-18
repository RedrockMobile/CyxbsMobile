package demo;

public class Main {
  static <T> T choose(T first, T second) { return second; }

  public static void main() {
    System.out.println(choose("left", "right"));
  }
}

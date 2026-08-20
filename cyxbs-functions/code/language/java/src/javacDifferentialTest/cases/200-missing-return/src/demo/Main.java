package demo;

public class Main {
  public static void main() {
    System.out.println(value(true));
  }

  private static int value(boolean ready) {
    if (ready) return 1;
  }
}

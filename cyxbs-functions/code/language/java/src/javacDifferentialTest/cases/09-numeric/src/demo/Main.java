package demo;

public class Main {
  public static void main() {
    long wide = 3_000_000_000L;
    double value = 1.5 + 2.25;
    int narrowed = (int) wide;
    System.out.println(wide + ":" + value + ":" + narrowed);
  }
}

package demo;

public class Main {
  public static void main() {
    double positive = 1.0 / 0.0;
    double negative = -1.0 / 0.0;
    System.out.println((positive > 0.0) + ":" + (negative < 0.0));
  }
}

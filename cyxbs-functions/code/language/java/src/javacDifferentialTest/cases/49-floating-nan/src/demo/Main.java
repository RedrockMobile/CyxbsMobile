package demo;

public class Main {
  public static void main() {
    float value = 0.0f / 0.0f;
    System.out.println((value == value) + ":" + (value != value));
  }
}

package demo;

interface Calculator {
  int apply(int value);
}

public class Main {
  public static void main() {
    int offset = 5;
    Calculator calculator = value -> {
      int doubled = value * 2;
      return doubled + offset;
    };
    System.out.println(calculator.apply(7));
  }
}

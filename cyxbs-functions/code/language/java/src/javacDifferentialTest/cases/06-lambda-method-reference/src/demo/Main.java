package demo;

interface Operation {
  int apply(int value);
}

public class Main {
  static int twice(int value) {
    return value * 2;
  }

  public static void main() {
    Operation addOne = value -> value + 1;
    Operation multiply = Main::twice;
    System.out.println(multiply.apply(addOne.apply(5)));
  }
}

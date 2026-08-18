package demo;

interface Operation {
  int apply(int value);
}

public class Main {
  public static void main() {
    int offset = 3;
    Operation operation = value -> value + offset;
    System.out.println(operation.apply(4));
  }
}

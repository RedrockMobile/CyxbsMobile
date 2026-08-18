package demo;

interface Formatter<T> {
  default String format(T value) {
    return "[" + value.toString() + "]";
  }
}

class TextFormatter implements Formatter<String> { }

public class Main {
  public static void main() {
    Formatter<String> formatter = new TextFormatter();
    System.out.println(formatter.format("java"));
  }
}

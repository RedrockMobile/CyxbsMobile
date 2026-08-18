package demo;

import java.util.function.Supplier;

public class Main {
  public static void main() {
    Supplier<StringBuilder> factory = StringBuilder::new;
    StringBuilder first = factory.get();
    StringBuilder second = factory.get();
    first.append("a");
    second.append("b");
    System.out.println(first.toString() + second.toString());
  }
}

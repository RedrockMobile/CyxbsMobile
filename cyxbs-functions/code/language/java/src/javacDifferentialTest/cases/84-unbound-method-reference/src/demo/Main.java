package demo;

import java.util.function.Function;

public class Main {
  public static void main() {
    Function<String, Integer> length = String::length;
    System.out.println(length.apply("method"));
  }
}

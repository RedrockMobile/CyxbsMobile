package demo;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Main {
  public static void case181() {
    Runnable action = () -> {
      System.out.print("run");
      System.out.println("nable");
    };
    action.run();
  }

  public static void case182() {
    Predicate<Integer> positive = value -> value > 0;
    System.out.println(positive.test(3) + ":" + positive.test(-1));
  }

  public static void case183() {
    Function<String, Integer> length = value -> value.length();
    System.out.println(length.apply("java"));
  }

  public static void case184() {
    String prefix = "lesson";
    Supplier<String> supplier = () -> prefix + "-ready";
    System.out.println(supplier.get());
  }

  public static void case185() {
    StringBuilder builder = new StringBuilder("bound");
    Supplier<String> supplier = builder::toString;
    builder.append("-ref");
    System.out.println(supplier.get());
  }

  public static void case186() {
    Function<String, Integer> length = String::length;
    System.out.println(length.apply("unbound"));
  }

  public static void case187() {
    Function<Integer, Integer> operation = Main::triple;
    System.out.println(operation.apply(4));
  }

  public static void case188() {
    Function<String, StringBuilder> factory = StringBuilder::new;
    System.out.println(factory.apply("ctor").append("-ref"));
  }

  public static void case189() {
    Level[] values = Level.values();
    System.out.println(values.length + ":" + values[0].name() + ":" + values[2].ordinal());
  }

  public static void case190() {
    Label value = Level.HIGH;
    System.out.println(value.label());
  }

  private static Integer triple(Integer value) {
    return value * 3;
  }
}

interface Label {
  String label();
}

enum Level implements Label {
  LOW,
  MEDIUM,
  HIGH;

  @Override
  public String label() {
    return name();
  }
}

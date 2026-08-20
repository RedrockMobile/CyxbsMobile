package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  private static <T> T identity(T value) {
    return value;
  }

  private static <T extends Number> int numberValue(T value) {
    return value.intValue();
  }

  private static int first(List<? extends Number> values) {
    return values.get(0).intValue();
  }

  private static void addValue(List<? super Integer> values) {
    values.add(9);
  }

  private static String choose(long value) {
    return "long";
  }

  private static String choose(Integer value) {
    return "Integer";
  }

  private static String choose(Object value) {
    return "Object";
  }

  private static String choose(String value) {
    return "String";
  }

  private static String arity(int value) {
    return "fixed";
  }

  private static String arity(int... values) {
    return "varargs";
  }

  public static void run251() {
    System.out.println(identity("java"));
  }

  public static void run252() {
    System.out.println(numberValue(Integer.valueOf(7)));
  }

  public static void run253() {
    List<Integer> values = new ArrayList<Integer>();
    values.add(5);
    System.out.println(first(values));
  }

  public static void run254() {
    List<Number> values = new ArrayList<Number>();
    addValue(values);
    System.out.println(values.get(0).intValue());
  }

  public static void run255() {
    GenericChild child = new GenericChild();
    System.out.println(child.value("ok"));
  }

  public static void run256() {
    System.out.println(Main.<String>identity("explicit"));
  }

  public static void run257() {
    System.out.println(choose(1));
  }

  public static void run258() {
    String value = null;
    System.out.println(choose(value));
  }

  public static void run259() {
    System.out.println(arity(1));
  }

  public static void run260() {
    GenericBase<String> value = new CovariantChild();
    System.out.println(value.value("x"));
  }
}

class GenericBase<T> {
  T value(T value) {
    return value;
  }
}

class GenericChild extends GenericBase<String> {
  @Override
  String value(String value) {
    return "child-" + value;
  }
}

class CovariantChild extends GenericBase<String> {
  @Override
  public String value(String value) {
    return "covariant-" + value;
  }
}

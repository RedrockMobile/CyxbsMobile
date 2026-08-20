package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void case151() {
    String value = identity("java");
    System.out.println(value);
  }

  public static void case152() {
    System.out.println(asInt(Integer.valueOf(12)));
  }

  public static void case153() {
    List<Integer> values = new ArrayList<Integer>();
    values.add(4);
    values.add(6);
    System.out.println(sum(values));
  }

  public static void case154() {
    List<Number> values = new ArrayList<Number>();
    addInteger(values, 9);
    System.out.println(values.get(0).intValue());
  }

  public static void case155() {
    StringBox box = new StringBox();
    box.set("ready");
    System.out.println(box.get());
  }

  public static void case156() {
    System.out.println(select(1));
  }

  public static void case157() {
    System.out.println(specific(null));
  }

  public static void case158() {
    System.out.println(join() + ":" + join(1, 2, 3));
  }

  public static void case159() {
    System.out.println(first("a", "b"));
  }

  public static void case160() {
    System.out.println(acceptStrings(new ArrayList<>()));
  }

  private static <T> T identity(T value) {
    return value;
  }

  private static <T extends Number> int asInt(T value) {
    return value.intValue();
  }

  private static int sum(List<? extends Number> values) {
    return values.get(0).intValue() + values.get(1).intValue();
  }

  private static void addInteger(List<? super Integer> values, int value) {
    values.add(Integer.valueOf(value));
  }

  private static String select(long value) {
    return "long";
  }

  private static String select(Integer value) {
    return "Integer";
  }

  private static String specific(Object value) {
    return "Object";
  }

  private static String specific(String value) {
    return "String";
  }

  private static int join(int... values) {
    int sum = 0;
    for (int value : values) sum += value;
    return sum;
  }

  private static <T> T first(T... values) {
    return values[0];
  }

  private static int acceptStrings(List<String> values) {
    values.add("ok");
    return values.size();
  }
}

class Box<T> {
  private T value;

  public void set(T value) {
    this.value = value;
  }

  public T get() {
    return value;
  }
}

final class StringBox extends Box<String> {
}

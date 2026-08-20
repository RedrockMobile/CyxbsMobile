package demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
  public static void case141() {
    List<String> values = new ArrayList<String>();
    values.add("a");
    values.add("b");
    System.out.println(values.size() + ":" + values.get(1));
  }

  public static void case142() {
    List<Integer> first = new ArrayList<Integer>();
    first.add(1);
    first.add(2);
    List<Integer> second = first;
    Integer previous = second.set(1, 9);
    System.out.println(previous + ":" + first.get(1));
  }

  public static void case143() {
    List<Integer> values = new ArrayList<Integer>();
    values.add(4);
    values.add(5);
    values.add(6);
    Integer removedValue = values.remove(1);
    boolean removedObject = values.remove(Integer.valueOf(6));
    System.out.println(removedValue + ":" + removedObject + ":" + values.size());
  }

  public static void case144() {
    Set<String> values = new HashSet<String>();
    System.out.println(values.add("x") + ":" + values.add("x") + ":" + values.add(null) + ":" + values.size());
  }

  public static void case145() {
    Set<Integer> values = new HashSet<Integer>();
    values.add(3);
    values.add(7);
    Iterator<Integer> iterator = values.iterator();
    int sum = 0;
    while (iterator.hasNext()) {
      sum += iterator.next();
    }
    System.out.println(sum);
  }

  public static void case146() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    Integer first = values.put("score", 1);
    Integer second = values.put("score", 2);
    System.out.println((first == null) + ":" + second + ":" + values.get("score"));
  }

  public static void case147() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    values.put("ready", 8);
    System.out.println(values.getOrDefault("ready", 0) + ":" + values.getOrDefault("missing", 5));
  }

  public static void case148() {
    Map<String, String> values = new HashMap<String, String>();
    values.put(null, "empty");
    System.out.println(values.containsKey(null) + ":" + values.get(null));
  }

  public static void case149() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    values.put("a", 1);
    values.put("b", 2);
    Set<String> keys = values.keySet();
    keys.remove("a");
    System.out.println(values.containsKey("a") + ":" + values.size());
  }

  public static void case150() {
    Map<Key, String> values = new HashMap<Key, String>();
    Key key = new Key(7);
    values.put(key, "found");
    System.out.println(values.get(key));
  }
}

final class Key {
  private final int value;

  Key(int value) {
    this.value = value;
  }
}

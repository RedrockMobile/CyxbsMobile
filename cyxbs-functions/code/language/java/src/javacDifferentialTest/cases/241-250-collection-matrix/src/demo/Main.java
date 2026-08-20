package demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
  public static void run241() {
    List<String> values = new ArrayList<String>();
    values.add("a");
    values.add("b");
    values.add("c");
    System.out.println(values.get(0) + values.get(1) + values.get(2));
  }

  public static void run242() {
    List<Integer> first = new ArrayList<Integer>();
    first.add(1);
    List<Integer> second = first;
    second.set(0, 9);
    System.out.println(first.get(0));
  }

  public static void run243() {
    List<String> values = new ArrayList<String>();
    values.add("a");
    values.add("b");
    values.add("c");
    System.out.println(values.remove(1) + ":" + values.size());
  }

  public static void run244() {
    List<Integer> values = new ArrayList<Integer>();
    values.add(1);
    values.add(2);
    values.add(3);
    System.out.println(values.remove(Integer.valueOf(2)) + ":" + values.size());
  }

  public static void run245() {
    Set<String> values = new HashSet<String>();
    System.out.println(values.add(null) + ":" + values.add(null) + ":" + values.size());
  }

  public static void run246() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    System.out.println(values.put("x", 1));
    System.out.println(values.put("x", 2));
    System.out.println(values.get("x"));
  }

  public static void run247() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    values.put(null, 7);
    System.out.println(values.containsKey(null) + ":" + values.get(null));
  }

  public static void run248() {
    Map<String, Integer> values = new HashMap<String, Integer>();
    values.put("a", 1);
    values.put("b", 2);
    Set<String> keys = values.keySet();
    keys.remove("a");
    System.out.println(values.containsKey("a") + ":" + values.size());
  }

  public static void run249() {
    List<String> values = new ArrayList<String>();
    values.add("a");
    values.add("b");
    Iterator<String> iterator = values.iterator();
    String result = "";
    while (iterator.hasNext()) {
      result += iterator.next();
    }
    System.out.println(result);
  }

  public static void run250() {
    Object key = new LessonKey();
    Map<Object, String> values = new HashMap<Object, String>();
    values.put(key, "found");
    System.out.println(values.get(key) + ":" + values.get(new LessonKey()));
  }
}

class LessonKey {
}

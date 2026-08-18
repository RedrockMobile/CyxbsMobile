package demo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {
  public static void main() {
    Map<String, Integer> values = new HashMap<>();
    values.put("a", 1);
    values.put("b", 2);
    Set<String> keys = values.keySet();
    keys.remove("a");
    System.out.println(values.size() + ":" + values.getOrDefault("b", 0));
  }
}

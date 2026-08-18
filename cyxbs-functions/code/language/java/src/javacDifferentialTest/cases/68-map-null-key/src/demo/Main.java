package demo;

import java.util.HashMap;
import java.util.Map;

public class Main {
  public static void main() {
    Map<String, String> values = new HashMap<>();
    values.put(null, "empty");
    values.put("key", null);
    System.out.println(values.get(null) + ":" + values.containsKey("key") + ":" + (values.get("key") == null));
  }
}

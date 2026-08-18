package demo;

import java.util.HashMap;
import java.util.Map;

public class Main {
  public static void main() {
    Map<String, Integer> values = new HashMap<>();
    Integer first = values.put("answer", 41);
    Integer second = values.put("answer", 42);
    System.out.println((first == null) + ":" + second + ":" + values.get("answer"));
  }
}

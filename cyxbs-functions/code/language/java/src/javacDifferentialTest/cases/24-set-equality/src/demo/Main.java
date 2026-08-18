package demo;

import java.util.HashSet;
import java.util.Set;

public class Main {
  public static void main() {
    Set<String> values = new HashSet<>();
    System.out.println(values.add("same"));
    System.out.println(values.add(new StringBuilder("same").toString()));
    System.out.println(values.contains("same") + ":" + values.size());
  }
}

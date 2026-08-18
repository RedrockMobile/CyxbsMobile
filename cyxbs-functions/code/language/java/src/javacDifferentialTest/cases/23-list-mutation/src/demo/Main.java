package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main() {
    List<String> values = new ArrayList<>();
    values.add("a");
    values.add("b");
    values.set(0, "x");
    values.remove(1);
    System.out.println(values.size() + ":" + values.get(0));
  }
}

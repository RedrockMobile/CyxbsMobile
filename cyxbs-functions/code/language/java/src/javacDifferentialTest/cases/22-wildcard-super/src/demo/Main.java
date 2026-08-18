package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  static void addValue(List<? super Integer> values) { values.add(7); }

  public static void main() {
    List<Number> values = new ArrayList<>();
    addValue(values);
    System.out.println(values.size() + ":" + values.get(0).intValue());
  }
}

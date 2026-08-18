package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  static int sum(List<? extends Number> values) {
    int result = 0;
    for (int index = 0; index < values.size(); index++) {
      result += values.get(index).intValue();
    }
    return result;
  }

  public static void main() {
    List<Integer> values = new ArrayList<>();
    values.add(2);
    values.add(5);
    System.out.println(sum(values));
  }
}

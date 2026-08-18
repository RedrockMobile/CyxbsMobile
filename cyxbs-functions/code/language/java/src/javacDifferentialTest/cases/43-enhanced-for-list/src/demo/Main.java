package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main() {
    List<Integer> values = new ArrayList<>();
    values.add(3);
    values.add(4);
    values.add(5);
    int total = 0;
    for (Integer value : values) {
      total += value;
    }
    System.out.println(total);
  }
}

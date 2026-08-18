package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  static int read(List<? extends Number> values) {
    return values.get(0).intValue();
  }

  static void write(List<? super Integer> values) {
    values.add(9);
  }

  public static void main() {
    List<Integer> values = new ArrayList<>();
    values.add(3);
    write(values);
    System.out.println(read(values) + ":" + values.get(1));
  }
}

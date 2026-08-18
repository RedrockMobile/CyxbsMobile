package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main() {
    List<String> values = new ArrayList<>();
    values.add("a");
    values.add("b");
    System.out.println(values.size() + ":" + values.get(1));
  }
}

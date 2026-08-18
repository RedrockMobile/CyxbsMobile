package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main() {
    List<Integer> values = new ArrayList<>();
    values.add(10);
    values.add(20);
    values.add(30);
    Integer removedValue = Integer.valueOf(20);
    boolean removed = values.remove(removedValue);
    int first = values.remove(0);
    System.out.println(removed + ":" + first + ":" + values.size());
  }
}

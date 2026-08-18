package demo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Main {
  public static void main() {
    List<Integer> values = new ArrayList<>();
    values.add(3);
    values.add(4);
    Iterator<Integer> iterator = values.iterator();
    int sum = 0;
    while (iterator.hasNext()) sum += iterator.next();
    System.out.println(sum);
  }
}

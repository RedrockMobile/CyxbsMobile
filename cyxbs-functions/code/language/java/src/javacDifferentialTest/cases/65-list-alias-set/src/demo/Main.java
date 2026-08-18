package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main() {
    List<String> first = new ArrayList<>();
    first.add("a");
    first.add("b");
    List<String> second = first;
    String old = second.set(1, "z");
    System.out.println(old + ":" + first.get(1));
  }
}

package demo;

interface Named {
  default String name() { return "default"; }
}

class Item implements Named { }

class OverrideItem implements Named {
  @Override public String name() { return "override"; }
}

public class Main {
  public static void main() {
    Named first = new Item();
    Named second = new OverrideItem();
    System.out.println(first.name() + ":" + second.name());
  }
}

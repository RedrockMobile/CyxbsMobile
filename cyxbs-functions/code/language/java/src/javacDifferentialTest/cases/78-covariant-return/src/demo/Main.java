package demo;

class Parent {
  Object value() { return "parent"; }
}

class Child extends Parent {
  @Override String value() { return "child"; }
}

public class Main {
  public static void main() {
    Parent value = new Child();
    System.out.println(value.value());
  }
}

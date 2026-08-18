package demo;

class Parent {
  Parent() { System.out.println(value()); }
  int value() { return 1; }
}

class Child extends Parent {
  int number = 7;
  @Override int value() { return number; }
}

public class Main {
  public static void main() {
    Child child = new Child();
    System.out.println(child.value());
  }
}

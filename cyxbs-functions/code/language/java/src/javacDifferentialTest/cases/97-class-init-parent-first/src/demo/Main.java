package demo;

class Parent {
  static int value = trace("parent", 2);

  static int trace(String name, int value) {
    System.out.print(name + ";");
    return value;
  }
}

class Child extends Parent {
  static int value = trace("child", 3);
}

public class Main {
  public static void main() {
    System.out.println(Child.value + Parent.value);
  }
}

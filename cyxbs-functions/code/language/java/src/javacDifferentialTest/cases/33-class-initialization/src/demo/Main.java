package demo;

class Parent {
  static int value = trace("parent", 1);
  static int trace(String text, int value) {
    System.out.println(text);
    return value;
  }
}

class Child extends Parent {
  static int child = trace("child", value + 1);
}

public class Main {
  public static void main() {
    System.out.println(Child.child);
    System.out.println(Child.child);
  }
}

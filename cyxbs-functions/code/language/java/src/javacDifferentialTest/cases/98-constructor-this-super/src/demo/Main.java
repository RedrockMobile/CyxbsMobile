package demo;

class Parent {
  final int value;

  Parent(int value) {
    this.value = value;
    System.out.print("parent;");
  }
}

class Child extends Parent {
  Child() {
    this(4);
    System.out.print("empty;");
  }

  Child(int value) {
    super(value);
    System.out.print("child;");
  }
}

public class Main {
  public static void main() {
    Child child = new Child();
    System.out.println(child.value);
  }
}

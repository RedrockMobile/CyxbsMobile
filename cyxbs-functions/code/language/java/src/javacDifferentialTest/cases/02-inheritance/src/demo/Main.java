package demo;

class Base {
  final int value;

  Base(int value) {
    this.value = value;
  }

  int score() {
    return value;
  }
}

class Child extends Base {
  Child(int value) {
    super(value);
  }

  @Override
  int score() {
    return value + 2;
  }
}

public class Main {
  public static void main() {
    Base item = new Child(5);
    System.out.println(item.score());
  }
}

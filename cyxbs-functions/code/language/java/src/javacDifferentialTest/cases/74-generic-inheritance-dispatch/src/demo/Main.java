package demo;

class Box<T> {
  private final T value;
  Box(T value) { this.value = value; }
  T value() { return value; }
}

class StringBox extends Box<String> {
  StringBox(String value) { super(value); }
  @Override String value() { return super.value() + "!"; }
}

public class Main {
  public static void main() {
    Box<String> box = new StringBox("ok");
    System.out.println(box.value());
  }
}

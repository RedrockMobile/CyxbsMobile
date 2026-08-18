package demo;

interface Supplier {
  String get();
}

public class Main {
  public static void main() {
    StringBuilder builder = new StringBuilder("value");
    Supplier supplier = builder::toString;
    builder.append(7);
    System.out.println(supplier.get());
  }
}

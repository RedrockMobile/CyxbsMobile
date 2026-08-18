package demo;

class DefaultIncrement implements Increment { }

public class Main {
  public static void main() {
    Increment operation = new DefaultIncrement();
    System.out.println(operation.apply(5));
  }
}

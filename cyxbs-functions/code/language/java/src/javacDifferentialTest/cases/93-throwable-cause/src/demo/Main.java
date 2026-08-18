package demo;

public class Main {
  public static void main() {
    IllegalArgumentException cause = new IllegalArgumentException("inner");
    IllegalStateException outer = new IllegalStateException("outer", cause);
    System.out.println(outer.getMessage());
    System.out.println(outer.getCause().getMessage());
    System.out.println(outer.getCause() == cause);
  }
}

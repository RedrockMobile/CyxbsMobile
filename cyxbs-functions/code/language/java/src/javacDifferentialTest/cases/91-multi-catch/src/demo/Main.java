package demo;

public class Main {
  static void fail(int kind) {
    if (kind == 1) throw new IllegalArgumentException("argument");
    throw new IllegalStateException("state");
  }

  public static void main() {
    for (int kind = 1; kind <= 2; kind++) {
      try {
        fail(kind);
      } catch (IllegalArgumentException | IllegalStateException exception) {
        System.out.println(exception.getMessage());
      }
    }
  }
}

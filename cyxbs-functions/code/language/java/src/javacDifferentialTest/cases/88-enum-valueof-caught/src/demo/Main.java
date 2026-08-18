package demo;

enum Direction {
  LEFT,
  RIGHT
}

public class Main {
  public static void main() {
    System.out.println(Direction.valueOf("RIGHT").ordinal());
    try {
      Direction.valueOf("UP");
    } catch (IllegalArgumentException exception) {
      System.out.println("unknown");
    }
  }
}

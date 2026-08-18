package demo;

enum Level {
  LOW,
  HIGH
}

public class Main {
  static int score(Level level) {
    switch (level) {
      case LOW:
        return 1;
      case HIGH:
        return 2;
      default:
        return 0;
    }
  }

  public static void main() {
    System.out.println(score(Level.HIGH));
  }
}

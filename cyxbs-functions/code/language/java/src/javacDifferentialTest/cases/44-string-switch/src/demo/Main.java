package demo;

public class Main {
  static int score(String value) {
    switch (value) {
      case "red": return 1;
      case "green": return 2;
      default: return 3;
    }
  }

  public static void main() {
    System.out.println(score("green") + score("other"));
  }
}

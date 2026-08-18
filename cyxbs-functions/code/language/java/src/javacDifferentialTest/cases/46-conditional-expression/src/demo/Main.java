package demo;

public class Main {
  static int max(int first, int second) {
    return first > second ? first : second;
  }

  public static void main() {
    int value = max(8, 3);
    System.out.println(value == 8 ? "yes" : "no");
  }
}

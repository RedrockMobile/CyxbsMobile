package demo;

public class Main {
  public static void main() {
    int result = 0;
    for (int value = 1; value <= 3; value++) {
      switch (value) {
        case 1:
          result += 1;
        case 2:
          result += 2;
          break;
        default:
          result += 4;
      }
    }
    System.out.println(result);
  }
}

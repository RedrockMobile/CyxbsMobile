package demo;

public class Main {
  public static void main() {
    int result = 0;
    for (int value = 0; value < 6; value++) {
      if (value == 1) continue;
      switch (value) {
        case 4:
          result += 10;
          break;
        case 5:
          break;
        default:
          result += value;
      }
    }
    System.out.println(result);
  }
}

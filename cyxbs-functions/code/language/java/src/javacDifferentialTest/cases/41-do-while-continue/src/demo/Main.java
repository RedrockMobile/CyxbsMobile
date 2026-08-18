package demo;

public class Main {
  public static void main() {
    int value = 0;
    int sum = 0;
    do {
      value++;
      if (value % 2 == 0) continue;
      sum += value;
    } while (value < 7);
    System.out.println(sum);
  }
}

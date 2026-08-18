package demo;

public class Main {
  public static void main() {
    int total = 0;
    for (int value : new int[]{2, 3, 5, 7}) {
      total += value;
    }
    System.out.println(total);
  }
}

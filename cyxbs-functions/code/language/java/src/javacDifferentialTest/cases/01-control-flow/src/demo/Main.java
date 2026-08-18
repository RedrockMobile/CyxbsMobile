package demo;

public class Main {
  public static void main() {
    int sum = 0;
    for (int index = 0; index < 5; index++) {
      sum += index;
    }
    System.out.println(sum);
    System.err.print("trace");
  }
}

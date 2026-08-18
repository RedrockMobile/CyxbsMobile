package demo;

public class Main {
  public static void main() {
    int[] first = {1, 2, 3};
    int[] second = first;
    second[1] += 5;
    System.out.println(first[1]);
  }
}

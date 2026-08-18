package demo;

import java.util.Scanner;

public class Main {
  public static void main() {
    Scanner scanner = new Scanner(System.in);
    int value = scanner.nextInt();
    String text = scanner.next();
    System.out.println((value + 1) + ":" + text);
  }
}

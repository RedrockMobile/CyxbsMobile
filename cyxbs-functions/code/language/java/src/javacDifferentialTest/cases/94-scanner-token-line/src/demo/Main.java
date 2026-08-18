package demo;

import java.util.Scanner;

public class Main {
  public static void main() {
    Scanner scanner = new Scanner(System.in);
    int number = scanner.nextInt();
    String remainder = scanner.nextLine();
    String line = scanner.nextLine();
    System.out.println(number + ":" + remainder.length() + ":" + line);
  }
}

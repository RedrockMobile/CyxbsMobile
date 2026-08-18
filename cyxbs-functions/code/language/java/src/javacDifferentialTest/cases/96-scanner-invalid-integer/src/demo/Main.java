package demo;

import java.util.InputMismatchException;
import java.util.Scanner;

public class Main {
  public static void main() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.hasNextInt());
    try {
      scanner.nextInt();
    } catch (InputMismatchException exception) {
      System.out.println(scanner.next());
    }
  }
}

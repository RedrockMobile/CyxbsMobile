package demo;

import java.util.Scanner;

public class Main {
  public static void main() {
    Scanner scanner = new Scanner(System.in);
    while (scanner.hasNextLine()) {
      System.out.println("[" + scanner.nextLine() + "]");
    }
  }
}

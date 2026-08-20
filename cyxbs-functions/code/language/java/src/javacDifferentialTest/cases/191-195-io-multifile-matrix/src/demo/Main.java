package demo;

import java.util.Scanner;

public class Main {
  public static void case191() {
    Scanner scanner = new Scanner(System.in);
    int sum = scanner.nextInt() + scanner.nextInt();
    System.out.println(sum);
  }

  public static void case192() {
    Scanner scanner = new Scanner(System.in);
    String first = scanner.next();
    String remainder = scanner.nextLine();
    String next = scanner.nextLine();
    System.out.println(first + ":" + remainder + ":" + next);
  }

  public static void case193() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.hasNextInt() + ":" + scanner.next());
  }

  public static void case194() {
    System.out.print("out");
    System.err.println("error");
    System.out.println("-done");
  }

  public static void case195() {
    Service<String> service = new MemoryService();
    System.out.println(Helper.message(service.load()));
  }
}

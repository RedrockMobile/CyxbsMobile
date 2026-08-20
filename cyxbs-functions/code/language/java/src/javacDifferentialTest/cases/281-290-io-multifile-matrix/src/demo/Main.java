package demo;

import java.util.Scanner;

public class Main {
  public static void run281() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.next() + ":" + scanner.next());
  }

  public static void run282() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.nextInt() + scanner.nextInt());
  }

  public static void run283() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.nextLine() + ":" + scanner.nextLine());
  }

  public static void run284() {
    Scanner scanner = new Scanner(System.in);
    System.out.println("[" + scanner.nextLine() + "]:[" + scanner.nextLine() + "]");
  }

  public static void run285() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.hasNextInt());
    System.out.println(scanner.next());
    System.out.println(scanner.nextInt());
  }

  public static void run286() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.hasNext() + ":" + scanner.next() + ":" + scanner.hasNext());
  }

  public static void run287() {
    Scanner first = new Scanner(System.in);
    Scanner second = first;
    System.out.println(first.next() + second.next());
  }

  public static void run288() {
    Scanner scanner = new Scanner(System.in);
    System.out.println(scanner.nextLine() + scanner.nextLine());
  }

  public static void run289() {
    System.out.println(Helper.identity(new Service("multi")).value());
  }

  public static void run290() {
    System.out.print("out-1");
    System.err.print("err-1");
    System.out.println("-out-2");
    System.err.println("-err-2");
  }
}

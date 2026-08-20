package demo;

public class Main {
  private static int trace;

  private static boolean mark(int value) {
    trace = trace * 10 + value;
    return value > 0;
  }

  public static void run201() {
    int sum = 0;
    for (int outer = 0; outer < 3; outer++) {
      for (int inner = 0; inner < 4; inner++) {
        if (inner == 2) {
          break;
        }
        sum += outer * 10 + inner;
      }
    }
    System.out.println(sum);
  }

  public static void run202() {
    int sum = 0;
    for (int outer = 0; outer < 3; outer++) {
      for (int inner = 0; inner < 4; inner++) {
        if (inner % 2 == 0) {
          continue;
        }
        sum += outer + inner;
      }
    }
    System.out.println(sum);
  }

  public static void run203() {
    trace = 0;
    for (int value = 0; value < 3; value++, mark(value)) {
      trace = trace * 10 + value;
    }
    System.out.println(trace);
  }

  public static void run204() {
    int value = 0;
    int checks = 0;
    while (++checks < 3 && ++value < 5) {
      value++;
    }
    System.out.println(checks + ":" + value);
  }

  public static void run205() {
    int count = 0;
    do {
      count++;
    } while (false);
    System.out.println(count);
  }

  public static void run206() {
    int result = 0;
    switch (8) {
      case 4:
        result = 4;
        break;
      default:
        result += 10;
      case 5:
        result += 5;
    }
    System.out.println(result);
  }

  public static void run207() {
    int value = true ? false ? 1 : 2 : 3;
    System.out.println(value);
  }

  public static void run208() {
    trace = 0;
    boolean value = false && mark(1);
    System.out.println(value + ":" + trace);
  }

  public static void run209() {
    trace = 0;
    boolean value = true || mark(1);
    System.out.println(value + ":" + trace);
  }

  public static void run210() {
    int traceValue = 0;
    for (int value : new int[]{3, 1, 4}) {
      traceValue = traceValue * 10 + value;
    }
    System.out.println(traceValue);
  }
}

package demo;

import java.util.ArrayList;
import java.util.List;

public class Main {
  private static int hits;

  public static void case101() {
    int sum = 0;
    int value = 0;
    while (value < 10) {
      value++;
      if (value % 2 == 0) continue;
      if (value > 7) break;
      sum += value;
    }
    System.out.println(sum);
  }

  public static void case102() {
    int count = 0;
    do {
      count++;
    } while (false);
    System.out.println(count);
  }

  public static void case103() {
    int value = 0;
    for (int index = 0; index < 3; index++) {
      value = value * 10 + index;
    }
    System.out.println(value);
  }

  public static void case104() {
    int count = 0;
    for (int outer = 0; outer < 3; outer++) {
      for (int inner = 0; inner < 5; inner++) {
        if (inner == 2) break;
        count++;
      }
    }
    System.out.println(count);
  }

  public static void case105() {
    int result = 0;
    switch (2) {
      default:
        result += 10;
      case 4:
        result += 4;
        break;
      case 3:
        result += 3;
    }
    System.out.println(result);
  }

  public static void case106() {
    int result;
    switch ("beta") {
      case "alpha": result = 1; break;
      case "beta": result = 2; break;
      default: result = 3;
    }
    System.out.println(result);
  }

  public static void case107() {
    hits = 0;
    int result = true ? hit(1) : hit(10);
    System.out.println(result + ":" + hits);
  }

  public static void case108() {
    int value = 7;
    String label = value < 0 ? "negative" : value < 10 ? "small" : "large";
    System.out.println(label);
  }

  public static void case109() {
    int product = 1;
    for (int value : new int[]{2, 3, 4}) {
      product *= value;
    }
    System.out.println(product);
  }

  public static void case110() {
    List<Integer> values = new ArrayList<Integer>();
    values.add(3);
    values.add(5);
    values.add(7);
    int sum = 0;
    for (Integer value : values) {
      sum += value;
    }
    System.out.println(sum);
  }

  private static int hit(int value) {
    hits++;
    return value;
  }
}

package demo;

public class Main {
  public static void main() {
    Integer value = Integer.valueOf(257);
    Number number = value;
    System.out.println(number.intValue() + ":" + number.floatValue() + ":" + number.doubleValue());
  }
}

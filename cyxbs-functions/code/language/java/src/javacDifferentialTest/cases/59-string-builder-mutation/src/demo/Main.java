package demo;

public class Main {
  public static void main() {
    StringBuilder builder = new StringBuilder("abc");
    builder.setCharAt(1, 'Z');
    builder.append(12).append(true);
    System.out.println(builder.reverse().toString());
    System.out.println(builder.substring(2, 5));
  }
}

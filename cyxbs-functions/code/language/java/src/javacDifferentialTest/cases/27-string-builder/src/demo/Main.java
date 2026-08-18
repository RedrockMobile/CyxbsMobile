package demo;

public class Main {
  public static void main() {
    StringBuilder builder = new StringBuilder("ab");
    builder.append(12).append(true).setCharAt(1, 'X');
    System.out.println(builder.reverse().substring(1));
  }
}

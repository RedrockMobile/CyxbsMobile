package demo;

public class Main {
  static <T> T identity(T value) {
    return value;
  }

  public static void main() {
    String text = identity("typed");
    Integer number = identity(Integer.valueOf(7));
    System.out.println(text + ":" + number);
  }
}

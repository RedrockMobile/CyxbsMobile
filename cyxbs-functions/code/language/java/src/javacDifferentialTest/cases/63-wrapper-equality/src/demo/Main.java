package demo;

public class Main {
  public static void main() {
    Integer first = Integer.valueOf(500);
    Integer second = Integer.valueOf(500);
    Long other = Long.valueOf(500L);
    System.out.println(first.equals(second) + ":" + first.equals(other) + ":" + first.hashCode());
  }
}

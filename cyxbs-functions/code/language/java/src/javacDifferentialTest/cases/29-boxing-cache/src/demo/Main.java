package demo;

public class Main {
  public static void main() {
    Integer lowA = 127;
    Integer lowB = 127;
    Integer highA = 128;
    Integer highB = 128;
    System.out.println((lowA == lowB) + ":" + (highA == highB));
    System.out.println(highA.equals(highB) + ":" + highA.hashCode());
  }
}

package demo;

public class Main {
  public static void main() {
    long wide = 4_294_967_297L;
    int narrowed = (int) wide;
    double decimal = 7.75;
    int truncated = (int) decimal;
    float single = (float) decimal;
    System.out.println(narrowed + ":" + truncated + ":" + single);
  }
}

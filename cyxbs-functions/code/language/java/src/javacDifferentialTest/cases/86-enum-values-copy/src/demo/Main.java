package demo;

enum Mode {
  FIRST,
  SECOND
}

public class Main {
  public static void main() {
    Mode[] first = Mode.values();
    first[0] = Mode.SECOND;
    Mode[] second = Mode.values();
    System.out.println(second[0].name() + ":" + second[1].ordinal());
  }
}

package demo;

interface Scale {
  static int base() { return 3; }
  default int apply(int value) { return value * Scale.base(); }
}

class ScaleImpl implements Scale { }

public class Main {
  public static void main() {
    Scale scale = new ScaleImpl();
    System.out.println(Scale.base() + ":" + scale.apply(4));
  }
}

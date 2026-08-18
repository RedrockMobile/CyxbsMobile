package demo;

interface Code {
  int code();
}

enum State implements Code {
  READY(7),
  DONE(9);

  private final int code;

  State(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}

public class Main {
  public static void main() {
    Code state = State.DONE;
    System.out.println(state.code());
  }
}

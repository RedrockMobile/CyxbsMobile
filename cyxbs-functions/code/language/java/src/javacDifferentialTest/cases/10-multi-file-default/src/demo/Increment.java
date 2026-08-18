package demo;

public interface Increment {
  default int apply(int value) {
    return value + 1;
  }
}

package demo;

final class Helper {
  private Helper() {
  }

  static <T> T identity(T value) {
    return value;
  }
}

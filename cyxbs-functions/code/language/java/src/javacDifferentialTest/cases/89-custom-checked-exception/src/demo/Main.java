package demo;

class LessonException extends Exception {
  LessonException(String message) {
    super(message);
  }
}

public class Main {
  static void verify(int value) throws LessonException {
    if (value < 0) throw new LessonException("negative:" + value);
  }

  public static void main() {
    try {
      verify(-3);
    } catch (LessonException exception) {
      System.out.println(exception.getMessage());
    }
  }
}

package demo;

public class Main {
  public static void case171() {
    try {
      int denominator = 0;
      int ignored = 1 / denominator;
      System.out.println(ignored);
    } catch (ArithmeticException exception) {
      System.out.println("caught");
    }
  }

  public static void case172() {
    System.out.println(returnFromFinally());
  }

  public static void case173() {
    int count = 0;
    while (true) {
      try {
        break;
      } finally {
        count++;
      }
    }
    System.out.println(count);
  }

  public static void case174() {
    try {
      throw new IllegalArgumentException("bad");
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.out.println(exception.getMessage());
    }
  }

  public static void case175() {
    try {
      throwChecked();
    } catch (LessonException exception) {
      System.out.println(exception.getMessage());
    }
  }

  public static void case176() {
    RuntimeException cause = new RuntimeException("root");
    RuntimeException outer = new RuntimeException("outer", cause);
    System.out.println(outer.getMessage() + ":" + outer.getCause().getMessage());
  }

  public static void case177() {
    try {
      try {
        System.out.print("body-");
      } finally {
        System.out.print("inner-");
      }
    } finally {
      System.out.println("outer");
    }
  }

  public static void case178() {
    try (LessonResource first = new LessonResource("first");
         LessonResource second = new LessonResource("second")) {
      System.out.print("body-");
    }
    System.out.println("done");
  }

  public static void case179() {
    try (FailingResource resource = new FailingResource()) {
      System.out.print("body-");
    } catch (RuntimeException exception) {
      System.out.println(exception.getMessage());
    }
  }

  public static void case180() {
    try (LessonResource resource = new LessonResource("only")) {
      System.out.print("body-");
    }
    System.out.println("done");
  }

  private static int returnFromFinally() {
    try {
      return 1;
    } finally {
      return 2;
    }
  }

  private static void throwChecked() throws LessonException {
    throw new LessonException("lesson");
  }
}

final class LessonException extends Exception {
  LessonException(String message) {
    super(message);
  }
}

final class LessonResource implements AutoCloseable {
  private final String name;

  LessonResource(String name) {
    this.name = name;
  }

  @Override
  public void close() {
    System.out.print(name + "-");
  }
}

final class FailingResource implements AutoCloseable {
  @Override
  public void close() {
    throw new RuntimeException("close");
  }
}

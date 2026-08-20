package demo;

public class Main {
  private static String trace;

  private static int returnWithFinally() {
    try {
      return 1;
    } finally {
      trace += "F";
    }
  }

  private static int overriddenReturn() {
    try {
      return 1;
    } finally {
      return 2;
    }
  }

  private static void throwChecked() throws LessonException {
    throw new LessonException("lesson");
  }

  public static void run271() {
    try {
      throw new IllegalArgumentException("bad");
    } catch (IllegalArgumentException exception) {
      System.out.println(exception.getMessage());
    }
  }

  public static void run272() {
    try {
      throwChecked();
    } catch (LessonException exception) {
      System.out.println(exception.getMessage());
    }
  }

  public static void run273() {
    trace = "";
    System.out.println(returnWithFinally() + ":" + trace);
  }

  public static void run274() {
    System.out.println(overriddenReturn());
  }

  public static void run275() {
    try {
      throw new IllegalArgumentException("a");
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.out.println("first");
    }
  }

  public static void run276() {
    try {
      throw new IllegalStateException("b");
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.out.println("second");
    }
  }

  public static void run277() {
    trace = "";
    try {
      try {
        throw new RuntimeException("x");
      } catch (RuntimeException exception) {
        trace += "C";
      } finally {
        trace += "F";
      }
    } finally {
      trace += "O";
    }
    System.out.println(trace);
  }

  public static void run278() {
    ResourceTrace.trace = "";
    try (LessonResource first = new LessonResource("1"); LessonResource second = new LessonResource("2")) {
      ResourceTrace.trace += "B";
    }
    System.out.println(ResourceTrace.trace);
  }

  public static void run279() {
    Exception cause = new Exception("root");
    RuntimeException wrapper = new RuntimeException("wrapper", cause);
    System.out.println(wrapper.getCause().getMessage());
  }

  public static void run280() {
    try {
      int[] values = {1};
      System.out.println(values[2]);
    } catch (ArrayIndexOutOfBoundsException exception) {
      System.out.println("caught");
    }
  }
}

class LessonException extends Exception {
  LessonException(String message) {
    super(message);
  }
}

class ResourceTrace {
  static String trace = "";
}

class LessonResource implements AutoCloseable {
  private final String name;

  LessonResource(String name) {
    this.name = name;
  }

  @Override
  public void close() {
    ResourceTrace.trace += name;
  }
}

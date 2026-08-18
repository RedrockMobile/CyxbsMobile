package demo;

class Resource implements AutoCloseable {
  private final String name;

  Resource(String name) {
    this.name = name;
    System.out.print("open-" + name + ";");
  }

  public void close() {
    System.out.print("close-" + name + ";");
  }
}

public class Main {
  public static void main() {
    try (Resource first = new Resource("a"); Resource second = new Resource("b")) {
      System.out.print("body;");
    }
    System.out.println();
  }
}

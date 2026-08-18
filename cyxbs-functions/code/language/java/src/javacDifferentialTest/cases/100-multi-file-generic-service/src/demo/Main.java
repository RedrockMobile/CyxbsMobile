package demo;

public class Main {
  public static void main() {
    Service<String> service = new MemoryService("ready");
    System.out.println(service.load());
  }
}

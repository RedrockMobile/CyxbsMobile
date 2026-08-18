package demo;

public class MemoryService implements Service<String> {
  private final String value;

  public MemoryService(String value) {
    this.value = value;
  }

  public String load() {
    return value;
  }
}

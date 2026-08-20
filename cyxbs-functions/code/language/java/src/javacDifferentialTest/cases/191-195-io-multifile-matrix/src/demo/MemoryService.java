package demo;

public final class MemoryService implements Service<String> {
  @Override
  public String load() {
    return "ready";
  }
}

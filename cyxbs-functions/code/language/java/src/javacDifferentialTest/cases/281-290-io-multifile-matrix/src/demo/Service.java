package demo;

final class Service {
  private final String value;

  Service(String value) {
    this.value = value;
  }

  String value() {
    return value;
  }
}

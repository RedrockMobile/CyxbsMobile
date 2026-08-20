package demo;

public class Main {
  public static void run261() {
    System.out.println(new OrderChild().trace);
  }

  public static void run262() {
    System.out.println(new Delegating().value);
  }

  public static void run263() {
    System.out.println(new ArgumentChild(7).value);
  }

  public static void run264() {
    DispatchBase value = new DispatchChild();
    System.out.println(value.name());
  }

  public static void run265() {
    HiddenBase base = new HiddenChild();
    HiddenChild child = new HiddenChild();
    System.out.println(base.fieldValue() + ":" + child.fieldValue() + ":" + child.childFieldValue());
  }

  public static void run266() {
    Greeting greeting = new GreetingImpl();
    System.out.println(greeting.message());
  }

  public static void run267() {
    System.out.println(Greeting.staticMessage());
  }

  public static void run268() {
    Producer producer = new StringProducer();
    System.out.println(producer.value());
  }

  public static void run269() {
    System.out.println(StaticOnce.value + ":" + StaticOnce.value + ":" + StaticOnce.count);
  }

  public static void run270() {
    System.out.println(InitChild.value + ":" + InitTrace.trace);
  }
}

class OrderBase {
  String trace = "base";

  OrderBase() {
    trace += "-ctor";
  }
}

class OrderChild extends OrderBase {
  OrderChild() {
    trace += "-child";
  }
}

class Delegating {
  int value;

  Delegating() {
    this(9);
  }

  Delegating(int value) {
    this.value = value;
  }
}

class ArgumentBase {
  int value;

  ArgumentBase(int value) {
    this.value = value;
  }
}

class ArgumentChild extends ArgumentBase {
  ArgumentChild(int value) {
    super(value * 2);
  }
}

class DispatchBase {
  String name() {
    return "base";
  }
}

class DispatchChild extends DispatchBase {
  @Override
  String name() {
    return "child";
  }
}

class HiddenBase {
  int value = 1;

  int fieldValue() {
    return value;
  }
}

class HiddenChild extends HiddenBase {
  int value = 2;

  int childFieldValue() {
    return value;
  }
}

interface Greeting {
  default String message() {
    return "default";
  }

  static String staticMessage() {
    return "static";
  }
}

class GreetingImpl implements Greeting {
}

class Producer {
  Object value() {
    return "base";
  }
}

class StringProducer extends Producer {
  @Override
  String value() {
    return "string";
  }
}

class StaticOnce {
  static int count;
  static int value = initialize();

  static int initialize() {
    count++;
    return 7;
  }
}

class InitTrace {
  static String trace = "";
}

class InitParent {
  static int value = mark("P", 1);

  static int mark(String label, int value) {
    InitTrace.trace += label;
    return value;
  }
}

class InitChild extends InitParent {
  static int value = mark("C", 2);
}

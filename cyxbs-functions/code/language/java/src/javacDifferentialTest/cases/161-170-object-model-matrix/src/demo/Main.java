package demo;

public class Main {
  public static void case161() {
    System.out.println(new ThisChain().value);
  }

  public static void case162() {
    System.out.println(new ChildConstructor().value());
  }

  public static void case163() {
    ParentMethod value = new ChildMethod();
    System.out.println(value.name());
  }

  public static void case164() {
    new ConstructorDispatchChild();
  }

  public static void case165() {
    System.out.println(InitChild.VALUE);
  }

  public static void case166() {
    System.out.println(InitOnce.VALUE + ":" + InitOnce.VALUE + ":" + InitOnce.count);
  }

  public static void case167() {
    FieldChild child = new FieldChild();
    FieldParent value = child;
    System.out.println(value.parentValue() + ":" + child.childValue());
  }

  public static void case168() {
    AnimalFactory factory = new CatFactory();
    System.out.println(factory.create().name());
  }

  public static void case169() {
    System.out.println(new GreetingImpl().greet());
  }

  public static void case170() {
    System.out.println(Scale.doubleValue(6));
  }

  static int initialize(String name, int value) {
    System.out.println(name);
    return value;
  }
}

final class ThisChain {
  final int value;

  ThisChain() {
    this(7);
  }

  ThisChain(int value) {
    this.value = value;
  }
}

class ParentConstructor {
  protected final int base;

  ParentConstructor(int base) {
    this.base = base;
  }
}

final class ChildConstructor extends ParentConstructor {
  private final int extra;

  ChildConstructor() {
    super(4);
    extra = 3;
  }

  int value() {
    return base + extra;
  }
}

class ParentMethod {
  public String name() {
    return "parent";
  }
}

final class ChildMethod extends ParentMethod {
  @Override
  public String name() {
    return "child";
  }
}

class ConstructorDispatchParent {
  ConstructorDispatchParent() {
    System.out.println(read());
  }

  protected int read() {
    return -1;
  }
}

final class ConstructorDispatchChild extends ConstructorDispatchParent {
  private int value = 8;

  @Override
  protected int read() {
    return value;
  }
}

class InitParent {
  static final int VALUE = Main.initialize("parent", 2);
}

final class InitChild extends InitParent {
  static final int VALUE = Main.initialize("child", InitParent.VALUE + 3);
}

final class InitOnce {
  static int count;
  static final int VALUE = initializeOnce();

  private static int initializeOnce() {
    count++;
    return 5;
  }
}

class FieldParent {
  private int value = 1;

  int parentValue() {
    return value;
  }
}

final class FieldChild extends FieldParent {
  private int value = 2;

  int childValue() {
    return value;
  }
}

class Animal {
  public String name() {
    return "animal";
  }
}

final class Cat extends Animal {
  @Override
  public String name() {
    return "cat";
  }
}

class AnimalFactory {
  public Animal create() {
    return new Animal();
  }
}

final class CatFactory extends AnimalFactory {
  @Override
  public Cat create() {
    return new Cat();
  }
}

interface Greeting {
  default String greet() {
    return "hello";
  }
}

final class GreetingImpl implements Greeting {
}

interface Scale {
  static int doubleValue(int value) {
    return value * 2;
  }
}

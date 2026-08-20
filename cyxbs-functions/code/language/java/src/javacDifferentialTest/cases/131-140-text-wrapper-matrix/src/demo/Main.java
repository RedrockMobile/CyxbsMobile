package demo;

public class Main {
  public static void case131() {
    String value = "A🐶B";
    System.out.println(value.length() + ":" + (int) value.charAt(1) + ":" + (int) value.charAt(2));
  }

  public static void case132() {
    String value = "lesson";
    System.out.println(value.substring(0, 3) + ":" + value.substring(3));
  }

  public static void case133() {
    String value = "x🐶y";
    System.out.println(value.indexOf(0x1F436));
  }

  public static void case134() {
    String value = "java-language";
    System.out.println(value.startsWith("java") + ":" + value.endsWith("age") + ":" + value.contains("lang"));
  }

  public static void case135() {
    StringBuilder builder = new StringBuilder("ab");
    builder.append(12).append(true).reverse();
    System.out.println(builder.toString());
  }

  public static void case136() {
    StringBuilder builder = new StringBuilder("java");
    builder.setCharAt(0, 'J');
    System.out.println(builder.charAt(0) + ":" + builder.substring(1));
  }

  public static void case137() {
    Integer lowA = Integer.valueOf(127);
    Integer lowB = Integer.valueOf(127);
    Integer highA = Integer.valueOf(128);
    Integer highB = Integer.valueOf(128);
    System.out.println((lowA == lowB) + ":" + (highA == highB));
  }

  public static void case138() {
    Boolean boolA = Boolean.valueOf(true);
    Boolean boolB = Boolean.valueOf(true);
    Character charA = Character.valueOf('A');
    Character charB = Character.valueOf('A');
    System.out.println((boolA == boolB) + ":" + (charA == charB));
  }

  public static void case139() {
    Integer left = Integer.valueOf(4);
    Integer right = Integer.valueOf(6);
    System.out.println(left + right);
  }

  public static void case140() {
    char[] value = new char[]{'J', 'a', 'v', 'a'};
    System.out.println(value);
  }
}

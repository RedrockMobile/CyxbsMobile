package demo;

public class Main {
  public static void run231() {
    System.out.println("abcdef".substring(2, 5));
  }

  public static void run232() {
    System.out.println("banana".indexOf("xyz"));
  }

  public static void run233() {
    String value = "A\ud83d\udc36B";
    System.out.println(value.length() + ":" + (int) value.charAt(1) + ":" + (int) value.charAt(2));
  }

  public static void run234() {
    StringBuilder builder = new StringBuilder("abcd");
    System.out.println(builder.reverse().toString());
  }

  public static void run235() {
    StringBuilder builder = new StringBuilder("java");
    builder.setCharAt(0, 'J');
    System.out.println(builder.toString());
  }

  public static void run236() {
    StringBuilder builder = new StringBuilder();
    builder.append(true).append(':').append(7);
    System.out.println(builder.toString());
  }

  public static void run237() {
    Integer left = Integer.valueOf(500);
    Integer right = Integer.valueOf(500);
    System.out.println(left.equals(right) + ":" + (left == right));
  }

  public static void run238() {
    Integer cachedLeft = Integer.valueOf(127);
    Integer cachedRight = Integer.valueOf(127);
    Integer uncachedLeft = Integer.valueOf(128);
    Integer uncachedRight = Integer.valueOf(128);
    System.out.println((cachedLeft == cachedRight) + ":" + (uncachedLeft == uncachedRight));
  }

  public static void run239() {
    String value = null;
    System.out.println("value=" + value);
  }

  public static void run240() {
    String value = "language-java";
    System.out.println(value.startsWith("lang") + ":" + value.endsWith("java") + ":" + value.contains("age"));
  }
}

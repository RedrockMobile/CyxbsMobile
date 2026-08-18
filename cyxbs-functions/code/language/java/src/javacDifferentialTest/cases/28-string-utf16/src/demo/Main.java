package demo;

public class Main {
  public static void main() {
    String text = "A😀B";
    System.out.println(text.length() + ":" + text.indexOf(0x1F600));
    System.out.println(text.substring(1, 3).equals("😀"));
  }
}

package demo;

class Animal { }
class Cat extends Animal {
  int value = 7;
}

public class Main {
  public static void main() {
    Cat[] cats = new Cat[2];
    Animal[] animals = cats;
    animals[0] = new Cat();
    System.out.println(cats[0].value);
  }
}

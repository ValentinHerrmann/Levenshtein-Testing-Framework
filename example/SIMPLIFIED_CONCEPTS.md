# Levenshtein - Code Example 01

## Overview
This code example demonstrates several key object-oriented programming concepts in Java, including interface constants, interface implementation, abstract super classes, super-class inheritance, method overriding, method overloading, and constructor overloading. The example uses a simple vehicle hierarchy with a `Driveable` interface and an `AbstractVehicle` abstract class, along with a concrete `Car` class that implements and extends these structures.

It is designed to demonstrate the Levenshtein name deviation by providing clear and distinct names for each concept while maintaining a coherent structure.


## Concepts Demonstrated

### 1. ✅ Interface-Constants
**Datei**: `Driveable.java`
```java
public interface Driveable {
    double MAX_SPEED = 200.0;  // Interface constant (public static final)
    // ...
}
```

### 2. ✅ Interface-Implementation
**Datei**: `Car.java`
```java
public class Car extends AbstractVehicle implements Driveable {
    @Override
    public void start() { ... }

    @Override
    public double getSpeed() { ... }
}
```

### 3. ✅ Abstract Super Class
**Datei**: `AbstractVehicle.java`
```java
public abstract class AbstractVehicle {
    protected String manufacturer;
    protected int year;

    public abstract double calculateCost();  // Abstract method
}
```

### 4. ✅ Super-Class Inheritance
**Datei**: `Car.java`
```java
public class Car extends AbstractVehicle implements Driveable {
    public Car(String manufacturer, int year, double price) {
        super(manufacturer, year);  // Calls superclass constructor
        // ...
    }
}
```

### 5. ✅ Method Overriding
**Datei**: `Car.java`

**a) Overriding abstract method:**
```java
@Override
public double calculateCost() {
    return price * 0.1;  // Implements abstract method from AbstractVehicle
}
```

**b) Overriding concrete method:**
```java
@Override
public String getInfo() {
    return super.getInfo() + " - $" + price;  // Overrides concrete method
}
```

**c) Implementing interface methods:**
```java
@Override
public void start() { ... }

@Override
public double getSpeed() { ... }
```

### 6. ✅ Method Overloading
**Datei**: `Car.java`
```java
// Same method name, different parameters
public double calculateCost() {
    return price * 0.1;
}

public double calculateCost(int years) {
    return price * 0.1 * years;
}
```

### 7. ✅ Constructor Overloading
**Datei**: `Car.java`
```java
// Constructor 1 - with all parameters
public Car(String manufacturer, int year, double price) {
    super(manufacturer, year);
    this.price = price;
    this.speed = 0.0;
}

// Constructor 2 - with default price
public Car(String manufacturer, int year) {
    this(manufacturer, year, 20000.0);  // Calls other constructor
}
```

## Struktur-Übersicht

![Simplified Concepts UML](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ValentinHerrmann/Levenshtein-Testing-Framework/refs/heads/main/code_example_01/puml/simplified_concepts.puml)


## Verwendung

### Beispiel 1: Constructor Overloading
```java
// Mit allen Parametern
Car car1 = new Car("BMW", 2023, 35000.0);

// Mit Default-Preis
Car car2 = new Car("Toyota", 2022);  // price = 20000.0
```

### Beispiel 2: Method Overloading
```java
Car car = new Car("BMW", 2023, 30000.0);

double annual = car.calculateCost();      // 3000.0 (10% of price)
double total = car.calculateCost(5);      // 15000.0 (5 years)
```

### Beispiel 3: Method Overriding
```java
Car car = new Car("BMW", 2023, 30000.0);

// Calls overridden method
String info = car.getInfo();  // "BMW (2023) - $30000.0"
```

### Beispiel 4: Interface Usage
```java
Car car = new Car("BMW", 2023, 30000.0);

car.start();                  // Sets speed to 10.0
double speed = car.getSpeed(); // Returns 10.0
double max = Driveable.MAX_SPEED; // Access interface constant: 200.0
```

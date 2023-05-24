package misk.feature.testing;

import java.util.Objects;

public class TestJsonObject {
  public final String name;
  public final Integer age;

  public TestJsonObject(String name, Integer age) {
    this.name = name;
    this.age = age;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestJsonObject that = (TestJsonObject) o;
    return Objects.equals(name, that.name) && Objects.equals(age, that.age);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, age);
  }
}

package misk.enums;

/** Java helper to get around kotlin type issues in creating enums given an arbitrary class */
public final class RawEnums {
  @SuppressWarnings("unchecked")
  public static Object valueOf(Class<?> enumType, String value) {
    return Enum.valueOf((Class) enumType, value);
  }
}

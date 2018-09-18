package io.kubernetes.client.util;

public final class Watches {
  public static <T> Watch.Response<T> newResponse(String type, T value) {
    return new Watch.Response<>(type, value);
  }

  private Watches() {}
}

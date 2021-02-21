package io.kubernetes.client.util;

public final class Watches {
  private Watches() {
  }

  public static <T> Watch.Response<T> newResponse(String type, T value) {
    return new Watch.Response<>(type, value);
  }
}

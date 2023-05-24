package misk.cloud.gcp.testing;

import com.google.api.client.util.Key;
import javax.annotation.Nonnull;

public class Body {
  private @Key String message;

  public Body() {
  }

  public Body(@Nonnull String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

package misk.feature.testing;

import org.jetbrains.annotations.NotNull;
import misk.feature.Attributes;
import wisp.feature.StringFeatureFlag;
import misk.feature.Feature;

public class TestStringFlag implements StringFeatureFlag {
  public final String clientIdentifier;
  public final TestCountry country;

  public TestStringFlag(String clientIdentifier, TestCountry country) {
    this.clientIdentifier = clientIdentifier;
    this.country = country;
  }

  @NotNull @Override public Feature getFeature() { return new Feature("test-boolean-flag"); }
  @NotNull @Override public String getKey() {
    return clientIdentifier;
  }
  @NotNull @Override public Attributes getAttributes() {
    return new Attributes()
        .with("country", country.toString());
  }
}

package misk.feature.testing;

import org.jetbrains.annotations.NotNull;
import wisp.feature.Attributes;
import wisp.feature.IntFeatureFlag;
import wisp.feature.Feature;

public class TestIntFlag implements IntFeatureFlag {
  public final String clientIdentifier;
  public final TestCountry country;

  public TestIntFlag(String clientIdentifier, TestCountry country) {
    this.clientIdentifier = clientIdentifier;
    this.country = country;
  }

  @NotNull @Override public Feature getFeature() { return new Feature("test-boolean-flag"); }
  @NotNull @Override public String getKey() { return clientIdentifier; }
  @NotNull @Override public Attributes getAttributes() {
    return new Attributes()
        .with("country", country.toString());
  }
}

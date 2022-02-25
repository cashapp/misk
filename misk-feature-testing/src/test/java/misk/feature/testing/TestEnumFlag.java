package misk.feature.testing;

import org.jetbrains.annotations.NotNull;
import wisp.feature.Attributes;
import wisp.feature.Feature;
import wisp.feature.EnumFeatureFlag;

public class TestEnumFlag implements EnumFeatureFlag<TestEnum> {
  public final String clientIdentifier;
  public final TestCountry country;

  public TestEnumFlag(String clientIdentifier, TestCountry country) {
    this.clientIdentifier = clientIdentifier;
    this.country = country;
  }

  @NotNull
  @Override public Feature getFeature() { return new Feature("test-json-flag"); }
  @NotNull @Override public String getKey() { return clientIdentifier; }
  @NotNull @Override public Attributes getAttributes() {
    return new Attributes()
        .with("country", country.toString());
  }

  @NotNull @Override public Class<? extends TestEnum> getReturnType() {
    return TestEnum.class;
  }
}

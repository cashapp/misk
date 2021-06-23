package misk.feature.testing;

import java.util.Map;
import javax.inject.Inject;
import misk.inject.KAbstractModule;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;
import org.junit.jupiter.api.Test;
import wisp.feature.Attributes;
import wisp.feature.Feature;

import static org.assertj.core.api.Assertions.assertThat;

@MiskTest
public class FakeFeatureFlagsJavaTest {
  private final Feature FEATURE = new Feature("foo");

  static class TestModule extends KAbstractModule {
    public void configure() {
      install(new FakeFeatureFlagsModule());
    }
  }

  @MiskTestModule TestModule module = new TestModule();
  @Inject FakeFeatureFlags subject;

  enum JokerTest {
    A
  }

  @Test public void testOverrideWithoutProvidingAttributes() {
    subject.overrideKey(FEATURE, "joker1", 55);
    assertThat(subject.getInt(FEATURE, "joker1")).isEqualTo(55);

    subject.overrideKey(FEATURE, "joker2", true);
    assertThat(subject.getBoolean(FEATURE, "joker2")).isEqualTo(true);

    subject.overrideKey(FEATURE, "joker3", "J");
    assertThat(subject.getString(FEATURE, "joker3")).isEqualTo("J");

    subject.overrideKey(FEATURE, "joker4", JokerTest.A);
    assertThat(subject.getEnum(FEATURE, "joker4", JokerTest.class)).isEqualTo(JokerTest.A);
  }

  @Test public void testOverrideWithAttributes() {
    Attributes attributes = new Attributes(Map.of("type", "bad"));
    subject.overrideKey(FEATURE, "joker1", 55, attributes);
    assertThat(subject.getInt(FEATURE, "joker1", attributes)).isEqualTo(55);

    subject.overrideKey(FEATURE, "joker2", true, attributes);
    assertThat(subject.getBoolean(FEATURE, "joker2", attributes)).isEqualTo(true);

    subject.overrideKey(FEATURE, "joker3", "J", attributes);
    assertThat(subject.getString(FEATURE, "joker3", attributes)).isEqualTo("J");

    subject.overrideKey(FEATURE, "joker4", JokerTest.A);
    assertThat(subject.getEnum(FEATURE, "joker4", JokerTest.class, attributes)).isEqualTo(JokerTest.A);
  }

}

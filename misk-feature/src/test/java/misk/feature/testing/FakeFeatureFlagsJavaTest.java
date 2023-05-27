package misk.feature.testing;

import java.util.Map;
import javax.inject.Inject;
import misk.inject.KAbstractModule;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;
import org.junit.jupiter.api.Test;
import misk.feature.Attributes;
import misk.feature.Feature;

import static org.assertj.core.api.Assertions.assertThat;

@MiskTest
public class FakeFeatureFlagsJavaTest {
  private final Feature FEATURE = new Feature("foo");

  static class TestModule extends KAbstractModule {
    public void configure() {
      install(new FakeFeatureFlagsModule());
      install(new MoshiTestingModule());
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

    subject.overrideKey(FEATURE, "joker5", 1.0);
    assertThat(subject.getDouble(FEATURE, "joker5")).isEqualTo(1.0);
  }

  @Test public void testStrongOverrideWithoutProvidingAttributes() {
    subject.overrideAny(TestBooleanFlag.class, true);
    assertThat(subject.get(new TestBooleanFlag("id", TestCountry.AUSTRALIA))).isEqualTo(true);

    subject.overrideAny(TestStringFlag.class, "hello");
    assertThat(subject.get(new TestStringFlag("id", TestCountry.AUSTRALIA))).isEqualTo("hello");

    subject.overrideAny(TestDoubleFlag.class, 5.0);
    assertThat(subject.get(new TestDoubleFlag("id", TestCountry.AUSTRALIA))).isEqualTo(5.0);

    subject.overrideAny(TestIntFlag.class, 1);
    assertThat(subject.get(new TestIntFlag("id", TestCountry.AUSTRALIA))).isEqualTo(1);

    subject.overrideAny(TestEnumFlag.class, TestEnum.TEST_VALUE_1);
    assertThat(subject.get(new TestEnumFlag("id", TestCountry.AUSTRALIA))).isEqualTo(TestEnum.TEST_VALUE_1);

    subject.overrideAny(TestJsonFlag.class, new TestJsonObject("bob", 37));
    assertThat(subject.get(new TestJsonFlag("id", TestCountry.AUSTRALIA)))
      .isEqualTo(new TestJsonObject("bob", 37));
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

    subject.overrideKey(FEATURE, "joker5", 1.0);
    assertThat(subject.getDouble(FEATURE, "joker5", attributes)).isEqualTo(1.0);
  }

  @Test public void testStrongOverrideWithAttributes() {
    // While possible, this statement is also a good argument for moving to Kotlin
    subject.<Boolean, TestBooleanFlag>overrideAny(TestBooleanFlag.class, true, (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestBooleanFlag("id", TestCountry.AUSTRALIA))).isEqualTo(true);

    subject.<String, TestStringFlag>overrideAny(TestStringFlag.class, "hello", (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestStringFlag("id", TestCountry.AUSTRALIA))).isEqualTo("hello");

    subject.<Double, TestDoubleFlag>overrideAny(TestDoubleFlag.class, 5.0, (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestDoubleFlag("id", TestCountry.AUSTRALIA))).isEqualTo(5.0);

    subject.<Integer, TestIntFlag>overrideAny(TestIntFlag.class, 1, (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestIntFlag("id", TestCountry.AUSTRALIA))).isEqualTo(1);

    subject.<TestEnum, TestEnumFlag>overrideAny(TestEnumFlag.class, TestEnum.TEST_VALUE_1, (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestEnumFlag("id", TestCountry.AUSTRALIA))).isEqualTo(TestEnum.TEST_VALUE_1);

    subject.<TestJsonObject, TestJsonFlag>overrideAny(TestJsonFlag.class, new TestJsonObject("bob", 37), (f) ->
        f.country == TestCountry.AUSTRALIA
    );
    assertThat(subject.get(new TestJsonFlag("id", TestCountry.AUSTRALIA)))
        .isEqualTo(new TestJsonObject("bob", 37));
  }
}

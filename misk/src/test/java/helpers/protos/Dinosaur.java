package helpers.protos;

import com.squareup.wire.Message;
import com.squareup.wire.ProtoAdapter;
import com.squareup.wire.WireField;
import com.squareup.wire.internal.Internal;
import java.util.List;
import okio.ByteString;

import static com.squareup.wire.internal.Internal.checkElementsNotNull;
import static com.squareup.wire.internal.Internal.copyOf;
import static com.squareup.wire.internal.Internal.immutableCopyOf;
import static com.squareup.wire.internal.Internal.newMutableList;

public final class Dinosaur extends Message<Dinosaur, Dinosaur.Builder> {
  public static final ProtoAdapter<Dinosaur> ADAPTER =
      ProtoAdapter.newMessageAdapter(Dinosaur.class);
  public static final String DEFAULT_NAME = "";
  private static final long serialVersionUID = 0L;
  /**
   * Common name of this dinosaur, like "Stegosaurus".
   */
  @WireField(
      tag = 1,
      adapter = "com.squareup.wire.ProtoAdapter#STRING"
  )
  public final String name;

  /**
   * URLs with images of this dinosaur.
   */
  @WireField(
      tag = 2,
      adapter = "com.squareup.wire.ProtoAdapter#STRING",
      label = WireField.Label.REPEATED
  )
  public final List<String> picture_urls;

  public Dinosaur(String name, List<String> picture_urls) {
    this(name, picture_urls, ByteString.EMPTY);
  }

  public Dinosaur(String name, List<String> picture_urls, ByteString unknownFields) {
    super(ADAPTER, unknownFields);
    this.name = name;
    this.picture_urls = immutableCopyOf("picture_urls", picture_urls);
  }

  @Override
  public Builder newBuilder() {
    Builder builder = new Builder();
    builder.name = name;
    builder.picture_urls = copyOf("picture_urls", picture_urls);
    builder.addUnknownFields(unknownFields());
    return builder;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof Dinosaur)) return false;
    Dinosaur o = (Dinosaur) other;
    return Internal.equals(unknownFields(), o.unknownFields())
        && Internal.equals(name, o.name)
        && Internal.equals(picture_urls, o.picture_urls);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode;
    if (result == 0) {
      result = unknownFields().hashCode();
      result = result * 37 + (name != null ? name.hashCode() : 0);
      result = result * 37 + (picture_urls != null ? picture_urls.hashCode() : 1);
      super.hashCode = result;
    }
    return result;
  }

  public static final class Builder extends com.squareup.wire.Message.Builder<Dinosaur, Builder> {
    public String name;

    public List<String> picture_urls;

    public Builder() {
      picture_urls = newMutableList();
    }

    /**
     * Common name of this dinosaur, like "Stegosaurus".
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * URLs with images of this dinosaur.
     */
    public Builder picture_urls(List<String> picture_urls) {
      checkElementsNotNull(picture_urls);
      this.picture_urls = picture_urls;
      return this;
    }

    @Override
    public Dinosaur build() {
      return new Dinosaur(name, picture_urls, buildUnknownFields());
    }
  }
}

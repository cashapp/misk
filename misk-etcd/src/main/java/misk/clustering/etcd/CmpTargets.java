package misk.clustering.etcd;

import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.op.CmpTarget;
import com.google.protobuf.ByteString;

/**
 * {@link CmpTargets} works around the fact that the static methods on etcd {@link CmpTarget}
 * returns package private types, which are inaccessible from Kotlin
 */
public final class CmpTargets {
  public static CmpTarget<Long> version(long version) {
    return CmpTarget.version(version);
  }

  public static CmpTarget<ByteString> value(ByteSequence value) {
    return CmpTarget.value(value);
  }

  public static CmpTarget<Long> createRevision(long revision) {
    return CmpTarget.createRevision(revision);
  }

  public static CmpTarget<Long> modRevision(long revision) {
    return CmpTarget.modRevision(revision);
  }

  private CmpTargets() {}
}

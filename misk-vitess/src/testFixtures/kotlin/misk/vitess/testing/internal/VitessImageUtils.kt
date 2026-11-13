package misk.vitess.testing.internal

/**
 * Derives the vtctldclient image URL from the vttestserver image URL.
 * Swaps `vttestserver` -> `vtctldclient` and strips the `-mysql{version}` suffix from the tag.
 *
 * Example: `ghcr.io/block/vitess/vttestserver:23.0.3-block.1-mysql84`
 *       -> `ghcr.io/block/vitess/vtctldclient:23.0.3-block.1`
 */
internal fun deriveVtctldClientImage(vttestserverImage: String): String {
  return vttestserverImage
    .replace("vttestserver", "vtctldclient")
    .replace(Regex("-mysql\\d+$"), "")
}

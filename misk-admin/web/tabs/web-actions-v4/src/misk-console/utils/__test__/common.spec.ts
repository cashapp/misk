import { matchNextWhitespace } from "@misk-console/utils/common"

test("ws", () => {
  expect(matchNextWhitespace("  ", 0)).toBe(2)
})

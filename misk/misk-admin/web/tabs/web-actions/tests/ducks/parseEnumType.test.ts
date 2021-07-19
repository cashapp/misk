import { parseEnumType } from "../../src/ducks"

describe("Parse server type Enum", () => {
  const testEnumType = "Enum<app.cash.common.AlphaEnum,Alpha,Bravo,Delta>"
  it("parses class name", () => {
    const parsed = parseEnumType(testEnumType)
    expect(parsed.enumClassName).toEqual("app.cash.common.AlphaEnum")
  })
  it("parses enum values", () => {
    const parsed = parseEnumType(testEnumType)
    expect(parsed.enumValues).toEqual(["Alpha", "Bravo", "Delta"])
  })
})

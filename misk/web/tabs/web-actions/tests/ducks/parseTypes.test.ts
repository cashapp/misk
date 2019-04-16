import { parseType, ServerTypes } from "../../src/ducks"

describe("Test parsing of string to ServerType", () => {
  it("double", () => {
    expect(parseType(ServerTypes.Double, "-123.456")).toEqual(-123.456)
  })
  it("int", () => {
    expect(parseType(ServerTypes.Int, "-123.456")).toEqual(-123)
  })
  it("long", () => {
    expect(parseType(ServerTypes.Long, "-123.456")).toEqual(-123.456)
  })
  it("short", () => {
    expect(parseType(ServerTypes.Short, "-123.456")).toEqual(-123.456)
  })
})

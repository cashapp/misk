import { parseType, ServerTypes } from "../../src/ducks"

describe("Test parsing of string to ServerType", () => {
  it("double doesn't parse (we only support float)", () => {
    expect(parseType(ServerTypes.Double, "-123.456")).toEqual("-123.456")
  })
  it("float", () => {
    expect(parseType(ServerTypes.Float, "-123.456")).toEqual(-123.456)
  })
  it("int", () => {
    expect(parseType(ServerTypes.Int, "-123.456")).toEqual(-123)
  })
  it("long", () => {
    expect(parseType(ServerTypes.Long, "-123.456")).toEqual(-123)
  })
  it("short", () => {
    expect(parseType(ServerTypes.Short, "-123.456")).toEqual(-123)
  })
})

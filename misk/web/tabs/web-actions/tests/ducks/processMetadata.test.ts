import { processMetadata } from "../../src/ducks"
import { nonTypedActionAPI } from "../testUtilities"

describe("Convert incoming IWebActionAPI to IWebActionInternal", () => {
  it("processMetadata converts IWebActionAPI[] to IWebActionInternal[]", () => {
    expect(processMetadata([nonTypedActionAPI])).toMatchSnapshot()
  })
})

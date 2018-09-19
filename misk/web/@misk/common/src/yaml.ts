export const indent = (spaces: number) => {
  let result = ""
  for (let i = 0; i < spaces; i++) {
    result += "\ \ "
  }
  return result
}

/**
 * @param json 
 * Stringified Json to Yaml string
 */
export const stringifiedJsonToYaml = (json: string) => {
  let result = ""
  let afterKey = false                       // Keeps track that current read ahead is a String, Number, or sub block
  let isKey = true
  let prev = ""
  let level = 0
  for (const c of json) {
    switch (c) {
      case "{": 
        afterKey = false                      // Reset on entry to new heirarchy level
        isKey = true                          // First text in level is always a key (not value)
        if (result !== "") {                  // Don't add new line to the beginning of the Yaml
          result += "\n" + indent(level)
        }
        level++                               // Increase indentation level on new heirarchy level
        break
      case "}":
        level--                               // Decrease indentation level on closing heirarchy level
        afterKey = false
        if (prev !== "}") {                   // Don't add excessive blank lines upon leaving a block
          result += "\n"
        }
        break
      case ",":
        isKey = !isKey
        if (isKey || prev === "}" && level < 2) {
          result += "\n"                      // Add new line for new Key or beginning of new root level block
        }
        result += indent(level - 1)
        break
      case ":":
        isKey = !isKey                        // : signifies end of key
        result += ":"
        afterKey = true
        break
      case "0":
      case "1":
      case "2":
      case "3":
      case "4":
      case "5":
      case "6":
      case "7":
      case "8":
      case "9":
        if (afterKey) {                       // Number after key uses space after : for pretty output
          result += "\ " + c
          afterKey = false
        } else {
          result += c
        }
        break
      case "\"":
        if (afterKey && !isKey) {             // String after key uses space after : for pretty output
          result += "\ "
          afterKey = false
        }
        break
      default:
        result += c
        break
    }
    prev = c
  }
  return(result)
}

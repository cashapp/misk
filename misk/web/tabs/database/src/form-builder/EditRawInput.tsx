/** @jsx jsx */
import { TextArea } from "@blueprintjs/core"
import { css, jsx } from "@emotion/core"
import { cssWrapTextArea } from "src/components"
import { handler } from "."

export const EditRawInput = (props: {
  children: any
  isOpen: boolean
  rawInput: string
  setRawInput: (value: any) => void
}) => {
  const { children, isOpen, rawInput, setRawInput } = props
  if (isOpen) {
    return (
      <TextArea
        css={css(cssWrapTextArea)}
        defaultValue={rawInput}
        fill={true}
        growVertically={true}
        onBlur={handler.handle(setRawInput)}
      />
    )
  } else {
    return children
  }
}

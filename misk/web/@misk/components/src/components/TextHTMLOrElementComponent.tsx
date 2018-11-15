import * as React from "react"

/**
 * <TextHTMLOrElementComponent content={<div>item</div>} length={35} />
 * <TextHTMLOrElementComponent content={"Test"} length={35} />
 * <TextHTMLOrElementComponent content={'<a href="http://squareup.com/">Test</a>'} length={35} />
 *
 * Renders passed in text, HTML-parseable string, or React element with optional length constraints for text.
 */

export interface ITextHTMLOrElementProps {
  content: string | Element | JSX.Element
}

export const TextHTMLOrElementComponent = (
  props: ITextHTMLOrElementProps
): JSX.Element => {
  const { content }: any = props
  let FormattedContent: any = content

  if (typeof content === "string" && !content.startsWith("<")) {
    FormattedContent = <span>{content}</span>
  } else if (typeof content === "string" && content.startsWith("<")) {
    FormattedContent = <span dangerouslySetInnerHTML={{ __html: content }} />
  }
  if (FormattedContent == null) {
    FormattedContent = <span />
  }
  return FormattedContent
}

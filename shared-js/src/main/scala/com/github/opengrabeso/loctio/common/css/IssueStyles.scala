package com.github.opengrabeso.loctio.common.css

import io.udash.css.CssBase

import scala.language.postfixOps

object IssueStyles extends CssBase  {
  import dsl._

  val articleMarkdown = style(
    // inspired by GitHub css
    unsafeRoot(".article-content") (
      unsafeChild("blockquote") (
        padding(`0`, 1 em),
        color(c"#6a737d"),
        borderLeft(.25 em, solid, c"#dfe2e5")
      ),
      unsafeChild("pre") (
        backgroundColor(rgba(27,31,35,.05))
      ),
      unsafeChild("pre code, pre tt") (
        color(c"#000000"),
        padding.`0`,
        margin.`0`,
        overflow.auto,
        fontSize(85 %%),
        lineHeight(1.45),
        backgroundColor.initial,
        borderRadius(3 px),
        border.`0`
      ),
      unsafeChild("pre>code") (
        fontSize(100 %%),
        wordBreak.normal,
        whiteSpace.pre,
        backgroundColor.transparent
      ),
      unsafeChild("code") (
        color(c"#000000"),
        margin.`0`,
        padding(0.2 em, 0.4 em),
        backgroundColor(rgba(27,31,35,.05)),
        fontSize(85 %%),
        borderRadius(3 px)
      ),
      unsafeChild("table") (
        unsafeChild("td, th") (
          padding(6 px, 13 px),
          border(1 px, solid, c"#dfe2e5"),
        ),
      ),
      unsafeChild(".lh-condensed") (
        lineHeight(1.25).important
      ),
      unsafeChild(".bg-gray-light") (
        backgroundColor(c"#fafbfc").important
      ),
      unsafeChild(".js-file-line-container") (
        unsafeChild(".tab-size[data-tab-size=\"8\"]") (
          tabSize := "8"
        )
      ),
      unsafeChild(".js-line-number") (
      ),
      unsafeChild(".blob-num") (
        width(1 %%),
        minWidth(50 px),
        textAlign.right,
        whiteSpace.nowrap,
        verticalAlign.top,
        fontFamily :=! "SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace",
        fontSize(12 px),
        lineHeight(20 px)
      ),
      unsafeChild(".blob-num:before") (
        content :=! "attr(data-line-number)"
      ),

      unsafeChild(".blob-code-inner") (
        fontFamily :=! "SFMono-Regular, Consolas, Liberation Mono, Menlo, monospace",
        fontSize(12 px),
        color(c"#24292e"),
        wordWrap.normal,
        whiteSpace.pre
      ),
      unsafeChild(".js-file-line") (
      ),
      unsafeChild(".pl-k") (
        color(c"#d73a49")
      ),
      unsafeChild(".pl-en") (
        color(c"#6f42c1")
      ),
      unsafeChild(".pl-pds, .pl-s, .pl-s .pl-pse .pl-s1, .pl-sr, .pl-sr .pl-cce, .pl-sr .pl-sra, .pl-sr .pl-sre") (
        color(c"#032f62")
      ),
      unsafeChild(".pl-3, .px-3") (
        paddingLeft(16 px).important
      )
    )
  )


}

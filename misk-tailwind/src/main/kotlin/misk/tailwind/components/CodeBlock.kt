package misk.tailwind.components

import kotlinx.html.TagConsumer
import kotlinx.html.code
import kotlinx.html.pre

fun TagConsumer<*>.CodeBlock(text: String) {
  pre("bg-gray-100 p-4 overflow-x-scroll rounded-lg") { code("text-wrap font-mono") { +text } }
}

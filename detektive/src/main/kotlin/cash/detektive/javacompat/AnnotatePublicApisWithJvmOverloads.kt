package cash.detektive.javacompat

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.internal.AutoCorrectable
import dev.detekt.api.modifiedText
import kotlin.reflect.KClass
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

@AutoCorrectable(since = "1.0.0")
class AnnotatePublicApisWithJvmOverloads(config: Config) :
  Rule(config, "Public functions and constructors with default arguments should be annotated with @JvmOverloads"),
  RequiresAnalysisApi {
  private val corrections = mutableListOf<Pair<Int, ElementType>>()

  override fun preVisit(root: KtFile) {
    corrections.clear()
  }

  override fun postVisit(root: KtFile) {
    if (corrections.isEmpty()) return

    val source = root.modifiedText ?: root.text
    // Detekt's Analysis API files are read-only; its file modifier writes modifiedText after analysis.
    val corrected = StringBuilder(source)
    corrections.sortByDescending { it.first }
    for ((offset, elementType) in corrections) {
      val annotation =
        when (elementType) {
          ElementType.CONSTRUCTOR -> if (source[offset] == '(') " @JvmOverloads constructor" else "@JvmOverloads "
          ElementType.FUNCTION -> {
            val lineStart = source.lastIndexOf('\n', offset - 1) + 1
            if ((lineStart until offset).all { source[it] == ' ' || source[it] == '\t' }) {
              buildString {
                append("@JvmOverloads\n")
                append(source, lineStart, offset)
              }
            } else {
              "@JvmOverloads "
            }
          }
        }
      corrected.insert(offset, annotation)
    }
    root.modifiedText = corrected.toString()
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    checkElement(function, ElementType.FUNCTION)
    super.visitNamedFunction(function)
  }

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    checkElement(constructor, ElementType.CONSTRUCTOR)
    super.visitPrimaryConstructor(constructor)
  }

  private fun checkElement(element: KtFunction, elementType: ElementType) {
    if (!isApplicable(element) || element.annotationEntries.any { it.isOfType(JvmOverloads::class) }) return
    if (element.isSuppressed(ruleName.value)) return

    val message =
      "Public ${elementType.name.lowercase()} '${element.nameAsSafeName}' " +
        "with default arguments, but without @JvmOverloads annotation"

    if (autoCorrect) {
      corrections += element.textRange.startOffset to elementType
      report(Finding(Entity.atName(element), message, suppressReasons = listOf("Auto correct")))
    } else {
      report(Finding(Entity.atName(element), message))
    }
  }

  private fun isApplicable(element: KtFunction): Boolean {
    val containingType = element.containingClassOrObject
    if (containingType !is KtClass || containingType.isInterface() || containingType.isAnnotation()) return false
    if (element.valueParameters.none { it.hasDefaultValue() }) return false

    return analyze(element) {
      val symbol = element.symbol
      if (symbol.annotations.classIds.any { it in INJECT_ANNOTATIONS }) return@analyze false
      if (
        symbol.visibility != KaSymbolVisibility.PUBLIC &&
          !(symbol.visibility == KaSymbolVisibility.INTERNAL && PUBLISHED_API in symbol.annotations)
      )
        return@analyze false

      element.parents.filterIsInstance<KtClassOrObject>().all {
        it.classSymbol?.visibility == KaSymbolVisibility.PUBLIC
      }
    }
  }

  private enum class ElementType {
    FUNCTION,
    CONSTRUCTOR,
  }

  private companion object {
    val PUBLISHED_API = ClassId.fromString("kotlin/PublishedApi")
    val INJECT_ANNOTATIONS =
      setOf(
        ClassId.fromString("javax/inject/Inject"),
        ClassId.fromString("jakarta/inject/Inject"),
        ClassId.fromString("com/google/inject/Inject"),
      )
  }
}

private fun KtElement.isSuppressed(ruleName: String): Boolean {
  var annotated: KtAnnotated? = this as? KtAnnotated ?: getStrictParentOfType<KtAnnotated>()
  while (annotated != null) {
    for (annotation in annotated.annotationEntries) {
      if (annotation.typeReference?.text != "Suppress" && annotation.typeReference?.text != "SuppressWarnings") {
        continue
      }
      for (argument in annotation.valueArguments) {
        val value = argument.getArgumentExpression()?.text?.trim('"') ?: continue
        val id =
          if (value.startsWith("detekt:", ignoreCase = true) || value.startsWith("detekt.", ignoreCase = true)) {
            value.substring(7)
          } else {
            value
          }
        if (
          id == ruleName ||
            id == "detektive" ||
            id == "detektive.$ruleName" ||
            id == "detektive:$ruleName" ||
            id.equals("all", ignoreCase = true)
        )
          return true
      }
    }
    annotated = (annotated as KtElement).getStrictParentOfType<KtAnnotated>()
  }
  return false
}

private fun KtAnnotationEntry.isOfType(annotation: KClass<out Annotation>) =
  shortName?.identifier == annotation.simpleName

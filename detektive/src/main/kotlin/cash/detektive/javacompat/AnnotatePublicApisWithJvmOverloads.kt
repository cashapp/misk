package cash.detektive.javacompat

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.CorrectableCodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.AutoCorrectable
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.api.internal.isSuppressedBy
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import kotlin.reflect.KClass
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isPublishedApi

@RequiresTypeResolution
@AutoCorrectable(since = "1.0.0")
class AnnotatePublicApisWithJvmOverloads(config: Config) : Rule(config) {

  override val issue =
    Issue(
      javaClass.simpleName,
      Severity.Defect,
      "Public functions and constructors with default arguments should be annotated with @JvmOverloads",
      Debt.FIVE_MINS,
    )

  override fun visitNamedFunction(function: KtNamedFunction) {
    checkElement(function, ElementType.FUNCTION)
    super.visitNamedFunction(function)
  }

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    checkElement(constructor, ElementType.CONSTRUCTOR)
    super.visitPrimaryConstructor(constructor)
  }

  private fun checkElement(element: KtFunction, elementType: ElementType) {
    if (bindingContext == BindingContext.EMPTY) {
      return
    }
    if (isApplicable(element, bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element])) {
      if (!element.annotationEntries.any { it.isOfType(JvmOverloads::class) }) {
        val message =
          "Public ${elementType.name.lowercase()} '${element.nameAsSafeName}' " +
            "with default arguments, but without @JvmOverloads annotation"

        if (autoCorrect) {
          if (!(element as KtElement).isSuppressedBy(ruleId, aliases, ruleSetConfig.parentPath)) {
            val annotation =
              element.addAnnotationEntry(
                KtPsiFactory.contextual(element.parent, markGenerated = true).createAnnotationEntry("@JvmOverloads")
              )
            if (elementType == ElementType.CONSTRUCTOR) {
              annotation.addBefore(
                KtPsiFactory.contextual(element.parent, markGenerated = true).createWhiteSpace(),
                null,
              )
              element.addAfter(KtPsiFactory.contextual(element.parent, markGenerated = true).createWhiteSpace(), null)
            } else if (elementType == ElementType.FUNCTION) {
              annotation.addBefore(KtPsiFactory.contextual(element.parent, markGenerated = true).createNewLine(), null)
            }
          }
          report(CorrectableCodeSmell(issue, Entity.atName(element), message, autoCorrectEnabled = true))
        } else {
          report(CodeSmell(issue, Entity.atName(element), message))
        }
      }
    }
  }

  private fun isApplicable(element: KtFunction, descriptor: DeclarationDescriptor?): Boolean {
    // Is inside a class and not an interface, or annotation
    val containingType = element.containingClassOrObject
    if (containingType !is KtClass || containingType.isInterface() || containingType.isAnnotation()) return false

    // Has any parameters with default values
    if (!element.valueParameters.any { it.hasDefaultValue() }) return false

    // Is not annotated with @Inject
    if (element.hasAnyAnnotation("javax.inject.Inject", "jakarta.inject.Inject", "com.google.inject.Inject"))
      return false

    // Is public
    val callableMemberDescriptor = descriptor as? CallableMemberDescriptor
    val visibility = callableMemberDescriptor?.effectiveVisibility()?.toVisibility()
    return Visibilities.Public == visibility ||
      (visibility == Visibilities.Internal && callableMemberDescriptor.isPublishedApi())
  }

  private fun KtAnnotated.hasAnyAnnotation(vararg annotationFqNames: String): Boolean {
    return annotationEntries
      .asSequence()
      .mapNotNull { it.typeReference }
      .mapNotNull { bindingContext[BindingContext.TYPE, it] }
      .any { annotationFqNames.toList().contains(it.fqNameOrNull()?.toString()) }
  }

  enum class ElementType {
    FUNCTION,
    CONSTRUCTOR,
  }
}

private fun KtAnnotationEntry.isOfType(annotation: KClass<out Annotation>) =
  shortName?.identifier == annotation.simpleName

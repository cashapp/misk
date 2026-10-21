package cash.detektive

import cash.detektive.javacompat.AnnotatePublicApisWithJvmOverloads
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class DetektiveRuleSetProvider : RuleSetProvider {

  override val ruleSetId = RuleSetId("detektive")

  override fun instance() = RuleSet(ruleSetId, listOf(::AnnotatePublicApisWithJvmOverloads))
}

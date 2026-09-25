package cash.detektive

import cash.detektive.javacompat.AnnotatePublicApisWithJvmOverloads
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class DetektiveRuleSetProvider : RuleSetProvider {

  override val ruleSetId: String = "detektive"

  override fun instance(config: Config): RuleSet {
    return RuleSet(ruleSetId, listOf(AnnotatePublicApisWithJvmOverloads(config)))
  }
}

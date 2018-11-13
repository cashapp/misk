package misk.hibernate

/**
 * MiskSchemaValidator compares two [MiskSchema]s and delegates errors to the implementation of the
 * [MiskSchemaErrors] provided. The validate method should handle errors and models Kotlin's precondition
 * paradig; for example, [check].
 *
 * MiskSchemaParentValidator is helpful for comparing [MiskSchemaParent] by providing a method for
 * splitting the children of the given [MiskSchema]s into their Intersection and the children unique
 * to each base on the normalized name of each child and finding potential duplicates (these
 * duplicates can exists since we are enforcing lower_snake_case).
 */

interface MiskSchemaValidator<T : MiskSchema> {
  /**
   * Compares dbSchema against ormSchema and uses [MiskSchemaErrors.validate] to evaluate potential
   * schema incompatibilities.
   */
  fun process(errors: MiskSchemaErrors, dbSchema: T, ormSchema: T)
}

interface MiskSchemaErrors {

  /**
   * Gives a MiskSchemaErrors instance to be used by child and use the nested structure of the schemas
   * to have create error messages.
   */
  fun newChildSchemaErrors(): MiskSchemaErrors

  /**
   * When expression is false should create an error using lambda().
   * lambda should return and error message corresponding to the case when expression is false.
   */
  fun validate(expression: Boolean, lambda: () -> String)

  /**
   * Adds a message that may be useful for the developer to debug. Should no register an error.
   */
  fun info(message: String)
}

abstract class MiskSchemaParentValidator<C : MiskSchema, P : MiskSchemaParent<C>> :
    MiskSchemaValidator<P> {
  fun splitChildren(
    errors: MiskSchemaErrors,
    firstSchema: P,
    secondSchema: P
  ): Triple<List<C>, List<C>, List<Pair<C, C>>> {
    // Look for duplicate identifiers.
    val duplicateFirstChildren =
        firstSchema.children
            .asSequence()
            .groupBy { it.normalizedName }
            .filter { it.value.size > 1 }

    errors.validate(duplicateFirstChildren.isEmpty()) {
      val duplicatesList =
          duplicateFirstChildren.map { duplicates -> duplicates.value.map { it.name } }
      "\"${firstSchema.name}\" has duplicate identifiers $duplicatesList"
    }

    val duplicateSecondChildren =
        secondSchema.children
            .asSequence()
            .groupBy { it.normalizedName }
            .filter { it.value.size > 1 }

    errors.validate(duplicateSecondChildren.isEmpty()) {
      val duplicatesList =
          duplicateSecondChildren.map { duplicates -> duplicates.value.map { it.name } }
      "\"${secondSchema.name}\" has duplicate identifiers $duplicatesList"
    }

    // Continue to compare those that are unique.
    val uniqueFirstChildren = firstSchema.children - duplicateFirstChildren.flatMap { it.value }
    val uniqueSecondChildren = secondSchema.children - duplicateSecondChildren.flatMap { it.value }

    // Find all children missing in the secondSchema.
    val uniqueSecondNames = uniqueSecondChildren.map { it.normalizedName }
    val (firstIntersection, firstOnly) =
        uniqueFirstChildren.partition {
          it.normalizedName in uniqueSecondNames
        }

    // Find all children missing in the firstSchema.
    val uniqueFirstNames = uniqueFirstChildren.map { it.normalizedName }
    val (secondIntersection, secondOnly) =
        uniqueSecondChildren.partition { it.normalizedName in uniqueFirstNames }

    // Sort the common children and pair them off in this order
    val intersectionPairs = firstIntersection.sortedBy { it.normalizedName }
        .zip(secondIntersection.sortedBy { it.normalizedName })

    return Triple(firstOnly, secondOnly, intersectionPairs)
  }
}

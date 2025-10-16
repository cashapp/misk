package misk.mcp.util

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Resolves the type argument at [argumentIndex] of [targetSuper] as used by [concreteClass].
 */
internal fun resolveTypeArgument(
  concreteClass: KClass<*>,
  targetSuper: KClass<*>,
  argumentIndex: Int
): KType {
  val visited = mutableSetOf<KClass<*>>()

  // BFS/DFS node: current class and mapping of that class's typeParameters -> resolved KType
  data class Node(val klass: KClass<*>, val mapping: Map<KTypeParameter, KType>)

  val stack = ArrayDeque<Node>()
  stack.add(Node(concreteClass, emptyMap()))

  while (stack.isNotEmpty()) {
    val (current, mapping) = stack.removeFirst()
    if (!visited.add(current)) continue

    for (supertype in current.supertypes) {
      val superClass = supertype.classifier as? KClass<*> ?: continue

      // For each type parameter of the superClass, compute what actual KType it's bound to on this edge.
      val params = superClass.typeParameters
      val args = supertype.arguments.map { it.type } // may contain nulls for star projection

      if (params.size != args.size) {
        // defensive: mismatched arity (shouldn't normally happen)
        continue
      }

      // Substitute any type-parameters appearing in the edge's argument types using current mapping.
      val substitutedArgs = params.indices.map { idx ->
        val argType = args[idx] ?: error("Star projection not supported here: $supertype")
        substituteTypeParameters(argType, mapping)
      }

      // Build the new mapping for superClass: superClass.typeParameter -> substitutedArg
      val newMapping = params.zip(substitutedArgs).toMap()

      // If we've reached the target superclass, return the requested argument (after substitution)
      if (superClass == targetSuper) {
        val result = newMapping.values.elementAtOrNull(argumentIndex)
          ?: error("No type argument at index $argumentIndex for $superClass from $concreteClass")

        return result
      }

      // Otherwise continue traversal upward from superClass with the new mapping.
      stack.add(Node(superClass, newMapping))
    }
  }

  error("Could not find supertype $targetSuper for ${concreteClass.simpleName}")
}

/**
 * Substitute KTypeParameters inside [type] using mapping; reconstruct parameterized KTypes where needed. 
 */
private fun substituteTypeParameters(type: KType, mapping: Map<KTypeParameter, KType>): KType {
  return when (val classifier = type.classifier) {
    is KTypeParameter -> mapping[classifier] ?: type
    is KClass<*> -> {
      // If there are no arguments, nothing to rebuild.
      if (type.arguments.isEmpty()) return type

      // Recurse for each argument
      val newArgs = type.arguments.map { proj ->
        if (proj.type == null) {
          // star projection
          KTypeProjection.STAR
        } else {
          val substituted = substituteTypeParameters(proj.type!!, mapping)
          // We preserve variance as invariant here because we don't rebind variance info — this is sufficient
          // for resolving concrete inner types; adjust if you need variance-aware behavior.
          KTypeProjection.invariant(substituted)
        }
      }

      // Recreate the parameterized type on the KClass
      classifier.createType(newArgs, type.isMarkedNullable)
    }
    else -> type
  }
}

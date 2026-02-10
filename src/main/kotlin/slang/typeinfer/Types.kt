package slang.typeinfer

/**
 * Type representation for Hindley-Milner type inference.
 *
 * Type variables use a mutable union-find structure: each [TVar] has a nullable
 * [TVar.bound] field. When bound is null the variable is free; when non-null it
 * points to the type it has been unified with.
 */
sealed class SlangType {
    /** A type variable (possibly bound via unification). */
    class TVar(
        val id: Int,
        var bound: SlangType? = null,
    ) : SlangType() {
        override fun toString(): String = if (bound != null) bound!!.toString() else "t$id"
    }

    object TNum : SlangType() {
        override fun toString() = "Num"
    }

    object TBool : SlangType() {
        override fun toString() = "Bool"
    }

    object TString : SlangType() {
        override fun toString() = "String"
    }

    object TNone : SlangType() {
        override fun toString() = "None"
    }

    object TUnit : SlangType() {
        override fun toString() = "Unit"
    }

    /** Function type: (param1, param2, ...) -> ret */
    data class TFun(
        val params: List<SlangType>,
        val ret: SlangType,
    ) : SlangType() {
        override fun toString() = "(${params.joinToString(", ")}) -> $ret"
    }

    data class TArray(
        val elem: SlangType,
    ) : SlangType() {
        override fun toString() = "[$elem]"
    }

    data class TRecord(
        val fields: Map<String, SlangType>,
    ) : SlangType() {
        override fun toString() = "{${fields.entries.joinToString(", ") { "${it.key}: ${it.value}" }}}"
    }

    data class TRef(
        val inner: SlangType,
    ) : SlangType() {
        override fun toString() = "Ref<$inner>"
    }
}

/** A polymorphic type scheme: ∀ vars . type */
data class TypeScheme(
    val vars: Set<Int>,
    val type: SlangType,
)

/** Resolve a chain of TVar bindings to the root representative type. */
fun prune(t: SlangType): SlangType =
    when {
        t is SlangType.TVar && t.bound != null -> {
            val pruned = prune(t.bound!!)
            t.bound = pruned // path compression
            pruned
        }
        else -> t
    }

/** Collect free type-variable ids in a type. */
fun freeVars(t: SlangType): Set<Int> =
    when (val p = prune(t)) {
        is SlangType.TVar -> setOf(p.id)
        is SlangType.TFun -> p.params.flatMap { freeVars(it) }.toSet() + freeVars(p.ret)
        is SlangType.TArray -> freeVars(p.elem)
        is SlangType.TRecord ->
            p.fields.values
                .flatMap { freeVars(it) }
                .toSet()
        is SlangType.TRef -> freeVars(p.inner)
        else -> emptySet()
    }

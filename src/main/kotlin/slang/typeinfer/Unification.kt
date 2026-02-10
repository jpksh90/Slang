package slang.typeinfer

import slang.common.CodeInfo

/**
 * Unification errors thrown when two types cannot be made equal.
 */
class TypeError(
    val location: CodeInfo,
    message: String,
) : Exception(message)

/**
 * Unifies two types, mutating [SlangType.TVar.bound] fields.
 * Throws [TypeError] if the types are incompatible.
 */
fun unify(
    a: SlangType,
    b: SlangType,
    location: CodeInfo = CodeInfo.generic,
) {
    val pa = prune(a)
    val pb = prune(b)

    if (pa === pb) return

    when {
        pa is SlangType.TVar -> {
            if (occursIn(pa.id, pb)) {
                throw TypeError(location, "Infinite type: t${pa.id} occurs in $pb")
            }
            pa.bound = pb
        }
        pb is SlangType.TVar -> unify(pb, pa, location)

        pa is SlangType.TFun && pb is SlangType.TFun -> {
            if (pa.params.size != pb.params.size) {
                throw TypeError(
                    location,
                    "Function arity mismatch: expected ${pa.params.size} params, got ${pb.params.size}",
                )
            }
            pa.params.zip(pb.params).forEach { (p1, p2) -> unify(p1, p2, location) }
            unify(pa.ret, pb.ret, location)
        }

        pa is SlangType.TArray && pb is SlangType.TArray ->
            unify(pa.elem, pb.elem, location)

        pa is SlangType.TRef && pb is SlangType.TRef ->
            unify(pa.inner, pb.inner, location)

        pa is SlangType.TRecord && pb is SlangType.TRecord -> {
            if (pa.fields.keys != pb.fields.keys) {
                throw TypeError(
                    location,
                    "Record field mismatch: ${pa.fields.keys} vs ${pb.fields.keys}",
                )
            }
            for (key in pa.fields.keys) {
                unify(pa.fields[key]!!, pb.fields[key]!!, location)
            }
        }

        // Ground types must be identical
        pa == pb -> { /* ok */ }

        else -> throw TypeError(location, "Cannot unify $pa with $pb")
    }
}

/** Occurs check: does type variable [varId] appear free in [type]? */
private fun occursIn(
    varId: Int,
    type: SlangType,
): Boolean {
    val p = prune(type)
    return when (p) {
        is SlangType.TVar -> p.id == varId
        is SlangType.TFun -> p.params.any { occursIn(varId, it) } || occursIn(varId, p.ret)
        is SlangType.TArray -> occursIn(varId, p.elem)
        is SlangType.TRef -> occursIn(varId, p.inner)
        is SlangType.TRecord -> p.fields.values.any { occursIn(varId, it) }
        else -> false
    }
}

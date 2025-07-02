package slang.typesystem

sealed class SlastType {
    abstract fun lub(other: SlastType) : SlastType
}

data object Bottom : SlastType() {
    override fun lub(other: SlastType): SlastType = other
}

data object IntType : SlastType() {
    override fun lub(other: SlastType): SlastType {
        return when (other) {
            is IntType -> IntType
            is AnyType -> AnyType
            is UnionType -> UnionType(other.type + other)
            else -> UnionType(setOf(other, IntType))
        }
    }
}

data object RealType : SlastType() {
    override fun lub(other: SlastType): SlastType {
        TODO("Not yet implemented")
    }
}

data class UnionType(val type: Set<SlastType>) : SlastType() {
    override fun lub(other: SlastType): SlastType {
        TODO("Not yet implemented")
    }
}

data class FunctionType(val input: List<SlastType>, val output: SlastType) : SlastType() {
    override fun lub(other: SlastType): SlastType {
        TODO("Not yet implemented")
    }
}

data class RecordType(val id: String) : SlastType() {
    override fun lub(other: SlastType): SlastType {
        TODO("Not yet implemented")
    }
}

data object AnyType : SlastType() {
    override fun lub(other: SlastType): SlastType {
        TODO("Not yet implemented")
    }
}

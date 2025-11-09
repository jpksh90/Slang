package slang.common

interface Transform<I, O, E> {
    fun transform(input: I): Result<O, E>
}

infix fun <I, O1, O2, E> Transform<I, O1, E>.then(other: Transform<O1, O2, E>): Transform<I, O2, E> {
    val first = this
    return object : Transform<I, O2, E> {
        override fun transform(input: I): Result<O2, E> {
            val result = first.transform(input)
            return when (result) {
                is Result.Ok -> other.transform(result.value)
                is Result.Err -> result
            }
        }
    }
}

operator fun <I, O, E> Transform<I, O, E>.invoke(input: I): Result<O, E> = transform(input)

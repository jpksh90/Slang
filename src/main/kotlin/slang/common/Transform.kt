package slang.common

interface Transform<I, O> {
    fun transform(input: I): Result<O, List<*>>
}

infix fun <I, O1, O2> Transform<I, O1>.then(other: Transform<O1, O2>): Transform<I, O2> {
    val first = this
    return object : Transform<I, O2> {
        override fun transform(input: I): Result<O2, List<*>> {
            val result = first.transform(input)
            return when (result) {
                is Result.Ok -> other.transform(result.value)
                is Result.Err -> result
            }
        }
    }
}

operator fun <I, O> Transform<I, O>.invoke(input: I): Result<O, List<*>> = transform(input)

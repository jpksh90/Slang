package slang.common

sealed class Result<out T, out E> {
    data class Ok<out T>(
        val value: T,
    ) : Result<T, Nothing>()

    data class Err<out E>(
        val error: E,
    ) : Result<Nothing, E>()

    companion object {
        fun <T> ok(value: T): Result<T, Nothing> = Ok(value)

        fun <E> err(error: E): Result<Nothing, E> = Err(error)
    }

    fun <U> map(f: (T) -> U): Result<U, E> =
        when (this) {
            is Ok -> ok(f(value))
            is Err -> Err(error)
        }

    fun <F> mapError(f: (@UnsafeVariance E) -> F): Result<T, F> =
        when (this) {
            is Ok -> Ok(value)
            is Err -> err(f(error))
        }

    inline fun <U> flatMap(f: (T) -> Result<U, @UnsafeVariance E>): Result<U, E> =
        when (this) {
            is Ok -> f(value)
            is Err -> Err(error)
        }

    fun forEach(f: (T) -> Unit) {
        when (this) {
            is Ok -> f(value)
            is Err -> {}
        }
    }

    fun getOrElse(default: @UnsafeVariance T): T =
        when (this) {
            is Ok -> value
            is Err -> default
        }

    fun getOrNull(): T? =
        when (this) {
            is Ok -> value
            is Err -> null
        }

    val isOk: Boolean
        get() = this is Ok

    val isErr: Boolean
        get() = this is Err

    fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (@UnsafeVariance E) -> R,
    ): R =
        when (this) {
            is Ok -> onSuccess(value)
            is Err -> onFailure(error)
        }

    override fun toString(): String =
        when (this) {
            is Ok -> "Ok($value)"
            is Err -> "Err($error)"
        }
}

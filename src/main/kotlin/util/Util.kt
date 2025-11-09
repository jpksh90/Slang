package util

import slang.parser.CompilerError

fun interface Transform<In, Out> {
    fun transform(input: In): Result<Out, List<CompilerError>>
}

sealed class Result<out T, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>()
    data class Err<E>(val error: E) : Result<Nothing, E>()

    val isOk: Boolean get() = this is Ok<T>
    val isErr: Boolean get() = this is Err<E>

    fun <U> map(f: (T) -> U): Result<U, E> = when (this) {
        is Ok -> Ok(f(this.value))
        is Err -> this
    }

    fun <F> mapError(f: (E) -> F): Result<T, F> = when (this) {
        is Ok -> this
        is Err -> Err(f(this.error))
    }

    fun getOrElse(default: () -> @UnsafeVariance T): T = when (this) {
        is Ok -> this.value
        is Err -> default()
    }

    fun getOrNull(): T? = when (this) {
        is Ok -> this.value
        is Err -> null
    }

    fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R) : R {
        return when (this) {
            is Ok -> onSuccess(this.value)
            is Err -> onFailure(this.error)
        }
    }


    companion object {
        fun <T> ok(value: T): Result<T, Nothing> = Ok(value)
        fun <E> err(error: E): Result<Nothing, E> = Err(error)
    }
}

// --- Composition helpers for Transform ---

/**
 * Compose two transforms: first apply `this`, if it yields Ok then apply [next], otherwise propagate Err.
 */
fun <A, B, C> Transform<A, B,>.andThen(next: Transform<B, C>): Transform<A, C> = Transform { input ->
    when (val r = this.transform(input)) {
        is Result.Ok -> next.transform(r.value)
        is Result.Err -> Result.Err(r.error)
    }
}

/** Infix alias for [andThen] to read nicely in pipelines. */
infix fun <A, B, C> Transform<A, B>.then(next: Transform<B, C>): Transform<A, C> = this.andThen(next)

/** Allow calling a transform like a function: `t(input)` returns Result. */
operator fun <A, B> Transform<A, B>.invoke(input: A): Result<B, List<CompilerError>> = this.transform(input)


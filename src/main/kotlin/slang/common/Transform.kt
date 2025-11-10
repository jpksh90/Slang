package slang.common

import slang.parser.CompilerError

fun interface Transform<In, Out> {
    fun transform(input: In): Result<Out, List<CompilerError>>
}

fun <A, B, C> Transform<A, B>.andThen(next: Transform<B, C>): Transform<A, C> =
    Transform { input ->
        when (val r = this.transform(input)) {
            is Result.Ok -> next.transform(r.value)
            is Result.Err -> Result.Err(r.error)
        }
    }

/** Infix alias for [andThen] to read nicely in pipelines. */
infix fun <A, B, C> Transform<A, B>.then(next: Transform<B, C>): Transform<A, C> = this.andThen(next)

/** Allow calling a transform like a function: `t(input)` returns Result. */
operator fun <A, B> Transform<A, B>.invoke(input: A): Result<B, List<CompilerError>> = this.transform(input)

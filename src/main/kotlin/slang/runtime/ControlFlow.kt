package slang.runtime

sealed class ControlFlow {
    data class Normal(val value: Value = Value.NoneValue) : ControlFlow()
    data class Return(val value: Value) : ControlFlow()
    object Break : ControlFlow()
    object Continue : ControlFlow()
}


class C {
    companion object {
        fun unaryMinus(): C = C()
    }
}

fun foo() {
    C.<caret>unaryMinus()
}

fun <T> run(f: () -> T): T {
    return f()
}

// valid, A here is effectively Any
fun foo() = run {
    class A
    A()
}

abstract class My

// valid, object literal here is also effectively Any
fun bar() = run {
    object: My() {}
}

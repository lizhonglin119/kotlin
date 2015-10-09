// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: A.java

public class A {
    static void foo() {}
    static void baz(String s) {}
}

// FILE: K.kt

open class K : A() {
    <!ACCIDENTAL_OVERRIDE!>fun foo()<!> {}
    fun foo(i: Int) {}
    fun baz(i: Int) {}

    companion object {
        fun foo() {}
    }
}

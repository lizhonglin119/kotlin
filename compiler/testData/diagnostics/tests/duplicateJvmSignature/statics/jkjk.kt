// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: A.java

public class A {
    static void foo() {}
    static void baz(String s) {}
}

// FILE: B.kt

open class B : A() {
}

// FILE: C.java

public class C extends B {
    static void bar(int i) {}
}

// FILE: K.kt

open class K : C() {
    <!ACCIDENTAL_OVERRIDE!>fun foo()<!> {}
    fun foo(a: Any) {}
    <!ACCIDENTAL_OVERRIDE!>fun bar(i: Int)<!> {}
    fun bar(i: String) {}
    fun baz(i: Int) {}

    companion object {
        fun foo() {}
        fun bar(i: Int) {}
    }
}

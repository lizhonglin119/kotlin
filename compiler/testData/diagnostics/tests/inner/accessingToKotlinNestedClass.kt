// !DIAGNOSTICS: -UNUSED_VARIABLE

// FILE: A.kt

open class A {
    class NC {}
    inner class IC {}
    interface NI {}
}

// FILE: I.kt

interface I {
    class NC {}
    interface NI {}
}

// FILE: B.kt

class B : A() {

}

// FILE: C.kt

class C : I {

}

// FILE: D.kt

class D : A(), I {

}

// FILE: test.kt

fun test() {
    val ac: A.NC = A.NC()
    val aic: A.IC = A().IC()
    val ai: A.NI? = null

    val ic: I.NC = I.NC()
    val ii: I.NI? = null

    val bc: B.<!UNRESOLVED_REFERENCE!>NC<!> = B.<!UNRESOLVED_REFERENCE!>NC<!>()
    val bic: B.<!UNRESOLVED_REFERENCE!>IC<!> = B().<!UNRESOLVED_REFERENCE!>IC<!>()
    val bi: B.<!UNRESOLVED_REFERENCE!>NI<!>? = null

    val cc: C.<!UNRESOLVED_REFERENCE!>NC<!> = C.<!UNRESOLVED_REFERENCE!>NC<!>()
    val cic: C.<!UNRESOLVED_REFERENCE!>IC<!> = C().<!UNRESOLVED_REFERENCE!>IC<!>()
    val ci: C.<!UNRESOLVED_REFERENCE!>NI<!>? = null

    val dc: D.<!UNRESOLVED_REFERENCE!>NC<!> = D.<!UNRESOLVED_REFERENCE!>NC<!>()
    val dic: D.<!UNRESOLVED_REFERENCE!>IC<!> = D().<!UNRESOLVED_REFERENCE!>IC<!>()
    val di: D.<!UNRESOLVED_REFERENCE!>NI<!>? = null
}

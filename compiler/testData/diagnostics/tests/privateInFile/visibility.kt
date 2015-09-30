// !DIAGNOSTICS: -UNUSED_VARIABLE

//FILE:file1.kt
package a

private open class A {
    fun bar() {}
}

private var x: Int = 10

var xx: Int = 20
  private set(value: Int) {}

private fun foo() {}

private fun bar() {
    val y = x
    x = 20
    xx = 30
}

fun makeA() = A()

private object PO {}

//FILE:file2.kt
package a

fun test() {
    val y = makeA()
    y.<!INVISIBLE_MEMBER!>bar<!>()
    <!INVISIBLE_MEMBER!>foo<!>()

    val u : <!INVISIBLE_REFERENCE!>A<!> = <!INVISIBLE_MEMBER!>A<!>()

    val z = <!INVISIBLE_MEMBER!>x<!>
    <!INVISIBLE_MEMBER!>x<!> = 30

    val po = <!INVISIBLE_MEMBER!>PO<!>

    val v = xx
    <!INVISIBLE_SETTER!>xx<!> = 40
}

class B : <!INVISIBLE_REFERENCE, INVISIBLE_MEMBER!>A<!>() {}

class Q {
    class W {
        fun foo() {
            val y = makeA() //assure that 'makeA' is visible
        }
    }
}

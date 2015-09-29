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
    y.<!INVISIBLE_FILE_MEMBER!>bar<!>()
    <!INVISIBLE_FILE_MEMBER!>foo<!>()

    val u : <!INVISIBLE_FILE_MEMBER!>A<!> = <!INVISIBLE_FILE_MEMBER!>A<!>()

    val z = <!INVISIBLE_FILE_MEMBER!>x<!>
    <!INVISIBLE_FILE_MEMBER!>x<!> = 30

    val po = <!INVISIBLE_FILE_MEMBER!>PO<!>

    val v = xx
    <!INVISIBLE_FILE_MEMBER!>xx<!> = 40
}

class B : <!INVISIBLE_FILE_MEMBER, INVISIBLE_FILE_MEMBER!>A<!>() {}

class Q {
    class W {
        fun foo() {
            val y = makeA() //assure that 'makeA' is visible
        }
    }
}

private interface My

private open class Base

public interface Your: <!EXPOSED_SUPER_INTERFACE!>My<!> {
    fun <T: Base> foo(): T
}

public class Derived<<!EXPOSED_TYPE_PARAMETER_BOUND!>T: My<!>>(val x: My): <!EXPOSED_SUPER_CLASS!>Base()<!> {

    <!EXPOSED_PROPERTY_TYPE!>val y: Base? = null<!>

    val <!EXPOSED_RECEIVER_TYPE!>My<!>.z: Int
        get() = 42

    fun <!EXPOSED_FUNCTION_RETURN_TYPE!>foo<!>(<!EXPOSED_PARAMETER_TYPE!>m: My<!>): My = m

    fun <!EXPOSED_RECEIVER_TYPE!>My<!>.<!EXPOSED_FUNCTION_RETURN_TYPE!>bar<!>(): My = this
}



internal open class My

// valid, internal from internal
internal open class Your: My() {
    // valid, effectively internal
    fun foo() = My()
}

// error, public from internal
class His: <!EXPOSED_SUPER_CLASS!>Your()<!> {
    // error, public from internal
    <!EXPOSED_PROPERTY_TYPE!>val x = My()<!>
    // valid, private from internal
    private fun bar() = My()     
}
// No supertype at all
class A1 {
    fun test() {
        <!SUPER_CANT_BE_EXTENSION_RECEIVER!>super<!>.<!DEPRECATION!>identityEquals<!>(null) // Call to an extension function
    }
}

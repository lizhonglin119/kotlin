package test

internal val noMangling = 1;

class Z {
    internal var mangled = 1;
}

fun box(): String {
    val clazz = Z::class.java
    val declaredField = clazz.declaredFields

    val mangled = declaredField.firstOrNull {
        it.name.startsWith("mangled$")
    }

    if (mangled == null) return "Class internal backing field should exist"
    if (!mangled.isSynthetic) return "Class internal backing field should be synthetic"

    val topLevel = Class.forName("test.FieldKt").getDeclaredField("noMangling")
    if (topLevel == null) return "Top level internal backing field should exist"
    if (!topLevel.isSynthetic) return "Top level internal backing field should be synthetic"

    return "OK"
}
// Example from KT-6822
// type of a: (Int?) -> Int? but must be (Int?) -> Int
val a: Int = l@ { it: Int? ->
    if (it != null) return@l it // no smart cast 
    5
}
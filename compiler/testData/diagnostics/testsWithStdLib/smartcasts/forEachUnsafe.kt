// KT-7186: True "Type mismatch" error

fun indexOfMax(a: IntArray): Int? {
    var maxI: Int?
    maxI = 0
    a.forEachIndexed { i, value ->
        if (value >= a[<!SMARTCAST_IMPOSSIBLE!>maxI<!>]) {
            maxI = i
        }
        else if (value < 0) {
            maxI = null
        }
    }
    return maxI
}
// !DIAGNOSTICS: -NOTHING_TO_INLINE

inline fun <T> foo(t1: T, t2: T) = t1 ?: t2

inline fun <T> bar(<!UNUSED_PARAMETER!>l<!>: (T) -> Unit): T = null!!

fun use() {
    var x: Int?
    x = 5
    <!DEBUG_INFO_SMARTCAST!>x<!>.hashCode()
    foo(bar { x = null }, <!SMARTCAST_IMPOSSIBLE!>x<!>)
}

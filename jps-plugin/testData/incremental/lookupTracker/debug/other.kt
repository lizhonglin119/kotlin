package foo.bar

/*p:foo.bar*/fun testOther(a: /*p:foo.bar*/A) {

    /*p:foo.bar c:foo.bar.A(invoke)*/a()
    /*p:foo.bar c:foo.bar.A(invoke) p:foo.bar(invoke)*/a(1)
}

internal fun noMangling() = 1;

private fun privateFun() = 1;
public fun publicFun() = 1;

class Z {
    internal fun mangled() = 1;

    private fun privateFunInClass() = 1;
    public fun publicFunInClass() = 1;
    protected fun protectedFunInClass() = 1;

}

/*2 cause of old package facade*/
// 2 noMangling\(\)I
// 2 privateFun\(\)I
// 2 publicFun\(\)I

// 1 synthetic mangled\$\w*\(\)I
// 1 privateFunInClass\(\)I
// 1 publicFunInClass\(\)I
// 1 protectedFunInClass\(\)I

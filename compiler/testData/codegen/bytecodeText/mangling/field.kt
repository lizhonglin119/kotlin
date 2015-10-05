private val privateVal = 1;
public val publicVal = 1;

internal val noMangling = 1;

class Z {
    internal var mangled = 1;

    private val privateClassVal = 1;
    public val publicClassVal = 1;
    protected val protectedClassVal = 1;
}


// 1 I noMangling = 1
// 1 I privateVal = 1
// 1 I publicVal = 1

// 1 synthetic I mangled\$
// 1 synthetic getMangled\$
// 1 synthetic setMangled\$
// 1 I privateClassVal
// 1 I publicClassVal
// 1 I protectedClassVal
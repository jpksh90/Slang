fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));

fun apply(f, x) => f(x);

fun fixpoint(f, x) => if (f(x) == x) then x else fixpoint(f, f(x));

fun double(f, x)  => f(f(x));
fun mod(a, b) => if (a < b) then a else mod(a-b, b);

fun gcd(a, b) => if (b == 0) then a else gcd(b, mod(a,b));
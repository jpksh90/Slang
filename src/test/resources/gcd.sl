fun mod(a, b) => ifte(a < b, a, mod(a-b, b));

fun gcd(a, b) => ifte(b == 0, a, gcd(b, mod(a,b)));
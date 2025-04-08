fun mandelbrot(c, maxIter) {
    let z = c;
    let n = 0;
    while (n < maxIter && (z.real * z.real + z.imag * z.imag) <= 4.0) {
        z = z.mul(z).plus(c);
        n = n + 1;
    }
    return n;
}


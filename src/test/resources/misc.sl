fun apply_n(f, n, x) => if (n == 0) then x else f(apply_n(f, n - 1, f(x)));

fun apply(f, x) => f(x);

fun fixpoint(f, x) => if (f(x) == x) then x else fixpoint(f, f(x));

fun double(f, x)  => f(f(x));

fun test() {
  let f = fun (x) => x + 1;
  let x = 0;
  let n = 10;
  let y = apply_n(f, n, x);
  let z = apply(f, x);
  let w = fixpoint(f, x);
  let v = double(f, x);
  print(y);
  print(z);
  print(w);
  print(v);
}

let Complex = {
    real : 0.0,
    img: 0.0,
    plus : fun (other) {
        return { real : this.real + other.real, img : this.img + other.img };
    },
    mul: fun (other) {
        return { real : this.real * other.real - this.img * other.img, img : this.real * other.img + this.img * other.real };
    }
};

fun mandelbrot(c, maxIter) {
    let z = c;
    let n = 0;
    while (n < maxIter && (z.real * z.real + z.imag * z.imag) <= 4.0) {
        z = z.mul(z).plus(c);
        n = n + 1;
    }
    return n;
}
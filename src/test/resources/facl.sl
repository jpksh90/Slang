fun factorial(n) => ifte(n == 0, 1, n * factorial(n - 1));

let num = readInput();
let fact = factorial(num);
print(fact);

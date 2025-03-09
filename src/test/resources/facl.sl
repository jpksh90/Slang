fun factorial(n) => if (n == 0) then 1 else n * factorial(n - 1);

let num = readInput();
let fact = factorial(num);
print(fact);

fun power(base, exp) {
    let result = 1;

    while exp > 0 {
        result = result * base;
        exp = exp - 1;
    }

    return result;
}

let base = readInput();
let exp = readInput();
print(power(base,exp));

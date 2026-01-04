function isPrime(number) {
    let limit = Math.floor(number / 2);
    for (let i = 2; i <= limit; i++) {
        if (number % i === 0) {
            return false;
        }
    }
    return true;
}
const primeNumbers = [];
for (let number = 2; number < 50000; number++) {
    if (isPrime(number)) {
        primeNumbers.push(number);
    }
}
primeNumbers;
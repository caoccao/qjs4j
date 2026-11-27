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
const upperLimit = 50000;
for (let number = 2; number < upperLimit; number++) {
    if (isPrime(number)) {
        primeNumbers.push(number);
    }
}
primeNumbers;

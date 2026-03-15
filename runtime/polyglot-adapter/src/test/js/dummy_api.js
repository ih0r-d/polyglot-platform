const add = (a, b) => a + b;

const ping = () => 123;

globalThis.add = add;
globalThis.ping = ping;
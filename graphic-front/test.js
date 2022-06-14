const { symmetricEncrypt, symmetricDecrypt } = require("./keygen");
const { createCipheriv, createDecipheriv } = require("crypto");
const { stringToBuffer } = require("./string-to-buf");
const { read } = require("./block-read");

const key = Buffer.alloc(32, 0);
key.write("my key", "utf-8");

const text = "Hello, World!";

// let encrypted = symmetricEncrypt(key, stringToBuffer(text));

// console.log(symmetricDecrypt(key, encrypted).toString("utf-8"));

async function main () {
    let cipher = new StreamCipher("a", "my key");
    let decipher = new StreamCipher("a", "my key", 20, false);
    // cipher.digest.pause();
    let input = Readable.from(stringToBuffer(text));
    // let s = Buffer.alloc(0);
    // let output = new Duplex({write:(c)=>{s=Buffer.concat([s,c]);},read:(size)=>{size=size ?? s.length;console.log(size);if(size>=s.length){return null;}const ret=s.slice(0,size);s=s.slice(size);return ret;}});
    let output = createWriteStream("test.txt");
    // output.pause();
    input.pipe(cipher.digest).pipe(decipher.digest).pipe(output);
    // cipher.digest.push(stringToBuffer(text));
    // console.log(output.read(1));
    // let cinput = new Duplex();
    // let coutput = new Duplex();
    // let douput = new Duplex();
    // douput.pause();
    // cinput.write(text);
    // cinput.pipe(cipher.digest).pipe(decipher.digest).pipe(douput);
    // console.log(douput.read());
    // cipher.digest.write(stringToBuffer(text));
    // decipher.digest.pause();
    // decipher.digest.write(cipher.digest.read());
    // console.log(await read(decipher.digest, 1));
    // let cipher = createCipheriv("aes256", key, Buffer.alloc(16, 0));
    // cipher.pause();
    // cipher.write(stringToBuffer(text));
    // let buf = await read(cipher, 13);
    // console.log(Buffer.from(buf).toString());
}

main();
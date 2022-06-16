// const { Transform } = require("stream");
const { hash } = require("./hash");
const { stringToBuffer } = require("./string-to-buf");

class KeyLengthError extends Error {
    /**
     * represents an error caused by an invalid key length
     * @param {String} message - error message
     */
    constructor (message) {
        super(message);
        this.name = "KeyLengthError";
    }
}

/**
 * regenerates symmetric key once exausted
 * @param {Buffer} key key to regenerate
 * @returns {Buffer}
 */
function regen_key (key) {
    //
}

/**
 * encrypts/decrypts data using the given key
 * @param {Buffer} key 32 byte key
 * @param {Buffer} buf data to encrypt/decrypt
 * @returns {Buffer}
 */
function crypt (key, buf) {
    if (key.length !== 32) {
        throw new KeyLengthError(`exoected 32 byte key but got key with ${key.length} instead`);
    }
    buf = Buffer.isBuffer(buf) ? buf : stringToBuffer(buf);
    /**@type {Number[]} */
    let f = [];
    for (let i = 0; i < Math.ceil(buf.length / 32); i ++) {
        for (let j = 0; j < 32; j ++) {
            if (i * 32 + j >= buf.length) break;
            let ubyte = buf[i * 32 + j];
            let obyte = 0;
            for (let k = 0; k < 8; k ++) {
                obyte = obyte | ((ubyte & (1 << k)) ^ (key[j] & (1 << k)));
            }
            f.push(obyte);
        }
        key = regen_key(key);
    }
    return Buffer.from(f);
}

const key = Buffer.alloc(32, 0x88);

const enc = crypt(key, "my stuff");

console.log(enc.toString("utf-8"), crypt(key, enc).toString("utf-8"));

class SymmetricCipher {
    /**
     * creates a symmetric cipher
     * @param {Buffer} key cipher key
     */
    constructor (key) {
        if (!Buffer.isBuffer(key)) {
            throw new TypeError(`expected buffer but got ${key.__proto__.constructor.name} instead`);
        }
        if (key.length !== 32) {
            throw new KeyLengthError(`exoected 32 byte key but got key with ${key.length} instead`);
        }
        this.key = key;
        this.key_position = 0;
        /**
         * @typedef DEF1
         * @description encrypts / decrypts string
         * @type {{(data : String) => Buffer}}
         * @ function
         * @ param {String} data
         * @ returns {Buffer}
         */
        /**
         * @type {{
         * DEF1;
         * (data : Number[]) => Buffer;
         * (data : Buffer) => Buffer;
         * }}
         */
        // this.crypt = (data) => {};
    }
    regen_key () {
        this.key_position = 0;
        this.key = Buffer.from(hash(this.key));
    }
    /**
     * encrypts a string
     * @method
     * @param {String} data data to encrypt
     * @returns {Buffer}
     *//**
    * encrypts a buffer
    * @method
    * @param {Buffer} data data to encrypt
    * @returns {Buffer}
    */
    crypt (data) {}
}

new SymmetricCipher().crypt

// function decrypt (key, buf) {}

// class EncryptStream extends Transform {
//     /**
//      * creates a stream which encrypts data using the given key
//      * @param {Buffer} key key to use for encryption, key MUST be exactly 32 bytes
//      */
//     constructor (key) {
//         if (key.length !== 32) {
//             throw new KeyLengthError(`exoected 32 byte key but got key with ${key.length} instead`);
//         }
//         super();
//         this.key = key;
//     }
// }

exports.SymmetricCipher = SymmetricCipher;
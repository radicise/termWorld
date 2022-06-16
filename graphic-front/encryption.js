const { hash } = require("./hash");
const { stringToBuffer } = require("./string-to-buf");
const { formatBuf } = require("./logging")

class KeyLengthError extends Error {
    /**
     * represents an error caused by an invalid key length
     * @param {string} message - error message
     */
    constructor (message) {
        super(message);
        this.name = "KeyLengthError";
    }
}

class SymmetricCipher {
    /**
     * @param {Buffer} key key to use
     */
    constructor (key) {
        if (!Buffer.isBuffer(key)) throw new TypeError("key must be a buffer");
        if (key.length !== 32) {
            throw new KeyLengthError("key length must be 32 bytes");
        }
        this.key = key;
        this.key_position = 0;
    }
    regenerate_key () {
        this.key_position = 0;
        // console.log("regen key");
        this.key = Buffer.from(hash(this.key));
        // console.log(`new key is ${formatBuf(this.key)}`);
    }
    /**
     * encrypts / decrypts data
     * @param {string | number[] | number | Buffer} data data to encrypt / decrypt
     * @param {boolean} [strIsUtf8]
     * @returns {Buffer}
     */
    crypt (data, strIsUtf8) {
        // console.log("crypt log");
        if (typeof data === "string") {
            data = stringToBuffer(data, strIsUtf8);
        }
        if (typeof data === "number") data = [data];
        if (Array.isArray(data)) {
            data = Buffer.from(data);
        }
        // console.log(data);
        return Buffer.from(data.map(v => {if(this.key_position>=32){this.regenerate_key();}const res = ((v & 0xff) ^ this.key[this.key_position]);this.key_position++;return res;}));
    }
}

exports.SymmetricCipher = SymmetricCipher;
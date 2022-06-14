const { createPrivateKey, createPublicKey, generateKeyPairSync, createCipheriv, createDecipheriv, KeyObject } = require("crypto");
const { stringToBuffer } = require("./string-to-buf");

/**
 * generates a pair of keys for RSA encryption
 * @returns {[KeyObject, KeyObject]}
 */
function generateKeyPair () {
    const KEYPAIR = generateKeyPairSync("rsa", {modulusLength:4096,publicKeyEncoding:{type:"spki",format:"pem"},privateKeyEncoding:{type:"pkcs8",format:"pem",cipher:"aes-256-cbc",passphrase:""}});
    const privateKeyS = KEYPAIR.privateKey;
    let privateKey = createPrivateKey({key:privateKeyS,type:"pkcs1",format:"pem",passphrase:"",encoding:"utf-8"});
    let publicKey = createPublicKey(privateKey);
    return [publicKey, privateKey];
}

/**
 * runs symmetric encryption on data using key
 * @param {Buffer} key symmetric key
 * @param {Buffer} data data to encrypt
 * @returns {Buffer}
 */
function symmetricEncrypt (key, data) {
    let cipher = createCipheriv("aes256", key, Buffer.alloc(16, 0));
    cipher.update(data);
    return cipher.final();
}

/**
 * runs symmetric decryption on data using key
 * @param {Buffer} key symmetric key
 * @param {Buffer} data data to decrypt
 * @returns {Buffer}
 */
 function symmetricDecrypt (key, data) {
    let cipher = createDecipheriv("aes256", key, Buffer.alloc(16, 0));
    cipher.update(data);
    return cipher.final();
}

exports.generateKeyPair = generateKeyPair;
exports.symmetricEncrypt = symmetricEncrypt;
exports.symmetricDecrypt = symmetricDecrypt;
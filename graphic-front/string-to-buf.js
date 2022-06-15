
/**
 * converts a string to a buffer of the bytes that the string is made from
 * @param {String} str string to convert
 * @param {Boolean} [asutf8] whether to return in uft-8
 * @param {Number} [padto] length to pad to, default no padding
 * @param {Number} [padwith] what to pad the buffer with
 * @returns {Buffer}
 */
function stringToBuffer (str, asutf8, padto, padwith) {
    let f = [];
    for (let i = 0; i < str.length; i ++) {
        const x = str.charCodeAt(i);
        if (!asutf8) {
            f.push((x & 0xff00) >> 8);
        }
        f.push(x & 0xff);
    }
    if (padto ?? false) while (f.length < padto) f.push(padwith);
    return Buffer.from(f);
}

/**
 * @param {String} str
 * @returns {Buffer}
 */
function charsToBuffer (str) {
    let f = [];
    const c = "0123456789abcdef";
    for (let i = 0; i < str.length; i += 2) {
        f.push(((c.indexOf(str[i]) << 4) | (c.indexOf(str[i+1]))));
    }
    return Buffer.from(f);
}

exports.stringToBuffer = stringToBuffer;
exports.charsToBuffer = charsToBuffer;
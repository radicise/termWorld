
/**
 * converts a string to a buffer of the bytes that the string is made from
 * @param {String} str string to convert
 * @returns {Buffer}
 */
function stringToBuffer (str) {
    let f = [];
    for (let i = 0; i < str.length; i ++) {
        const x = str.charCodeAt(i);
        f.push((x & 0xff00) >> 8);
        f.push(x & 0xff);
    }
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
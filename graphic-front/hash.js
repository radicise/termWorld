const { createHash } = require("crypto");

const dthconv = "0123456789abcdef";

/**
 * generates a hash
 * @param {Buffer} nums number to hash
 * @returns {Number[]}
 */
 function hash (nums) {
    if (!Array.isArray(nums) && !Buffer.isBuffer(nums)) {
        nums = [nums];
    }
    if (!Buffer.isBuffer(nums)) {
        nums = Buffer.from(nums);
    }
    const h = createHash("sha256");
    h.update(nums);
    const d = h.digest("hex");
    let f = [];
    for (let i = 0; i < d.length; i += 2) {
        n = d[i] + d[i+1];
        f.push(dthconv.indexOf(n[0]) << 4 | dthconv.indexOf(n[1]));
    }
    return f;
}

exports.hash = hash;
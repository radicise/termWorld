const join_path = require("path").join;
const { appendFileSync, existsSync, writeFileSync, mkdirSync, truncateSync } = require("fs");
const { Socket } = require("net");
const { createPrivateKey, createPublicKey, generateKeyPairSync, createHash, KeyObject } = require("crypto");

/**
 * @description thrown when something is expecting a specific key length but got a different length
 * 
 * @example <caption>SymmetricCipher constructor</caption>
 * if (key.length !== 32) throw new KeyLengthError("key length must be 32 bytes");
 */
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

/**
 * @example
 * // a function was expecting an even number of something but got an odd number
 * if (thing.length % 2 !== 0) throw new InvalidDataError("was expecting even number but got an odd number!")
 */
class InvalidDataError extends Error {
    /**
     * represents an error caused by invalid data
     * @param {String} message - error message
     */
    constructor (message) {
        super(message);
        this.name = "InvalidDataError";
    }
}

/**
 * generates a hash
 * @param {Buffer|number[]|number} nums number to hash
 * @returns {number[]}
 * @example <caption>hashing a password using ```stringToBuffer```</caption>
 * hash(Buffer.concat([stringToBuffer("my password"), Buffer.from(SOMEID)]));
 */
function hash (nums) {
    nums = Array.isArray(nums) ? Buffer.from(nums) : (Buffer.isBuffer(nums) ? nums : Buffer.of(nums));
    const h = createHash("sha256");
    h.update(Buffer.from(nums));
    const d = h.digest("hex");
    let f = [];
    for (let i = 0; i < d.length; i += 2) {
        const n = d[i] + d[i+1];
        f.push(c.indexOf(n[0]) << 4 | c.indexOf(n[1]));
    }
    return f;
}

/**
 * generates a pair of keys for RSA encryption
 * @returns {[KeyObject, KeyObject]}
 */
function generateKeyPair () {
    const KEYPAIR = generateKeyPairSync("rsa", {modulusLength:4096,publicKeyEncoding:{type:"spki",format:"pem"},privateKeyEncoding:{type:"pkcs8",format:"pem",cipher:"aes-256-cbc",passphrase:"a"}});
    const privateKeyS = KEYPAIR.privateKey;
    let privateKey = createPrivateKey({key:privateKeyS,type:"pkcs8",format:"pem",passphrase:"a",encoding:"utf-8"});
    let publicKey = createPublicKey(privateKey);
    return [publicKey, privateKey];
}

const c = "0123456789abcdef";

/**
 * @typedef NBufferEncoding
 * @type {"utf-8"|"utf8"|"utf-16"|"utf16"|"ascii"}
 */

/**
 * provides helper methods on {@link Socket}
 * 
 * the methods ```NSocket``` overrides are: {@link NSocket.read}, {@link NSocket.write}, {@link NSocket.end}
 * 
 * ```NSocket``` also adds four new methods: {@link NSocket.bundle}, {@link NSocket.flush}, {@link NSocket.setCryptor}, {@link NSocket.setUseEncryption}
 * 
 * @example <caption>creating a new NSocket</caption>
 * const socket = new NSocket();
 * socket.connect(port, ip);
 * const numbers = await socket.read(4);
 * @example <caption>creating an NSocket from an existing Socket</caption>
 * socket = NSocket.from(socket);
 * const numbers = await socket.read(4);
 */
class NSocket extends Socket {
    constructor () {
        super();
        this.pause();
        /**@private */
        this._oread = Socket.prototype.read;
        /**@private */
        this._owrite = Socket.prototype.write;
        /**@private */
        this._oend = Socket.prototype.end;
        /**@type {SymmetricCipher} @private */
        this.cryptor = null;
        /**@private */
        this.ending = false;
        /**@private */
        this.use_cryptor = true;
        /**@private */
        this.do_flush = true;
        /**@type {Buffer[]} @private */
        this.bundled = [];
        const that = this;
        function rebind () {
            that.write = NSocket.prototype.write;
            that.read = NSocket.prototype.read;
            that.end = NSocket.prototype.end;
        }
        this.once("connect", rebind);
    }
    /**
     * converts a Socket to an NSocket
     * @param {Socket} socket {@link Socket} to convert
     * @returns {NSocket}
     */
    static from (socket) {
        socket.pause();
        socket._oread = Socket.prototype.read;
        socket._owrite = Socket.prototype.write;
        socket._oend = Socket.prototype.end;
        socket.read = NSocket.prototype.read;
        socket.write = NSocket.prototype.write;
        socket.end = NSocket.prototype.end;
        socket._wwrite = NSocket.prototype._wwrite;
        socket.do_flush = true;
        socket.cryptor = null;
        socket.use_cryptor = true;
        socket.ending = false;
        socket.setCryptor = NSocket.prototype.setCryptor;
        socket.setUseEncryption = NSocket.prototype.setUseEncryption;
        socket.bundled = [];
        socket.bundle = NSocket.prototype.bundle;
        socket.flush = NSocket.prototype.flush;
        return socket;
    }
    /**
     * bundles the {@link NSocket.write} commands until {@link NSocket.flush} is called
     * 
     * use this to bundle any write commands that can be sent together to reduce network load
     */
    bundle () {
        this.do_flush = false;
    }
    /**
     * flushes the bundled {@link NSocket.write} commands
     */
    flush () {
        this.do_flush = true;
        if (this.bundled.length === 0) return true;
        this._owrite(Buffer.concat(this.bundled));
        this.bundled = [];
        return false;
    }
    /**
     * sets the internal cryptor
     * @param {SymmetricCipher} cryptor the cryptor to set, see {@link SymmetricCipher} for info on what this does
     */
    setCryptor (cryptor) {
        this.cryptor = cryptor;
    }
    /**
     * sets whether the {@link NSocket} is using encryption, see {@link NSocket.setCryptor} for info on the cryptor
     * @param {boolean} use whether to use encryption
     */
    setUseEncryption (use) {
        this.use_cryptor = use;
    }
    /**
     * overrides the end method to ensure that all data is finished being written before ending the connection
     * @override
     * @param {()=>void} cb
     */
    end (cb) {
        cb = cb || (() => {});
        this.ending = true;
        let x = true;
        if (!this.do_flush) {
            x = this.flush();
        }
        if (this.writableFinished && x) {
            return this._oend(cb);
        }
        this.once("drain", () => {this._oend(cb)});
        this.emit("cClose");
    }
    /**
     * @param {Buffer} data
     */
    _wwrite (data) {
        if (typeof data === "string" && !this.do_flush) return this.bundled.push(stringToBuffer(data, true));
        if (!this.do_flush) return this.bundled.push(data);
        this._owrite(data);
    }
    /**
     * writes data to the socket``
     * 
     * note that when the internal cryptor is set the ```strIsUtf8``` parameter becomes applicable
     * @param {string | number | Buffer | number[]} data data to write
     * @param {boolean} [strIsUtf8] passed through to the internal cryptor if applicable see {@link SymmetricCipher.crypt} for more info
     * @returns {Promise<void>}
     */
    write (data, strIsUtf8) {
        const that = this;
        return new Promise((res, _) => {
            if (that.ending) return res();
            if (that.cryptor !== null && that.cryptor !== undefined && that.use_cryptor) {
                data = that.cryptor.crypt(data, strIsUtf8);
            }
            if (typeof data === "string") {
                that._wwrite(data);
            } else if (typeof data === "number") {
                that._wwrite(Uint8Array.of(data & 0xff));
            } else {
                that._wwrite(Uint8Array.from(data));
            }
            if (this.do_flush) {
                that.once("drain", res);
            } else {
                res();
            }
        });
    }
    /**
     * @typedef readOptions
     * @type {object}
     * @property {number|number[]|Buffer} [default] default value to return if connection fails
     * @property {"number"|"array"|"buffer"|"string"} [format] format to return data in, if ```"string"``` is used {@link readOptions.encoding} must be provided as well
     * @property {NBufferEncoding} [encoding] the encoding to use when ```format``` is ```"string"```, defaults to ```"utf-16"```
     */
    /**
     * async operation to read from the socket
     * @param {number} size size, in bytes, to read
     * @param {readOptions} [options] options for reading
     * @returns {Promise<number[]>}
     */
    read (size, options) {
        // const evE = new Error();
        /**@type {Buffer} */
        let buf = null;
        if (options?.default !== null && options?.default !== undefined) {
            options.default = typeof options.default === "number" ? Buffer.alloc(1, options.default) : Array.isArray(options.default) ? Buffer.from(options.default) : options.default;
        }
        const that = this;
        let defaulted = false;
        function toCall (r) {
            // try {
            buf = that._oread(size);
            // } catch (e) {e.stack += evE.stack; throw e;}
            if (that.readableEnded) {
                defaulted = true;
                buf = options?.default ?? Buffer.of(0x00);
            }
            if (buf !== null) {
                if (that.cryptor !== null && that.cryptor !== undefined && that.use_cryptor && !defaulted) {
                    buf = that.cryptor.crypt(buf, !["utf-16", "utf16"].includes((options?.encoding ?? "utf-8")));
                }
                switch (options?.format) {
                    case "number":
                        if (buf.length === 1) {
                            r(buf[0]);
                            break;
                        } else {
                            r(Array.from(buf));
                            break;
                        }
                    case "buffer":
                        r(buf);
                        break;
                    case "string":
                        if (["utf8", "uft-8"].includes(options?.encoding)) {
                            r(buf.toString("utf-8"));
                        } else if (options.encoding === "ascii") {
                            r(buf.toString("ascii"));
                        } else {
                            r(bufferToString(buf));
                        }
                        break;
                    default:
                        r(Array.from(buf));
                        break;
                }
                return;
            }
            setTimeout(()=>{toCall(r)}, 0);
        }
        return new Promise((res, _) => {
            toCall(res);
        });
    }
}

/**
 * converts a string to a buffer of the bytes that the string is made from
 * @param {string | Buffer} str string to convert
 * @param {boolean} [asascii] whether to return in ASCII
 * @param {number} [padto] length to pad to, default no padding
 * @param {number} [padwith] what to pad the buffer with
 * @returns {Buffer}
 */
function stringToBuffer (str, asascii, padto, padwith) {
    if (Buffer.isBuffer(str)) return str;
    let f = [];
    for (let i = 0; i < str.length; i ++) {
        const x = str.charCodeAt(i);
        if (!asascii) {
            f.push((x & 0xff00) >> 8);
        }
        else if (f > 127) {
            continue;
        }
        f.push(x & 0xff);
    }
    if (padto ?? false) {while (f.length < padto) {f.push(padwith);}}
    // console.log(f.length, "FLEN");
    return Buffer.from(f);
}

/**
 * converts a buffer to text
 * @param {Buffer|number[]} buf buffer to convert
 * @param {NBufferEncoding} [encoding] text encoding defaults to ```utf-16```
 */
function bufferToString (buf, encoding) {
    encoding = encoding || "utf-16";
    if (!["utf16", "utf-16"].includes(encoding)) {
        return (Array.isArray(buf) ? Buffer.from(buf) : buf).toString(encoding);
    }
    if (buf.length % 2) {
        throw new InvalidDataError(`utf-16 requires even number of bytes but got "${buf.length}" instead`);
    }
    let f = "";
    for (let i = 0; i < buf.length; i += 2) {
        f += String.fromCharCode((buf[i] << 8) | buf[i+1]);
    }
    return f;
}

/**
 * @param {string | Buffer} str string to convert
 * @returns {Buffer}
 */
function charsToBuffer (str) {
    if (Buffer.isBuffer(str)) return str;
    str = str.split(" ").join("").split(/0x(?=\d\d)/).join("");
    if (str.length % 2) {
        throw new InvalidDataError(`requires an even number of hexadecimal digits but got "${str.length}" digits instead`);
    }
    let f = [];
    for (let i = 0; i < str.length; i += 2) {
        f.push(((c.indexOf(str[i]) << 4) | (c.indexOf(str[i+1]))));
    }
    return Buffer.from(f);
}

/**
 * converts n to hex representation
 * @param {number|number[]|Buffer}n thing to convert
 * @returns {string}
 */
function asHex (n) {
    n = Buffer.isBuffer(n) ? Array.from(n) : n;
    if (!Array.isArray(n)) {
        return c[n >> 4] + c[n & 0x0f];
    }
    return n.map(v => c[v >> 4]+c[v & 0x0f]).join("");
}

/**
 * reduces buffer contents to hex representation
 * @param {number[]|Buffer} buf buffer to reduce
 * @returns {string}
 */
function reduceToHex (buf) {
    buf = Buffer.isBuffer(buf) ? Array.from(buf) : buf;
    return buf.map(v => c[v >> 4]+c[v & 0x0f]).join("");
}

/**
 * formats a buffer as a string
 * @param {Buffer | number[] | number} buf buffer to format
 * @returns {string}
 */
function formatBuf (buf) {
    buf = typeof buf === "number" ? [buf] : buf;
    if (Buffer.isBuffer(buf)) {
        buf = Array.from(buf);
    }
    // console.log(buf);
    return `<Buffer ${(Array.isArray(buf)?buf:Array.from(buf)).map(v => c[v >> 4] + c[v & 0x0f]).toString().split(",").join(" ")}>`;
}

class LogBundler {
    /**
     * creates a bundle of logs where it's important that related logs are not interrupted
     * once logged, the bundle cannot be used further
     * @param {Logger} log logger to send the bundle to @see {@link Logger}
     */
    constructor (log) {
        /**@private */
        this.parent_log = log;
        /**@type {string[]} @private */
        this.bundle = [];
        /**
         * stores if the bundle has been logged
         * @private
         */
        this.finished = false;
        /**@type {string|null} @private */
        this.logOnFinish = null;
        /**@type {[number, number]} @private */
        this.headerDashCounts = [0, 0];
        /**@private */
        this.indentCount = 0;
    }
    /**
     * sets dash and space count
     * @param {number} dashes number of dashes
     * @param {number} spaces number of spaces
     */
    setHeaderDashCount (dashes, spaces) {
        this.headerDashCounts = [dashes, spaces];
    }
    /**
     * sets the number of spaces to indent each non-header line by
     * @param {number} count number of spaces
     */
    setIndentCount (count) {
        this.indentCount = count;
    }
    /**
     * logs text
     * @param {string} text text to log
     * @param {string} [withDate] log text with date
     */
    mkLog (text, withDate) {
        if (withDate) {
            text = `${this.parent_log.getTimeStamp()} ` + text;
        }
        text = " ".repeat(this.indentCount) + text;
        this.bundle.push(text);
    }
    /**
     * logs text as a header
     * @param {string} text text to log
     */
    mkHeader (text) {
        const d = "-".repeat(this.headerDashCounts[0]);
        const s = " ".repeat(this.headerDashCounts[1]);
        text = d + s + text + s + d;
        this.bundle.push(text);
    }
    /**
     * sets the text to log when @see {@link LogBundler.finish} is called
     * @param {string} text text to log, if falsey then no text will be logged
     * @param {boolean} [isHeader] sets if the final text should be a header
     */
    onFinish (text, isHeader) {
        if (text && isHeader) {
            const d = "-".repeat(this.headerDashCounts[0]);
            const s = " ".repeat(this.headerDashCounts[1]);
            text = d + s + text + s + d;
        }
        this.logOnFinish = text ? String(text) : null;
    }
    /**
     * logs the bundle @see {@link Logger.mkLog}
     * @returns {void}
     */
    finish () {
        if (this.finished) return;
        this.finished = true;
        if (this.logOnFinish !== null) this.bundle.push(this.logOnFinish);
        this.parent_log.logBundle(this.bundle);
    }
}

/**
 * @param {string[]} path
 * @param {string} [defaultVal]
 */
function mkTmp (path, defaultVal) {
    for (let i = 1; i < path.length; i ++) {
        const p = join_path(__dirname, ...path.slice(0, i));
        if (!existsSync(p)) {
            if (i === path.length - 1) {
                writeFileSync(p, defaultVal ?? "", {encoding:"utf-8"});
            } else {
                mkdirSync(p);
            }
        }
    }
}

class Logger {
    /**
     * @param {string[]} path
     */
    constructor (...path) {
        mkTmp(path);
        this.path = join_path(__dirname, ...path);
        this.no_logging = false;
    }
    /**
     * creates a new bundler
     * @returns {LogBundler}
     */
    createLogBundle () {
        return new LogBundler(this);
    }
    /**
     * @private
     * @param {string[]} bundled bundled logs
     */
    logBundle (bundled) {
        this.mkLog(bundled.join("\n"));
    }
    getTimeStamp () {
        const d = new Date();
        return `(${["Sun","Mon","Tue","Wed","Thu","Fri","Sat"][d.getDay()]} ${d.getMonth().toString().padStart(2, "0")} ${d.getDate().toString().padStart(2, "0")} ${d.getFullYear()} @ ${d.getUTCHours().toString().padStart(2, "0")}:${d.getMinutes().toString().padStart(2, "0")}:${d.getSeconds().toString().padStart(2, "0")})`;
    }
    /**
     * formats a buffer as a string
     * @param {Buffer | number[]} buf buffer to format
     * @returns {string}
     */
    formatBuf (buf) {
        return formatBuf(buf);
    }
    /**
     * makes a log entry
     * @param {string} text text to log
     * @param {boolean} [withDate] log with date
     * @returns {void}
     */
    mkLog (text, withDate) {
        if (this.no_logging) return;
        appendFileSync(this.path, `${withDate ? (this.getTimeStamp() + " ") : ""}${text}\n`, {encoding:"utf-8"});
    }
    /**
     * clears the log file
     */
    clearLogFile () {
        truncateSync(this.path, 0);
    }
}

class SymmetricCipher {
    /**
     * @param {Buffer} key key to use
     */
    constructor (key) {
        if (!Buffer.isBuffer(key)) throw new TypeError("key must be a buffer");
        if (key.length !== 32) throw new KeyLengthError("key length must be 32 bytes");
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
     * @param {boolean} [strIsUtf8] when a string is passed, this value will be passed to the ```asutf8``` parameter of {@link stringToBuffer}
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

/**
 * segments a number into bytes
 * @param {number} big number to segment
 * @param {number} byte_count number of bytes to segment big into
 * @returns {number[]}
 */
function bigToBytes (big, byte_count) {
    /**@type {number[]} */
    let f = [];
    for (let i = byte_count-1; i >= 0; i --) {
        f.push((big & (0xff << (i * 8))) >> (i * 8));
    }
    return f;
}

/**
 * creates a number out of component bytes
 * @param {number[]} bytes bytes to convert
 * @returns {number}
 */
function bytesToBig (bytes) {
    let f = 0;
    for (let i = bytes.length - 1; i >= 0; i --) {
        f = f | (bytes[i] << (((bytes.length - 1) - i) * 8));
    }
    return f;
}

/**
 * @typedef SAddr
 * @type {{id:0|1|2,address:any,port:number}[]}
 */

/**
 * resolves the given address
 * @param {SAddr} addr address
 * @returns {Promise<[string, string, boolean]>}
 */
function resolveServerAddr (addr) {
    return new Promise ((res, _) => {
        switch (addr.id) {
            case 0:
                return res([addr.address.join("."), `using IPv4 to connect: ip="${addr.address.join(".")}" port="${addr.port}"`, false]);
            case 1:
                let x = [];
                let b = "";
                for (let i = 0; i < 16; i += 2) {
                    x.push(asHex([addr.address[i], addr.address[i+1]]));
                }
                x.forEach(v => {
                    if (v !== "0000") {
                        b += v;
                    }
                    b += ":";
                });
                return res([x.slice(0, x.length-1), `using IPv6 to connect: ip="${x.slice(0, x.length-1)}" port="${addr.port}"`, false]);
            case 2:
                dns_lookup(addr.address, (e, a, _) => {
                    if (e) return res(["0.0.0.0", `DNS LOOKUP FAILURE: ${e}`, true]);
                    res([a, `using DNS to connect: hostname="${addr.address}" port="${addr.port}"`, false]);
                });
        }
    });
}

/**
 * connects a socket to the given address
 * @param {SAddr} addr address to connect to
 * @param {NSocket} sock socket to connect with
 * @param {(ret:string, hadErr:boolean) => void} cb callback for logs
 * @returns {Promise<boolean>}
 */
function vConnect (addr, sock, cb) {
    return new Promise((res, _) => {
        resolveServerAddr(addr).then(([a, m, e]) => {
            if (e) {cb(m, true); return res(false); }
            sock.connect(addr.port, a);
            cb(m, false);
            res(true);
        });
    });
}

/**
 * eats ```count``` items from ```arr```
 * @template T
 * @param {T[]} arr array to eat from
 * @param {number} count number of items to eat
 * @returns {T[]}
 */
function eat (arr, count) {
    return arr.splice(0, count);
}

/**
 * @param {number} l
 * @param {number} h
 * @returns {number}
 */
function randomInt (l, h) {
    l = Math.floor(l);
    h = Math.floor(h);
    return Math.floor(Math.random() * (l-h) + l);
}

exports.SymmetricCipher = SymmetricCipher;
exports.NSocket = NSocket;
exports.Logger = Logger;
exports.SAddr = this.SAddr;
exports.formatBuf = formatBuf;
exports.stringToBuffer = stringToBuffer;
exports.charsToBuffer = charsToBuffer;
exports.bufferToString = bufferToString;
exports.asHex = asHex;
exports.reduceToHex = reduceToHex;
exports.generateKeyPair = generateKeyPair;
exports.hash = hash;
exports.bigToBytes = bigToBytes;
exports.bytesToBig = bytesToBig;
exports.mkTmp = mkTmp;
exports.vConnect = vConnect;
exports.eat = eat;
exports.resolveServerAddr = resolveServerAddr;
exports.randomInt = randomInt;
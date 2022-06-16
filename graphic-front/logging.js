const join_path = require("path").join;
const { appendFileSync, existsSync, writeFileSync, mkdirSync, truncateSync } = require("fs");

const c = "0123456789abcdef";

/**
 * formats a buffer as a string
 * @param {Buffer | number[]} buf buffer to format
 * @returns {string}
 */
function formatBuf (buf) {
    if (Buffer.isBuffer(buf)) {
        buf = Array.from(buf);
    }
    return `<Buffer ${Array.isArray(buf)?buf:Array.from(buf).map(v => c[v >> 4] + c[v & 0x0f]).toString().split(",").join(" ")}>`;
}

class Logger {
    /**
     * @param {string[]} path
     */
    constructor (...path) {
        for (let i = 1; i < path.length; i ++) {
            const p = join_path(__dirname, ...path.slice(0, i));
            if (!existsSync(p)) {
                if (i === path.length - 1) {
                    writeFileSync(p, "", {encoding:"utf-8"});
                } else {
                    mkdirSync(p);
                }
            }
        }
        this.path = join_path(__dirname, ...path);
        this.no_logging = false;
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
     * @returns {void}
     */
    mkLog (text) {
        if (this.no_logging) return;
        appendFileSync(this.path, text+"\n", {encoding:"utf-8"});
    }
    /**
     * clears the log file
     */
    clearLogFile () {
        truncateSync(this.path, 0);
    }
}

exports.Logger = Logger;
exports.formatBuf = formatBuf;
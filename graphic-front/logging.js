const join_path = require("path").join;
const { appendFileSync, existsSync, writeFileSync, mkdirSync } = require("fs");

class Logger {
    /**
     * @param {String[]} path
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
        this.path = join_path(__dirname, path);
        this.no_logging = false;
    }
    mkLog (text) {
        if (this.no_logging) return;
        appendFileSync(this.path, text+"\n", {encoding:"utf-8"});
    }
}

exports.Logger = Logger;
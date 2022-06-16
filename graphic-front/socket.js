const { Socket } = require("net");

class NSocket extends Socket {
    constructor () {
        super();
        this.pause();
        /**@type {(size? : number) => any} */
        this._oread = Socket.prototype.read;
        /**@type {(data : string | Uint8Array, cb? : any) => void} */
        this._owrite = Socket.prototype.write;
    }
    /**
     * writes data to the socket
     * @param {string | number | Buffer | number[]} data data to write
     */
    write (data) {
        if (typeof data === "string") {
            this._owrite(data);
        } else if (typeof data === "number") {
            this._owrite(Uint8Array.of(data & 0xff));
        } else {
            this._owrite(Uint8Array.from(data));
        }
    }
    /**
     * async operation to read from the socket
     * @param {number} size size, in bytes, to read
     * @param {{default?:number|number[]|Buffer}} [options] options for reading
     * @returns {Promise<number[]>}
     */
    read (size, options) {
        let buf = null;
        if (options?.default !== null && options?.default !== undefined) {
            options.default = typeof options.default === "number" ? Buffer.alloc(1, options.default) : Array.isArray(options.default) ? Buffer.from(options.default) : options.default;
        }
        function toCall (r) {
            buf = this._oread(size);
            if (this.readableEnded) {
                buf = defaultV;
            }
            if (buf !== null) {
                r(Array.from(buf));
                return;
            }
            setTimeout(()=>{toCall(r)}, 0);
        }
        return new Promise((res, _) => {
            toCall(res);
        });
    }
}

exports.NSocket = NSocket;
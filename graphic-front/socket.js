'use strict';

const { Socket } = require("net");

class NSocket extends Socket {
    constructor () {
        super();
        this.pause();
        this._oread = Socket.prototype.read;
        this._owrite = Socket.prototype.write;
        this._oend = Socket.prototype.end;
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
     * @param {Socket} socket socket to convert
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
        return socket;
    }
    /**
     * @param {()=>void} cb
     */
    end (cb) {
        cb = cb || (() => {});
        if (this.writableFinished) {
            return this._oend(cb);
        }
        this.once("drain", () => {this._oend(cb)});
    }
    /**
     * writes data to the socket
     * @param {string | number | Buffer | number[]} data data to write
     * @returns {Promise<void>}
     */
    write (data) {
        if (typeof data === "string") {
            this._owrite(data);
        } else if (typeof data === "number") {
            this._owrite(Uint8Array.of(data & 0xff));
        } else {
            this._owrite(Uint8Array.from(data));
        }
        return new Promise((res, _) => {
            this.once("drain", res);
        });
    }
    /**
     * async operation to read from the socket
     * @param {number} size size, in bytes, to read
     * @param {{default?:number|number[]|Buffer}} [options] options for reading
     * @returns {Promise<number[]>}
     */
    read (size, options) {
        const evE = new Error();
        let buf = null;
        if (options?.default !== null && options?.default !== undefined) {
            options.default = typeof options.default === "number" ? Buffer.alloc(1, options.default) : Array.isArray(options.default) ? Buffer.from(options.default) : options.default;
        }
        const that = this;
        function toCall (r) {
            // try {
            buf = that._oread(size);
            // } catch (e) {e.stack += evE.stack; throw e;}
            if (that.readableEnded) {
                buf = options?.default ?? Buffer.of(0x00);
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
/**
 * reads data from a socket
 * @param {import("net").Socket} socket socket to read from
 * @param {number} size size to read, in bytes
 * @param {{ default?: number | number[], transform?: { f: (buf : Buffer, args : any[]) => Buffer, args: any[] } }} options options
 * @returns {Promise<number[]>}
 */
function read (socket, size, options) {
    let evE = new Error();
    if (options?.default !== null && options?.default !== undefined) {options.default = typeof options.default === "number" ? Buffer.alloc(1, options.default) : Buffer.from(options.default);}
    let buf = null;
    function recurse (f, r) {
        try {
        if (f()) {
            // console.log(options, buf);
            //...(options?.transform?.args || [])
            return r(Array.from(options?.transform ? options.transform.f(buf) : buf));}
        } catch (er) {er.stack+=evE.stack;throw er;}
        setTimeout(()=>{recurse(f, r);}, 0);
    }
    return new Promise((res, _) => {
        recurse(() => {
            if (socket.readableEnded) {
                buf = options?.default ?? Buffer.alloc(1, 0);
                return true;
            }
            buf = socket.read(size);
            if (buf !== null) {
                return true;
            }
            return false;
        }, res);
    });
}

exports.read = read;
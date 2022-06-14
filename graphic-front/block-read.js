/**
 * reads data from a socket
 * @param {import("net").Socket} socket
 * @param {Number} size
 * @param {{default:Number|Number[],transform:{f:()=>Buffer,args:any[]}}} [options] options
 */
function read (socket, size, options) {
    if (options?.default) {options.default = typeof options.default === "number" ? Buffer.alloc(1, options.default) : Buffer.from(options.default);}
    let buf = null;
    function recurse (f, r) {
        if (f()) {return r(Array.from(options?.transform ? options.transform.f(...options.transform.args, buf) : buf));}
        setTimeout(()=>{recurse(f, r);}, 0);
    }
    function readin (n) {
        return new Promise(res => {
            recurse(()=>{if (socket.readableEnded){buf=options?.default ?? Buffer.alloc(1, 0);return true;};buf = socket.read(n);if (buf !== null) {return true;}return false;}, res);
        });
    }
    return readin(size);
}

// /**
//  * reads data from a stream
//  * @param {import("net").Socket} socket
//  * @param {Number} size
//  * @param {{default:Number|Number[],transform:{f:()=>Buffer,args:any[]}}} [options]
//  * @returns {Promise<Number[]>}
//  */
// function read (socket, size, options) {
    // return read2(socket, size, options);
    // let buf = null;
    // function recurse (res) {
    //     buf = socket.read(size);
    //     if (buf !== null) {
    //         return res(Array.from(buf));
    //     }
    //     setTimeout(recurse, 0);
    // }
    // return new Promise(res => {
    //     recurse(res);
    //     // function p1 (f, r) {if (f()) {return r(buf);}setTimeout(()=>{p1(f, r);}, 0);}
    // });
// }

exports.read = read;
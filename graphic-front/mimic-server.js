const net = require("net");
const { read } = require("./block-read");
const { randomBytes } = require("crypto");

const port = 5000;
const host = "127.0.0.1";

const serverID = Array.from(Buffer.alloc(8, 0));

const server = net.createServer(async (socket) => {
    socket.pause();
    function write (data) {
        if (!Array.isArray(data)) {
            data = [data];
        }
        socket.write(Buffer.from(data));
    }
    let buf = [];
    console.log("waiting for username");
    buf = await read(socket, 32);
    console.log(buf);
    write([...Array.from(randomBytes(32)), ...serverID]);
    buf = await read(socket, 48);
    console.log(buf);
    write(0x63);
    socket.end();
}).listen(port, host);
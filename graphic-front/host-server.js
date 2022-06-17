const net = require("net");
const { read } = require("./block-read");
const { randomBytes, publicEncrypt, createPublicKey } = require("crypto");
const { formatBuf } = require("./logging");
const { NSocket } = require("./socket");
const dns_lookup = require("dns").lookup;
const { hash } = require("./hash");
const { stringToBuffer } = require("./string-to-buf");

net.createServer(async (socket) => {
    socket.write(Buffer.of(0x50));
    socket.on("data", data => console.log(formatBuf(data)));
}).listen(4000);

const test = new NSocket();
test.connect(4000, "127.0.0.1", async () => {
    console.log(await test.read(1));
});

const port = 15651;

const serverID = Array.from(Buffer.alloc(8, 0));
let serverPassword = "password";

/**
 * converts n to hex representation
 * @param {number|number[]}n thing to convert
 * @returns {string}
 */
 function asHex (n) {
    if (!Array.isArray(n)) {
        return c[n >> 4] + c[n & 0x0f];
    }
    let f = n.map(v => c[v >> 4]+c[v & 0x0f]).join("");
    return f;
}

class Host {
    constructor () {
        this.player_count = 0;
        this.max_players = 0;
        /**@type {{id:0|1|2,address:any;port,number}[]} */
        this.maintain_auths = [
            {
                id:0,
                address:[127, 0, 0, 1],
                port:15652,
            },
        ];
        /**@type {Buffer} */
        this.secret;
        this.regenerateSecret();
    }
    regenerateSecret () {
        this.secret = randomBytes(32);
        for (const addr of this.maintain_auths) {
            const sock = new NSocket();
            console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
            sock.on("connect", async () => {
                console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
                sock.write([0x33, ...serverID]);
                if ((await sock.read(1))[0] === 0x55) return;
                const nonce0 = await sock.read(32);
                console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
                sock.write(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer(serverPassword), Buffer.from(serverID)]))), Buffer.from(nonce0)])));
                if ((await sock.read(1))[0] === 0x55) return;
                let buf = await sock.read(2);
                console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
                buf = await sock.read((buf[0] << 8) | buf[1]);
                console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
                const ret = publicEncrypt(createPublicKey(Buffer.from(buf).toString("utf-8")), this.secret);
                console.log(sock.write == NSocket.prototype.write, sock.read == NSocket.prototype.read);
                sock.write([(ret.length & 0xff00) >> 8, ret.length & 0xff]);
                sock.write(ret);
                if ((await sock.read(1))[0] === 0x55) return console.log("failed");
                console.log("succes");
                sock.end();
            });
            switch (addr.id) {
                case 0:
                    sock.connect(addr.port, addr.address.join("."));
                    break;
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
                    sock.connect(x.slice(0, x.length-1), addr.port);
                    break;
                case 2:
                    dns_lookup(addr.address, (e, a, _) => {
                        if (e) return;
                        sock.connect(a, addr.port);
                    });
                    break;
            }
        }
    }
}

const host = new Host();

const server = net.createServer(
    /**
     * @param {NSocket} socket
     */
    async (socket) => {
        socket = NSocket.from(socket);
        socket.on("error", (err) => {});
        const initopid = (await socket.read(1))[0];
        if (initopid === 0x00) return;
        if (initopid === 0x64) {
            const statusid = (await socket.read(1))[0];
            if (statusid === 0x01) {
                socket.write([(host.max_players & 0xff00) >> 8, host.max_players & 0xff, (host.player_count & 0xff00) >> 8, host.player_count & 0xff]);
            }
            if (statusid === 0x02) {
                socket.write();
            }
            socket.end();
            return;
        }
        if (initopid === 0x63) {
            console.log("waiting for username");
            buf = await read(socket, 32);
            console.log(formatBuf(buf));
            socket.write([...Array.from(randomBytes(32)), ...serverID]);
            buf = await read(socket, 48);
            console.log(formatBuf(buf));
            socket.write(0x63);
            socket.end();
            return;
        }
    }
).listen(port);
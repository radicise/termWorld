const net = require("net");
const { randomBytes, publicEncrypt, createPublicKey } = require("crypto");
const dns_lookup = require("dns").lookup;
const { hash, Logger, formatBuf, stringToBuffer, asHex, NSocket } = require("./defs");

const argv = process.argv;

const logger = new Logger("logs", "server-log.log");

if (argv.includes("--clear-log")) logger.clearLogFile();

logger.mkLog("INSTANCE START", true);

let no_exit = false;

function onexit () {
    logger.no_logging = false;
    logger.mkLog("INSTANCE END", true);
    if (!no_exit) {
        process.exit();
    }
}

process.on("SIGINT", onexit);
process.on("SIGUSR1", onexit);
process.on("SIGUSR2", onexit);
process.on("uncaughtException", (l)=>{logger.no_logging=false;logger.mkLog(`UNHANDLED EXCEPTION: ${l}`, true);console.log(l?.stack);onexit()});

const unsafe_logs = argv.includes("--unsafe-log");
if (unsafe_logs) logger.mkLog("UNSAFE LOGGING ENABLED");
logger.no_logging = argv.includes("--no-log");

if (logger.no_logging) {
    logger.no_logging = false;
    logger.mkLog("SERVER LOGGING DISABLED");
    logger.no_logging = true;
}

const port = 15651;

const serverID = Array.from(Buffer.alloc(8, 0));
let serverPassword = "password";

class Host {
    constructor () {
        this.player_count = 0;
        this.max_players = 0;
        /**@type {{id:0|1|2,address:any,port:number}[]} */
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
    /**
     * @private
     */
    regenerateSecret () {
        this.secret = randomBytes(32);
        for (const addr of this.maintain_auths) {
            const sock = new NSocket();
            const bundle = logger.createLogBundle();
            bundle.setHeaderDashCount(5, 2);
            bundle.setIndentCount(7);
            bundle.mkHeader("AUTH UPDATE PROTOCOL");
            bundle.onFinish("CONNECTION TERMINATED", true);
            sock.on("error", () => {bundle.mkLog("connection error"); bundle.finish()});
            sock.on("cClose", ()=>{bundle.finish()});
            sock.on("connect", async () => {
                bundle.mkLog("connected to auth server");
                sock.write([0x33, ...serverID]);
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: auth server did not recognize server ID"); return sock.end();}
                const nonce0 = await sock.read(32);
                sock.write(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer(serverPassword), Buffer.from(serverID)]))), Buffer.from(nonce0)])));
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: invalid server password"); return sock.end();}
                let buf = await sock.read(2);
                buf = await sock.read((buf[0] << 8) | buf[1]);
                const ret = publicEncrypt(createPublicKey(Buffer.from(buf).toString("utf-8")), this.secret);
                sock.write([(ret.length & 0xff00) >> 8, ret.length & 0xff]);
                sock.write(ret);
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: unknown reason"); return sock.end();}
                bundle.mkLog("successfully updated server secret");
                sock.end();
            });
            switch (addr.id) {
                case 0:
                    bundle.mkLog(`using IPv4 to connect to auth server: ip="${addr.address.join(".")}" port="${addr.port}"`);
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
                    bundle.mkLog(`using IPv6 to connect to auth server: ip="${x.slice(0, x.length-1)}" port="${addr.port}"`);
                    sock.connect(addr.port, x.slice(0, x.length-1));
                    break;
                case 2:
                    bundle.mkLog(`using DNS to connect to auth server: hostname="${addr.address}" port="${addr.port}"`);
                    dns_lookup(addr.address, (e, a, _) => {
                        if (e) {bundle.mkLog(`DNS LOOKUP FAILURE: ${e}`); return bundle.finish();}
                        sock.connect(a, addr.port);
                    });
                    break;
            }
        }
    }
    /**
     * @param {NSocket} socket
     */
    socketConnection (socket) {}
}

const host = new Host();

const server = net.createServer((socket) => {host.socketConnection(NSocket.from(socket))}).listen(port);

/**
const server = net.createServer(
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
*/
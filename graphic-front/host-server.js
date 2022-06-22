const net = require("net");
const { randomBytes, publicEncrypt, createPublicKey } = require("crypto");
const dns_lookup = require("dns").lookup;
const { hash, Logger, formatBuf, stringToBuffer, asHex, NSocket, bigToBytes, bufferToString, mkTmp, SAddr, vConnect} = require("./defs");
const { readFileSync, existsSync } = require("fs");
const join_path = require("path").join;
const gameVersion = 2;//update upon update
const plugin_reg_path = join_path(__dirname, "data", "server", "plugins.json");

mkTmp(["data", "server", "plugins.json"], "{}");

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


/**
 * @typedef ServerPlugin
 * @type {object}
 * @property {number} service_register provides boolean indication of what services the plugin provides and therefore what it needs to listen to
 * 
 * BITS (0 is rightmost):
 * 
 * 0 - denyPlayerLogin
 * 
 * 1 - spawnObstructionResolver
 * @property {(name:string,id:number[]) => [boolean, string]} [denyPlayerLogin] checks if a player login should be denied
 * @property {() => boolean} [spawnObstructionResolver] attempts to resolve player spawn obstruction. returns true on success and false otherwise NOT FULLY DEFINED YET
 */

class Entity {
    /**
     * 
     * @param {number} xPos
     * @param {number} yPos
     */
    constructor(xPos, yPos) {
        this.etype = 0;
        this.x = xPos;
        this.y = yPos;
        this.termColor = 16;
        this.health = 10
    }
    /**
     * serializes level data to a given buffer
     * @param {Buffer} buf buffer to write serialized data to
     * @param {number} off offset in buffer
     * @returns {number} amount of bytes written
     */
    serialize (buf, off) {
        buf.writeUInt8(this.etype, off);
        off++;
        if (etype == 4) {
            return off;
        }
        else if (etype == 2) {
            //TODO serialize the inventory
        }
        buf.writeUInt32BE(this.x, off);
        off += 4;
        buf.writeUInt32BE(this.y, off);
        off += 4;
        if (type == 3) {
            //TODO serialize item data
        }
        buf.writeBigInt64BE(BigInt((this.termColor & 0xf) << 6));
        off += 8;
        buf.writeInt16BE(this.health);
        off += 2; 
        return off;
    }
}
class Host {
    constructor () {
        this.name = "DEFAULT SERVER";
        const tiargv = argv.find(v => v.startsWith("--turnint="));
        const mpargv = argv.find(v => v.startsWith("--maxplay="));
        this.turn_interval = ((tiargv ? Number(tiargv.split("=")[1]) : null) ?? Number(process.env.turnint)) || 300;
        this.max_players = ((mpargv ? Number(mpargv.split("=")[1]) : null) ?? Number(process.env.maxplay)) || 10;
        /**@type {NSocket[]} */
        this.connected = [];
        /**@type {Entity[]} */
        this.entities = [];
        this.level_age = 0;
        const lsxargv = argv.find(v => v.startsWith("--lspawnx="));
        const lsyargv = argv.find(v => v.startsWith("--lspawny="));
        const lwargv = argv.find(v => v.startsWith("--lwidth="));
        const lhargv = argv.find(v => v.startsWith("--lheight="));
        this.level_spawnx = ((lsxargv ? Number(lsxargv.split("=")[1]) : null) ?? Number(process.env.lspawnx)) || 1;
        this.level_spawny = ((lsyargv ? Number(lsyargv.split("=")[1]) : null) ?? Number(process.env.lspawny)) || 1;
        this.level_width = ((lwargv ? Number(lwargv.split("=")[1]) : null) ?? Number(process.env.lwidth)) || 10;
        this.level_height = ((lhargv ? Number(lhargv.split("=")[1]) : null) ?? Number(process.env.lheight)) || 10;
        this.level_data = [];
        /**@type {ServerPlugin[]} */
        this.plugins = [];
        this.listeners = {
            /**@type {ServerPlugin[]} */
            checkPlayerDeny : [],
            /**@type {Function[]} */
            spawnObstructionResolver : [],
        };
        for (let y = 0; y < this.level_height; y ++) {
            let row = [];
            for (let x = 0; x < this.level_width; x ++) {
                row.push(0);
            }
            this.level_data.push(row);
        }
        /**@type {SAddr[]} */
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
        this.reloadPlugins();
    }
    /**
     * serializes level data to a given buffer
     * @param {Buffer} buf buffer to write serialized data to
     * @param {number} off offset of write start
     * @returns {number} amount of bytes written
     */
    serializeLevelData (buf, off) {
        buf.writeInt32BE(gameVersion, off);
        off += 4;
        buf.writeInt32BE(this.level_width, off);
        off += 4;
        buf.writeInt32BE(this.level_height, off);
        off += 4;
        buf.writeInt32BE(1024, off);//TODO Make this actually mean something
        off += 4;
        for (var row in this.level_data) {
            for (let cell in row) {
                buf.writeUInt8(cell, off);
                off++;
            }
        }
        buf.writeBigInt64BE(BigInt(this.level_age), off);
        off += 8;
        buf.writeInt32BE(this.level_spawnx, off);
        off += 4;
        buf.writeInt32BE(this.level_spawny, off);
        off += 4;
        buf.writeInt32BE(this.entities.length, off);
        off += 4;
        this.entities.forEach(function (ent) {
            off += ent.serialize(buf, off);
        });
        return off;
    }
    /**
     * reloads the server plugins
     */
    reloadPlugins () {
        this.plugins = [];
        this.listeners.checkPlayerDeny = [];
        this.listeners.spawnObstructionResolver = [];
        /**@type {{dir:string,plugins:string[]}} */
        const plugs = JSON.parse(readFileSync(plugin_reg_path, {encoding:"utf-8"}));
        // plugin defs weren't found
        if (!plugs.dir) return;
        for (const name of plugs.plugins) {
            const p = join_path(__dirname, "data", "server", plugs.dir, name);
            if (!existsSync(p)) continue;
            const { Plugin } = require(`./data/server/${plugs.dir}/${name}`);
            /**@type {ServerPlugin} */
            const plugin = new Plugin();
            this.plugins.push(plugin);
            if (plugin.service_register & 1) {
                this.listeners.checkPlayerDeny.push(plugin);
            }
        }
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
            sock.on("error", (theerror) => {bundle.mkLog(`connection error: ${theerror}`); bundle.finish()});
            sock.on("cClose", ()=>{bundle.finish()});
            sock.on("connect", async () => {
                bundle.mkLog("connected to auth server");
                sock.write([0x33, ...serverID]);
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: auth server did not recognize server ID"); return sock.end();}
                const nonce0 = await sock.read(32);
                sock.write(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer(serverPassword, false, 32, 0x20), Buffer.from(serverID)]))), Buffer.from(nonce0)])));
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: invalid server password"); return sock.end();}
                let buf = await sock.read(2);
                buf = await sock.read((buf[0] << 8) | buf[1]);
                const ret = publicEncrypt(createPublicKey({"key" : Buffer.from(buf), "format" : "der", "type" : "pkcs1"}), this.secret);
                sock.write([(ret.length & 0xff00) >> 8, ret.length & 0xff]);
                sock.write(ret);
                if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: unknown reason"); return sock.end();}
                bundle.mkLog(`successfully updated server secret for auth server at ${["IPv4", "IPv6", "hostname"][addr.id]} ${addr.address} port ${addr.port}` + (unsafe_logs ? ` to ${formatBuf(this.secret)}` : ""));
                sock.end();
            });
            vConnect(addr, sock, (ret, hadErr) => {
                bundle.mkLog(ret);
                if (hadErr) bundle.finish();
            });
        }
    }
    /**
     * checks if a player should be denied access even with valid auth eg: player was banned
     * @param {string} name name of player
     * @param {number[]} id player id
     * @param {number} ver player version
     * @returns {[boolean, string]}
     */
    checkPlayerDeny (name, id, ver) {
        for (const check of this.listeners.checkPlayerDeny) {
            const km = check.denyPlayerLogin(name, id);
            if (km[0]) {
                return km;
            }
        }
        if (ver != gameVersion) {
            return [true, "outdated client version"]
        }
        return [false, ""];
    }
    /**
     * @param {NSocket} socket
     */
    async socketJoinServer (socket) {
        const name = bufferToString(await socket.read(32));
        const nonce0 = randomBytes(32);
        socket.bundle();
        socket.write(nonce0);
        socket.write(serverID);
        socket.flush();
        const authdata = await socket.read(72);
        const ahash = authdata.slice(40, 72);
        const checkhash = hash(Buffer.concat([Buffer.from(authdata.slice(0, 32)), nonce0, this.secret, Buffer.from(authdata.slice(32, 40))]));
        if (checkhash.some((v, i) => v !== ahash[i])) {
            socket.write(0x55);
            socket.end();
        } else {
            socket.write(0x63);
            socket.write([(gameVersion >>> 24) & 0xff, (gameVersion >>> 16) & 0xff, (gameVersion >>> 8) & 0xff, gameVersion & 0xff]);
            let treatAsVer = socket.read(4);
            treatAsVer = (((((treatAsVer[0] << 8) ^ treatAsVer[1]) << 8) ^ treatAsVer[2]) << 8) ^ treatAsVer[3];
            console.log(treatAsVer);
            const km = this.checkPlayerDeny(name, authdata.slice(32, 40), treatAsVer);
            if (km[0] ?? false) {
                const bufmsg = stringToBuffer(km[1] ?? "reason not provided");
                socket.bundle();
                socket.write(0x55);
                socket.write(bigToBytes(bufmsg.length, 4));
                socket.write(bufmsg);
                socket.flush();
                return socket.end();
            }
            socket.write(0x63);
            const remAddr = socket.remoteAddress;
            socket.on("cClose", () => {
                this.connected.splice(this.connected.findIndex(v => v.remoteAddress === remAddr), 1);
            });
            this.connected.push(socket);
            const sData = Buffer.alloc(1024)//Maybe change this somehow
            const numWritten = this.serializeLevelData(sData, 0);
            socket.write(sData.subarray(0, numWritten));
        }
        // socket.write([nonce0, ...serverID]);
        return;
    }
    /**
     * @param {NSocket} socket
     */
    async socketManagementLoop (socket) {
        //
    }
    /**
     * @param {NSocket} socket
     */
    async socketStatusRequest (socket) {
        const statusid = await socket.read(1, {format:"number"});
        switch (statusid) {
            case 0x01:
                socket.write([...bigToBytes(this.max_players, 2), ...bigToBytes(this.connected.length, 2)]);
                break;
            case 0x02:
                socket.bundle();
                socket.write(stringToBuffer(this.name.slice(0, 20)));
                socket.write(bigToBytes(this.turn_interval, 2));
                socket.write(bigToBytes(this.level_height, 8));
                socket.write(bigToBytes(this.level_width, 8));
                socket.flush();
                break;
            case 0x03:
                socket.bundle();
                socket.write(this.maintain_auths.length & 0xff);
                for (const server of this.maintain_auths) {
                    socket.write(server.id + 1);
                    switch (server.id) {
                        case 0:
                            socket.write(server.address);
                            break;
                        case 1:
                            socket.write(server.address);
                            break;
                        case 2:
                            const s = stringToBuffer(server.address);
                            socket.write(bigToBytes(s.length, 4));
                            socket.write(s);
                            break;
                    }
                    socket.write(bigToBytes(server.port, 2));
                }
                socket.flush();
                break;
        }
        socket.end();
    }
    /**
     * @param {NSocket} socket
     */
    async socketConnection (socket) {
        const initopid = await socket.read(1, {format:"number"});
        if (initopid === 0x00) return;
        if (initopid === 0x63) return this.socketJoinServer(socket);
        if (initopid === 0x64) return this.socketStatusRequest(socket);
        if (initopid === 0x01) return this.socketManagementLoop(socket);
    }
}

const host = new Host();

const server = net.createServer((socket) => {host.socketConnection(NSocket.from(socket))}).listen(port);

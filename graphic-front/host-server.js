require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, createPublicKey, privateDecrypt } = require("crypto");
const dns_lookup = require("dns").lookup;
const { hash, Logger, formatBuf, stringToBuffer, asHex, NSocket, bigToBytes, bufferToString, mkTmp, SAddr, vConnect, SymmetricCipher, generateKeyPair, bytesToBig } = require("./defs");
const { readFileSync, existsSync } = require("fs");
const join_path = require("path").join;

const plugin_reg_path = join_path(__dirname, "data", "server", "plugins.json");

mkTmp(["data", "server", "plugins.json"], "{}");

let [ publicKey, privateKey ] = generateKeyPair();

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
 * @property {(name:string,id:number[],policies:object,counts:object) => [boolean, string]} [denyPlayerLogin] checks if a player login should be denied
 * @property {() => boolean} [spawnObstructionResolver] attempts to resolve player spawn obstruction. returns true on success and false otherwise NOT FULLY DEFINED YET
 */

/**@type {{name:string,type:"bool"|"number"|"string",id:number,value:boolean|number|string}[]} */
let server_policies = [
    {
        name : "allow-no-authentication-servers",
        type : "bool",
        id : 0,
        value : false,
    },
    {
        name : "max-player-count",
        type : "number",
        id : 1,
        value : 10,
    }
];


class Host {
    constructor () {
        this.name = "DEFAULT SERVER";
        const tiargv = argv.find(v => v.startsWith("--turnint="));
        const mpargv = argv.find(v => v.startsWith("--maxplay="));
        this.turn_interval = ((tiargv ? Number(tiargv.split("=")[1]) : null) ?? Number(process.env.turnint)) || 300;
        this.max_players = ((mpargv ? Number(mpargv.split("=")[1]) : null) ?? Number(process.env.maxplay)) || 10;
        /**@type {NSocket[]} */
        this.connected = [];
        this.level_age = 0;
        const lwargv = argv.find(v => v.startsWith("--lwidth="));
        const lhargv = argv.find(v => v.startsWith("--lheight="));
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
     * @returns {Promise<[number, number]>}
     */
    regenerateSecret () {
        const that = this;
        return new Promise(async (res, _) => {
            let good = 0;
            let fail = 0;
            that.secret = randomBytes(32);
            for (const addr of that.maintain_auths) {
                const sock = new NSocket();
                const bundle = logger.createLogBundle();
                bundle.setHeaderDashCount(5, 2);
                bundle.setIndentCount(7);
                bundle.mkHeader("AUTH UPDATE PROTOCOL");
                bundle.onFinish("CONNECTION TERMINATED", true);
                sock.on("error", () => {bundle.mkLog("connection error"); bundle.finish()});
                sock.on("cClose", ()=>{bundle.finish()});
                await new Promise((res, _) => {
                    sock.on("connect", async () => {
                        bundle.mkLog("connected to auth server");
                        sock.write([0x33, ...serverID]);
                        if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: auth server did not recognize server ID"); fail++; return res(sock.end());}
                        const nonce0 = await sock.read(32);
                        sock.write(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer(serverPassword), Buffer.from(serverID)]))), Buffer.from(nonce0)])));
                        if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: invalid server password"); fail++; return res(sock.end());}
                        let buf = await sock.read(2);
                        buf = await sock.read((buf[0] << 8) | buf[1]);
                        const ret = publicEncrypt(createPublicKey(Buffer.from(buf).toString("utf-8")), that.secret);
                        sock.write([(ret.length & 0xff00) >> 8, ret.length & 0xff]);
                        sock.write(ret);
                        if ((await sock.read(1))[0] === 0x55) {bundle.mkLog("failed: unknown reason"); fail ++; return res(sock.end());}
                        good ++;
                        bundle.mkLog("successfully updated server secret");
                        res(sock.end());
                    });
                    vConnect(addr, sock, (ret, hadErr) => {
                        bundle.mkLog(ret);
                        if (hadErr) {fail ++; res(bundle.finish()); }
                    });
                });
            }
            return res([good, fail]);
        });
    }
    /**
     * checks if a player should be denied access even with valid auth eg: player was banned
     * @param {string} name name of player
     * @param {number[]} id player id
     * @returns {[boolean, string]}
     */
    checkPlayerDeny (name, id) {
        for (const check of this.listeners.checkPlayerDeny) {
            const km = check.denyPlayerLogin(name, id, server_policies, {players:this.connected.length});
            if (km[0]) {
                return km;
            }
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
            socket.write([0x63, 0x00]);
            await socket.read(1);
            const km = this.checkPlayerDeny(name, authdata.slice(32, 40));
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
        }
        // socket.write([nonce0, ...serverID]);
        return;
    }
    /**
     * @param {NSocket} socket
     */
    async socketManagementLoop (socket) {
        const exp = publicKey.export({format:"pem",type:"spki"});
        socket.bundle();
        socket.write(bigToBytes(exp.length, 2));
        socket.write(stringToBuffer(exp, true));
        socket.flush();
        const password = bufferToString(privateDecrypt(privateKey, await socket.read(bytesToBig(await socket.read(2)), {format:"buffer"})));
        if (password !== process.env.HOST_ADMIN) {
            socket.write(0x55);
            return socket.end();
        }
        socket.write(0x63);
        const clientpubkey = createPublicKey(await socket.read(bytesToBig(await socket.read(2)), {format:"buffer"}));
        const symkey = randomBytes(32);
        const enckey = publicEncrypt(clientpubkey, symkey);
        socket.bundle();
        socket.write(bigToBytes(enckey.length, 2));
        socket.write(enckey);
        socket.flush();
        const cipher = new SymmetricCipher(symkey);
        socket.setCryptor(cipher);
        while (true) {
            const opid = await socket.read(1, {format:"number"});
            // terminate connection
            if (opid === 0x00) {
                return socket.end();
            }
            // set / get policies
            if (opid === 0x01) {
                const pid = await socket.read(1, {format:"number"});
                const ind = server_policies.findIndex(v => v.id === pid);
                if (ind === -1) {
                    socket.write(0x55);
                    continue;
                }
                socket.write(0x63);
                if (await socket.read(1, {format:"number"}) === 0x01) {
                    const type = server_policies[ind].type;
                    const tid = type === "bool" ? 0x01 : (type === "number" ? 0x02 : (type === "string" ? 0x03 : 0x04));
                    const val = server_policies[ind].value;
                    socket.bundle();
                    socket.write(tid);
                    if (tid === 1) {
                        socket.write(val ? 0x02 : 0x01);
                    } else if (tid === 2) {
                        socket.write(bigToBytes(val, 4));
                    } else if (tid === 3) {
                        socket.write(bigToBytes(val.length, 4));
                        socket.write(val);
                    }
                    socket.flush();
                } else {
                    let value;
                    switch (server_policies[ind].type) {
                        case "bool":
                            value = (await socket.read(1, {format:"number"}) === 0x02);
                            break;
                        case "number":
                            value = bytesToBig(await socket.read(4));
                            break;
                        case "string":
                            value = bufferToString(await socket.read(bytesToBig(await socket.read(4))));
                            break;
                    }
                    if (value !== undefined) {
                        server_policies[ind].value = value;
                        socket.write(0x63);
                    } else {
                        socket.write(0x55);
                    }
                }
            }
            // regen secret key
            if (opid === 0x02) {
                const data = await this.regenerateSecret();
                socket.write([data[0] & 0xff, data[1] & 0xff]);
            } else if (opid === 0x03) {
                socket.bundle();
                socket.write(bigToBytes(Object.keys(server_policies).length, 2));
                for (const key in server_policies) {
                    const val = server_policies[key];
                    socket.write(val.id);
                    const expkey = stringToBuffer(key);
                    socket.write(bigToBytes(expkey.length, 4));
                    socket.write(expkey);
                    if (val.type === "bool") {
                        socket.write(0x01);
                        socket.write(val.value ? 0x02 : 0x01);
                    } else if (val.type === "number") {
                        socket.write(0x02);
                        socket.write(bigToBytes(val.value, 4));
                    } else if (val.type === "string") {
                        socket.write(0x03);
                        const v2 = stringToBuffer(val.value);
                        socket.write(bigToBytes(v2.length, 4));
                        socket.write(v2);
                    } else {
                        socket.write(0x04);
                    }
                }
                socket.flush();
            }
        }
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
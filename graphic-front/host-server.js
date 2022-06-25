require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, createPublicKey, privateDecrypt } = require("crypto");
const { hash, Logger, formatBuf, stringToBuffer, NSocket, bigToBytes, bufferToString, mkTmp, SAddr, vConnect, SymmetricCipher, generateKeyPair, bytesToBig, randomInt } = require("./defs");
const { readFileSync, existsSync } = require("fs");
const join_path = require("path").join;
const gameVersion = 2;//update upon update
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
 * @param {Buffer} buf the buffer to write the data to
 * @param {number} off the offset at which to start writing the data
 * @param {number} x the old x-coordinate
 * @param {number} y the old y-coordinate
 * @param {number} xp the new x-coordinate
 * @param {number} yp the new y-coordinate
 * @returns {number} the new buffer offset
 */
function addMoveData (buf, off, x, y, xp, yp) {
    buf.writeUInt8(0x04, off);
    off++;
    buf.writeInt32BE(x, off);
    off += 4;
    buf.writeInt32BE(y, off);
    off += 4;
    buf.writeInt32BE(xp, off);
    off += 4;
    buf.writeInt32BE(yp, off);
    off += 4;
    return off;
}
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
 * @property {([number, number]) => [number, number]} [spawnObstructionResolver] determines spawing of any entity that is added to the level, even if it has not been proven that the desired spawn location is occupied. Input is a desired location in [x, y] format, output is the location chosen by the function in [x, y] format. Vanilla implementation returns same coordinates as input when unobstructed.
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

/**
 * @typedef Entity
 * @type {object}
 * @property {number} etype entity type
 * @property {number} termColor terminal ansi-style color code, 16 if no specific color for this entity
 * @property {number} health amount of health
 * @property {number[]} inventory inventory, inventory[<even number n>] contains the item ID for the slot <n / 2> while inventory[<n + 1>] contains the corresponding item amount, not surpassing 127. In entity-items, this only holds the item held by the entity-item.
 * @property {number} [regenFrame] regen frame, 0 - 15, dogs only. dog regenerates when (((level_age ^ regenFrame) & 0xf) == 0)
 */
class Entity {
    static invSizes = [0, 2, 15, 1, 0];
    /**
     * @param {number} type entityTypeID
     */
    constructor(type) {
        this.etype = type;
        this.termColor = 16;
        if (this.etype > 0) {
            this.termColor = randomInt(8, 16);
        }
        this.health = 10
        if (this.etype == 1) {
            this.regenFrame = randomInt(0, 15);
        }
        this.inventory = new Array(Entity.invSizes[type] * 2);
    }
    /**
     * serializes level data to a given buffer
     * @param {Buffer} buf buffer to write serialized data to
     * @param {number} off offset in buffer
     * @param {number} x entity's x-coordinate
     * @param {number} y entity's y-coordinate
     * @returns {number} the buffer offset after writing
     */
    serialize (buf, off, x, y) {
        buf.writeUInt8(this.etype, off);
        off++;
        if (this.etype == 4) {
            return off;
        }
        if ((this.etype == 1) || (this.etype == 2)) {
            buf.writeUInt32BE(Entity.invSizes[this.etype], off);
            off += 4;
            for (let i = 0; i < Entity.invSizes[this.etype]; i++) {
                if (this.inventory[i * 2] == undefined) {
                    buf.writeUInt8(0, off);
                    off++;
                    continue;
                }
                if (this.inventory[(i * 2) + 1] == 0) {
                    buf.writeInt8(0xff, off);
                }
                else {
                    buf.writeInt8(this.inventory[(i * 2) + 1], off);
                }
                off++;
                buf.writeUInt8(this.inventory[i * 2], off);
                off++;
            }
        }
        buf.writeUInt32BE(x, off);
        off += 4;
        buf.writeUInt32BE(y, off);
        off += 4;
        if (this.etype == 3) {
            //TODO serialize item data
        }
        if (this.etype == 2) {
            buf.writeBigInt64BE(BigInt(((this.termColor & 0xf) << 6) ^ (this.regenFrame & 0xf)), off);
        }
        else {
            buf.writeBigInt64BE(BigInt((this.termColor & 0xf) << 6), off);
        }
        off += 8;
        buf.writeInt16BE(this.health, off);
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
        /**@type {Map<number, Entity} */
        this.entities = new Map();
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
        for (const h in this.plugins) {
            if ((h.service_register & 1) == 1) {
                this.listeners.checkPlayerDeny.push(h);
            }
            if ((h.service_register & 2) == 2) {
                this.listeners.checkPlayerDeny.push(h.spawnObstructionResolver);
            }
        }
        for (let y = 0; y < this.level_height; y ++) {
            let row = new Array(0);
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
        /**@type {Buffer} */
        this.animationBuffer = Buffer.alloc(4096);//TODO set through lanch arguments
        this.regenerateSecret();
        this.reloadPlugins();
        /**@type {number} */
        this.bufOff = 0;
        this.entities.set((3 * this.level_width) + 2, new Entity(1));
        setInterval(this.animateFrame.bind(this), this.turn_interval);
    }
    /**
     * serializes level data to a given buffer
     * @param {Buffer} buf buffer to write serialized data to
     * @param {number} off offset of write start
     * @returns {number} the buffer offset after writing
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
        for (let i = 0; i < this.level_data.length; i++) {
            for (let j = 0; j < this.level_data[i].length; j++) {
                buf.writeUint8(this.level_data[i][j], off);
                off++;
            }
        }
        buf.writeBigInt64BE(BigInt(this.level_age), off);
        off += 8;
        buf.writeInt32BE(this.level_spawnx, off);
        off += 4;
        buf.writeInt32BE(this.level_spawny, off);
        off += 4;
        buf.writeInt32BE(this.entities.size, off);
        off += 4;
        this.entities.forEach(function (pos, ent) {
            let h = this.sptv(ent);
            off = pos.serialize(buf, off, h[0], h[1]);
        }, this);
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
                        bundle.mkLog(`successfully updated server secret for auth server at ${["IPv4", "IPv6", "hostname"][addr.id]} ${addr.address} port ${addr.port}` + (unsafe_logs ? ` to ${formatBuf(this.secret)}` : ""));
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
     * @param {number} ver player version
     * @returns {[boolean, string]}
     */
     checkPlayerDeny (name, id, ver) {
        for (const check of this.listeners.checkPlayerDeny) {
            const km = check.denyPlayerLogin(name, id, server_policies, {players:this.connected.length});
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
     * decides all entity spawn positions
     * @param {number} x desired x-coordinate
     * @param {number} y desired y-coordinate
     * @returns {[number, number]|null} decided spawn coordinates in [x, y] format, or null, if the spawn is denied
     */
    resolveSpawning (x, y) {
        for (const res of this.listeners.spawnObstructionResolver) {
            return res(x, y)
        }
        if ((typeof (this.entities.get(this.vpts(x, y)))) == undefined) {
            return null;
        }
        return ([x, y]);
    }
    /**
     * attempts to move the entity at one coordinate posititon by a given vector
     * 
     * @param {number} curX entity's current x-coordinate
     * @param {number} curY entity's current y-coordinate
     * @param {number} byX movement by X
     * @param {number} byY momvement by Y
     * @param {number} depth recursion depth
     * @returns {boolean} if the entity was moved by the specified vector by this call of the function
     */
    moveEntBy(oldX, oldY, byX, byY, depth) {
        if (depth > 15) {
            return false;
        };
        
        if (oldX + byX < 0) {
            byX = -oldX;
        }
        if (oldX + byX >= this.level_width) {
            byX = (this.level_width - oldX) - 1;
        }
        if (oldY + byY < 0) {
            byY = -oldY;
        }
        if (oldY + byY >= this.level_height) {
            byY = (this.level_height - oldY) - 1;
        }
        
        //TODO damage if depth is more than 0
        
        let sc = this.vpts(oldX + byX, oldY + byY);
        if ((typeof (this.entities.get(sc))) == 'undefined') {
            let po = this.vpts(oldX, oldY);
            let thg = this.entities.get(po);
            this.entities.delete(po);
            this.entities.set(sc, thg);
            this.bufOff = addMoveData(this.animationBuffer, this.bufOff, oldX, oldY, oldX + byX, oldY + byY);
            return true;
        }
        
        //TODO implement pushing other entities and item pickup
        
        return false;
    }
    /** 
     * @param {number} x x-coordinate
     * @param {number} y y-coordinate
     * @returns {number} scalar representation of the passed coordinates
    */
    vpts(x, y) {
        return ((y * this.level_width) + x);
    }
    /** 
     * @param {number} s scalar representation of the coordinates
     * @returns {[number, number]} vector representation of the passed scalar-represented coordinates
    */
    sptv(s) {
        return ([s % this.level_width, Math.floor(s / this.level_width)]);
    }
    /**
     * performs one animation frame and then sends the level update data to clients along with the 0x02 byte for rendering the frame client-side
     * @returns {number} amount of bytes written to each client from the animation buffer as part of the animation
     */
    animateFrame() {
        this.entities.forEach(function (treb, hee) {
            switch (treb.etype) {
                case (1):
                    let scpo = this.sptv(hee);
                    this.moveEntBy(scpo[0], scpo[1], randomInt((-1), 2), randomInt((-1), 2), 0);
                    break;
            }
        }, this);
        this.animationBuffer.writeUInt8(0x02, this.bufOff);
        this.bufOff++;
        let subToSend = this.animationBuffer.subarray(0, this.bufOff);
        for (let p = 0; p < this.connected.length; p++) {
            this.connected[p].write(subToSend);
        }
        let h = this.bufOff;
        this.bufOff = 0;
        return h;
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
            let treatAsVer = await socket.read(4);
            treatAsVer = (((((treatAsVer[0] << 8) ^ treatAsVer[1]) << 8) ^ treatAsVer[2]) << 8) ^ treatAsVer[3];
            const userID = authdata.slice(32, 40);
            logger.mkLog(`Player is joining, userID=${userID}`)
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
            const spawnPlace = this.resolveSpawning(this.level_spawnx, this.level_spawny);
            if (spawnPlace == null) {
                const bufmsg = stringToBuffer("spawn desired at the spawn point was denied");
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
            const sData = Buffer.alloc(1024)//TODO probably change this somehow
            const numWritten = this.serializeLevelData(sData, 0);
            socket.write(sData.subarray(0, numWritten));
            socket.write([(this.turn_interval >>> 8) & 0xff, this.turn_interval & 0xff]);
            const initialForm = new Entity(2);
            socket.write([(spawnPlace[0] >>> 24) & 0xff, (spawnPlace[0] >>> 16) & 0xff, (spawnPlace[0] >>> 8) & 0xff, spawnPlace[0] & 0xff, (spawnPlace[1] >>> 24) & 0xff, (spawnPlace[1] >>> 16) & 0xff, (spawnPlace[1] >>> 8) & 0xff, spawnPlace[1] & 0xff]);
            this.connected.push(socket);
            this.animationBuffer.writeUInt8(0x06, this.bufOff);
            this.bufOff++;
            this.bufOff = initialForm.serialize(this.animationBuffer, this.bufOff, spawnPlace[0], spawnPlace[1]);
            this.entities.set(this.vpts(spawnPlace[0], spawnPlace[1]), initialForm);
        }
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
                for (const pol of server_policies) {
                    const key = pol.name;
                    const val = pol.value;
                    socket.write(pol.id);
                    const expkey = stringToBuffer(key);
                    socket.write(bigToBytes(expkey.length, 4));
                    socket.write(expkey);
                    if (pol.type === "bool") {
                        socket.write(0x01);
                        socket.write(val ? 0x02 : 0x01);
                    } else if (pol.type === "number") {
                        socket.write(0x02);
                        socket.write(bigToBytes(val, 4));
                    } else if (pol.type === "string") {
                        socket.write(0x03);
                        const v2 = stringToBuffer(val);
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

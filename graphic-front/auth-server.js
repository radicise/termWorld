require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { NSocket, stringToBuffer, charsToBuffer, reduceToHex, asHex, generateKeyPair, SymmetricCipher, hash, Logger, formatBuf, bigToBytes, bufferToString, bytesToBig } = require("./defs");

const argv = process.argv;

const port = Number(argv[2]) || 15652;

const logger = new Logger("logs", "auth-log.log");

if (argv.includes("--clear-log")) logger.clearLogFile();

logger.mkLog(`INSTANCE START ON PORT: ${port}`, true);

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
const decryption_debugging = argv.includes("--debug-encryption");
if (unsafe_logs) logger.mkLog("UNSAFE LOGGING ENABLED");
logger.no_logging = argv.includes("--no-log");

if (logger.no_logging) {
    logger.no_logging = false;
    logger.mkLog("SERVER LOGGING DISABLED");
    logger.no_logging = true;
}

let [publicKey, privateKey] = generateKeyPair();

// let privateKey = createPrivateKey({format:"pem",key:"boom"});
// let publicKey = createPublicKey(privateKey);


let userIdDb = {
    "0000000000000000" : ["a", hash(Buffer.concat([stringToBuffer("a", false, 32, 0x20), Buffer.alloc(8, 0)]))],
    "0000000000000005" : ["guest", hash(Buffer.concat([stringToBuffer("password", false, 32, 0x20), Buffer.from([0, 0, 0, 0, 0, 0, 0, 5])]))],
};

let serverIdDb = {
    "0000000000000000" : [Buffer.from(hash(Buffer.concat([stringToBuffer("password", false, 32, 0x20), Buffer.alloc(8, 0)]))), Buffer.alloc(32, 0)],
    "0000000000000001" : [Buffer.from(hash(Buffer.concat([stringToBuffer("password", false, 32, 0x20), Buffer.from([0, 0, 0, 0, 0, 0, 0, 1])]))), Buffer.from([0x58, 0xe0, 0xd3, 0x14, 0x41, 0xd0, 0xe6, 0x6e, 0x8b, 0xa4, 0xf1, 0xd3, 0x4b, 0xc6, 0x46, 0x76, 0x10, 0xa7, 0x2f, 0x22, 0xbd, 0x04, 0x53, 0x2b, 0xf1, 0x8f, 0x0b, 0xb3, 0x35, 0xac, 0x72, 0xb0])],
}

let socketIDS = 0;

let banish = {};

setInterval(() => {
    for (const key in banish) {
        if (banish[key] <= 0) {
            delete banish[key];
        } else {
            banish[key] --;
        }
    }
}, 1000);

/**@type {{name:string,type:"bool"|"number"|"string",id:number,value:boolean|number|string}[]} */
let server_policies = [
    {
        name : "require-unique-names",
        type : "bool",
        id : 0,
        value : false,
    }
];

class Auth {
    constructor () {}
    /**
     * @param {NSocket} socket
     */
    async authConnection (socket) {
        function failed (nb) {
            if (!nb) {
                banish[socket.remoteAddress] = 30;
                logger.mkLog(`${usingID} was banished`);
            }
            socket.write(0x55);
            socket.end();
        }
        const usingID = socket.refid;
        let buf = await socket.read(8);
        const uID = reduceToHex(buf);
        // console.log(uID);
        // console.log(buf);
        if (!(uID in userIdDb)) {
            logger.mkLog(`${usingID} authentication failure: player id "${uID}" does not exist`);
            failed();
            return;
        }
        socket.write(0x63);
        const nonce1 = randomBytes(32);
        socket.write(nonce1);
        buf = await socket.read(72);
        const targetSID = reduceToHex(buf.slice(0, 8));
        const nonce0 = buf.slice(8, 40);
        const ghash = buf.slice(40, 72);
        let h2 = Array.from(userIdDb[uID][1]);
        const h1 = hash(Buffer.concat([Buffer.from(h2), nonce1, charsToBuffer(uID)]));
        // console.log(`${formatBuf(h2)}\n${formatBuf(nonce1)}\n${formatBuf(charsToBuffer(uID))}`);
        // console.log(`${formatBuf(h1)}\n${formatBuf(ghash)}`);
        if (h1.some((v, i) => v !== ghash[i])) {
            logger.mkLog(`${usingID} authentication failure: player secret not validated`);
            failed();
            return;
        }
        socket.write(0x63);
        if (!(reduceToHex(buf.slice(0, 8)) in serverIdDb)) {
            logger.mkLog(`${usingID} authentication failure: server id "${targetSID}" does not exist"`);
            failed();
            return;
        }
        socket.write(0x63);
        let h = Array.from(stringToBuffer(userIdDb[uID][0]));
        for (let i = h.length; i < 32; i ++) h.push(0x20);
        const b = Buffer.from(hash(Buffer.concat([Buffer.from(h), Buffer.from(nonce0), serverIdDb[targetSID][1], charsToBuffer(uID)])));
        logger.mkLog(`${usingID} was authenticated` + (unsafe_logs ? ` computed hash = ${logger.formatBuf(b)}` : ""));
        socket.write(b);
        socket.end();
    }
    /**
     * @param {NSocket} socket
     */
    async manConnection (socket) {
        let buf;
        const usingID = socket.refid;
        logger.mkLog(`${usingID} has id'd as config connection`);
        const exp = stringToBuffer(publicKey.export({type:"spki",format:"pem"}), true);
        socket.write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
        socket.write(exp);
        buf = await socket.read(bytesToBig(await socket.read(2)));
        const password = bufferToString(privateDecrypt(privateKey, Buffer.from(buf)), "utf-16");
        if (password !== process.env.AUTH_ADMIN) {
            logger.mkLog(`${usingID} failed to confirm auth as config`);
            socket.write(0x55);
            socket.end();
            return;
        }
        logger.mkLog(`${usingID} has been authenticated as config connection`);
        socket.write(0x00);
        const clientPub = createPublicKey(await socket.read(bytesToBig(await socket.read(2)), {format:"buffer"}));
        let symmetricKey;
        if (decryption_debugging) {
            symmetricKey = Buffer.of(0x73, 0x36, 0xf6, 0x1a, 0xe0, 0x2d, 0xac, 0x22, 0x3f, 0x87, 0x38, 0x0e, 0x87, 0x28, 0x3f, 0xdf, 0x1b, 0xbd, 0xfd, 0xca, 0x1f, 0x85, 0xbb, 0x7a, 0x92, 0x6b, 0x8e, 0x56, 0x4b, 0x65, 0x86, 0x9f);
        } else {
            symmetricKey = randomBytes(32);
        }
        const encrypted = publicEncrypt(clientPub, symmetricKey);
        socket.bundle();
        socket.write(bigToBytes(encrypted.length, 2));
        socket.write(encrypted);
        socket.flush();
        const cipher = new SymmetricCipher(symmetricKey);
        socket.setCryptor(cipher);
        let breakout;
        while (true) {
            buf = await socket.read(1);
            switch (buf[0]) {
                case 0x00:
                    console.log("EXITCODE");
                    socket.end();
                    breakout = true;
                    break;
                case 0x01:
                    const pid = await socket.read(1, {format:"number"});
                    const ind = server_policies.findIndex(v => v.id === pid);
                    if (ind === -1) {
                        socket.write(0x55);
                        break;
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
                        console.log(server_policies[ind]);
                    }
                    break;
                case 0x02:
                    buf = cipher.crypt(await socket.read(72));
                    const usrname = Buffer.from(buf.slice(0, 32)).toString("utf-8");
                    const usrid = buf.slice(32, 40);
                    const password = Buffer.from(buf.slice(40, 72)).toString("utf-8");
                    if (reduceToHex(usrid) in userIdDb) {
                        socket.write(0x01);
                        break;
                    }
                    userIdDb[reduceToHex(usrid)] = [usrname, password];
                    if (unsafe_logs) {
                        logger.mkLog(`${usingID} registered account "${usrname}" with password "${password}"`);
                    }
                    socket.write(0x03);
                    break;
                case 0x03:
                    socket.bundle();
                    socket.write(bigToBytes(Object.keys(server_policies).length, 2));
                    for (const pol of server_policies) {
                        const key = pol.name;
                        const val = pol.value;
                        socket.write(pol.id);
                        const expkey = stringToBuffer(key);
                        socket.write(bigToBytes(expkey.length, 4));
                        socket.write(expkey);
                        console.log(pol, val);
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
                    break;
                default:
                    socket.write(0xff);
                    // enwrite(0xff);
                    break;
            }
            if (breakout) break;
        }
        console.log("exited");
        socket.end();
    }
    /**
     * @param {NSocket} socket
     */
    async updateSecretConnection (socket) {
        const usingID = socket.refid;
        // console.log("X");
        const SID = await socket.read(8);
        logger.mkLog(`${usingID} has identified as server "${formatBuf(SID)}" with op for server secret update`);
        if (!(reduceToHex(SID) in serverIdDb)) {
            logger.mkLog(`SERVER SECRET ${usingID} - server id not found`);
            failed(true);
            return;
        }
        socket.write(0x63);
        const nonce0 = randomBytes(32);
        socket.write(nonce0);
        logger.mkLog(`SERVER SECRET ${usingID} - generated nonce`);
        let buf = await socket.read(32);
        const h1 = hash(Buffer.concat([serverIdDb[reduceToHex(SID)][0], nonce0]));
        if (h1.some((v, i) => v !== buf[i])) {
            logger.mkLog(`SERVER SECRET ${usingID} - failed to authenticate server`)
            failed(true);
            return;
        }
        socket.write(0x63);
        const exp = publicKey.export({type:"pkcs1", format:"der"});
        socket.write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
        socket.write(exp);
        buf = await socket.read(2);
        buf = await socket.read((buf[0] << 8) | buf[1]);
        const sec = privateDecrypt(privateKey, Buffer.from(buf));
        serverIdDb[reduceToHex(SID)][1] = sec;
        socket.write(0x63);
        logger.mkLog(`SERVER SECRET ${usingID} - updated server secret successfully` + (unsafe_logs ? ` to ${formatBuf(sec)}` : ""));
        socket.end();
    }
    /**
     * @param {NSocket} socket 
     */
    async socketConnection (socket) {
        if (socket.remoteAddress in banish) return;
        socket.refid = socketIDS;
        socketIDS ++;
        const usingID = socket.refid;
        logger.mkLog(`connection: ${usingID}, waiting for op`);
        const opid = await socket.read(1, {format:"number"});
        logger.mkLog(`${usingID} recieved opid: ${asHex(opid)}`);
        if (opid === 0x63) {
            this.authConnection(socket);
        } else if (opid === 0x01) {
            this.manConnection(socket);
        } else if (opid === 0x33) {
            this.updateSecretConnection(socket);
        }
    }
}

const auth = new Auth();

const server = net.createServer((socket) => {auth.socketConnection(NSocket.from(socket));}).listen(port);
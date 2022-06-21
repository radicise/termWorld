require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { NSocket, stringToBuffer, charsToBuffer, reduceToHex, asHex, generateKeyPair, SymmetricCipher, hash, Logger, formatBuf } = require("./defs");
// const { read } = require("./block-read");
// const { stringToBuffer, charsToBuffer, reduceToHex, asHex } = require("./string-to-buf");
// const { generateKeyPair, symmetricEncrypt, symmetricDecrypt } = require("./keygen");
// const { SymmetricCipher } = require("./encryption");
// const { hash } = require("./hash");
// const { Logger, formatBuf } = require("./logging");
// const { NSocket } = require("./socket");

const argv = process.argv;

const logger = new Logger("logs", "auth-log.log");

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

const server = net.createServer(
    /**
     * @param {NSocket} socket 
     */
    async (socket) => {
        socket = NSocket.from(socket);
        if (socket.remoteAddress in banish) return;
        function failed (nb) {
            if (!nb) {
                banish[socket.remoteAddress] = 30;
                logger.mkLog(`${usingID} was banished`);
            }
            socket.write(0x55);
            socket.end();
        }
        const usingID = socketIDS;
        socketIDS ++;
        let buf;
        logger.mkLog(`connection: ${usingID}, waiting for op`);
        // buf = await socket.read(1, {format:"number"});
        buf = (await socket.read(1))[0];
        // console.log(formatBuf(buf));
        logger.mkLog(`${usingID} recieved opid: ${asHex(buf)}`);
        if (buf === 0x63) {
            buf = await socket.read(8);
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
        } else if (buf === 0x32) {
            const exp = stringToBuffer(publicKey.export({type:"spki",format:"pem"}), true);
            socket.write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
            socket.write(exp);
            buf = await socket.read(4);
            const buf2 = await socket.read(buf[0] << 8 | buf[1]);
            console.log(buf2);
            const password = privateDecrypt(privateKey, Buffer.from(buf2)).toString("utf-8");
            buf = await socket.read(buf[2] << 8 | buf[3]);
            if (password !== process.env.AUTH_ADMIN) {
                socket.write(0x55);
                socket.end();
                return;
            }
            socket.write(0x00);
            const clientPub = createPublicKey(Buffer.from(buf));
            const symmetricKey = randomBytes(32);
            const encrypted = publicEncrypt(clientPub, symmetricKey);
            socket.write([(encrypted.length & 0xff00) >> 8, encrypted.length & 0xff]);
            socket.write(encrypted);
            const cipher = new SymmetricCipher(symmetricKey);
            /**
             * @param {number[] | number | Buffer} data
             */
            function enwrite (data) {
                data = Buffer.isBuffer(data) ? data : Buffer.from(Array.isArray(data) ? data : [data]);
                socket.write(cipher.crypt(data));
                // socket.write(symmetricEncrypt(symmetricKey, data));
            }
            let breakout;
            while (true) {
                buf = cipher.crypt(await read(socket, 1, {default:0x00}));
                switch (buf[0]) {
                    case 0x00:
                        console.log("EXITCODE");
                        socket.end();
                        breakout = true;
                        break;
                    case 0x01:
                        // console.log(`connection ${usingID} account registration attempt`);
                        buf = cipher.crypt(await socket.read(72));
                        const usrname = Buffer.from(buf.slice(0, 32)).toString("utf-8");
                        const usrid = buf.slice(32, 40);
                        const password = Buffer.from(buf.slice(40, 72)).toString("utf-8");
                        // console.log(`DETAILS\n${formatBuf(usrname)}, ${formatBuf(usrid)}, ${formatBuf(password)}`);
                        if (reduceToHex(usrid) in userIdDb) {
                            enwrite(0x01);
                            break;
                        }
                        userIdDb[reduceToHex(usrid)] = [usrname, password];
                        if (unsafe_logs) {
                            logger.mkLog(`${usingID} registered account "${usrname}" with password "${password}"`);
                        }
                        enwrite(0x03);
                        break;
                    default:
                        enwrite(0xff);
                        break;
                }
                if (breakout) break;
            }
            console.log("exited");
        } else if (buf === 0x33) {
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
            const exp = stringToBuffer(publicKey.export({format:"pem",type:"spki"}), true);
            socket.write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
            socket.write(exp);
            buf = await socket.read(2);
            buf = await socket.read((buf[0] << 8) | buf[1]);
            const sec = privateDecrypt(privateKey, Buffer.from(buf));
            serverIdDb[reduceToHex(SID)][1] = sec;
            socket.write(0x63);
            logger.mkLog(`SERVER SECRET ${usingID} - updated server secret successfully` + (unsafe_logs ? ` to ${formatBuf(sec)}` : ""));
        }
        socket.end();
    }
).listen(Number(argv[2]) || 15652);
require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { read } = require("./block-read");
const { stringToBuffer, charsToBuffer } = require("./string-to-buf");
const { generateKeyPair, symmetricEncrypt, symmetricDecrypt } = require("./keygen");
const { SymmetricCipher } = require("./encryption");
const { hash } = require("./hash");
const { Logger, formatBuf } = require("./logging");

const argv = process.argv;

function getTimeStamp () {
    const d = new Date();
    return `(${["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"][d.getDay()]} ${d.getMonth()} ${d.getDate()} ${d.getFullYear()} @ ${d.getHours()}:${d.getMinutes()}:${d.getSeconds()})`;
}

const logger = new Logger("logs", "auth-log.log");

if (argv.includes("--clear-log")) logger.clearLogFile();

logger.mkLog(`INSTANCE START ${getTimeStamp()}`);

let no_exit = false;

function onexit () {
    logger.no_logging = false;
    logger.mkLog(`INSTANCE END ${getTimeStamp()}`);
    if (!no_exit) {
        process.exit();
    }
}

process.on("SIGINT", onexit);
process.on("SIGUSR1", onexit);
process.on("SIGUSR2", onexit);
process.on("uncaughtException", (l)=>{logger.no_logging=false;logger.mkLog(`UNHANDLED EXCEPTION ${getTimeStamp()}: ${l}`);console.log(l?.stack);onexit()});

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
    "0000000000000005" : ["guest", hash(Buffer.concat([stringToBuffer("password"), Buffer.from([0, 0, 0, 0, 0, 0, 0, 5])]))],
};

let serverIdDb = {
    "0000000000000000" : [Buffer.from(hash(Buffer.concat([stringToBuffer("password"), Buffer.alloc(8, 0)]))), Buffer.alloc(32, 0)],
    "0000000000000001" : [Buffer.from(hash(Buffer.concat([stringToBuffer("password"), Buffer.from([0, 0, 0, 0, 0, 0, 0, 1])]))), Buffer.from([0x58, 0xe0, 0xd3, 0x14, 0x41, 0xd0, 0xe6, 0x6e, 0x8b, 0xa4, 0xf1, 0xd3, 0x4b, 0xc6, 0x46, 0x76, 0x10, 0xa7, 0x2f, 0x22, 0xbd, 0x04, 0x53, 0x2b, 0xf1, 0x8f, 0x0b, 0xb3, 0x35, 0xac, 0x72, 0xb0])],
}

const c = "0123456789abcdef";


/**
 * converts n to hex representation
 * @param {number|number[]}n thing to convert
 * @returns {string}
 */
function asHex (n) {
    if (!Array.isArray(n)) {
        return c[n >> 4] + c[n & 0x0f];
    }
    let f = n.map(v => c[v >> 4]+c[v & 0x0f]);
    return f;
}

/**
 * reduces buffer contents to hex representation
 * @param {number[]|Buffer} buf buffer to reduce
 * @returns {string}
 */
function reduceToHex (buf) {
    return buf.map(v => c[v >> 4]+c[v & 0x0f]).join("");
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

const server = net.createServer(async (socket) => {
    // let socket = 
    // if (banish.includes(socket.remoteAddress)) {socket.end();return;}
    // banish.push(socket.remoteAddress);
    if (socket.remoteAddress in banish) {
        return;
    }
    function failed (nb) {
        if (!nb) {
            banish[socket.remoteAddress] = 30;
            logger.mkLog(`${usingID} was banished`);
        }
        write(0x55);
        socket.end();
    }
    const usingID = socketIDS;
    socketIDS ++;
    socket.pause();
    function write (data) {
        if (!Array.isArray(data) && !Buffer.isBuffer(data)) {
            data = [data];
        }
        socket.write(Buffer.from(data));
    }
    let buf;
    logger.mkLog(`connection: ${usingID}, waiting for op`);
    buf = await read(socket, 1);
    logger.mkLog(`${usingID} recieved opid: ${asHex(buf[0])}`);
    if (buf[0] === 0x63) {
        buf = await read(socket, 8);
        const uID = reduceToHex(buf);
        // console.log(uID);
        // console.log(buf);
        if (!(uID in userIdDb)) {
            logger.mkLog(`${usingID} authentication failure: player id "${uID}" does not exist`);
            failed();
            return;
        }
        write(0x63);
        const nonce1 = randomBytes(32);
        socket.write(nonce1);
        buf = await read(socket, 72);
        const targetSID = reduceToHex(buf.slice(0, 8));
        const nonce0 = buf.slice(8, 40);
        const ghash = buf.slice(40, 72);
        let h2 = Array.from(userIdDb[uID][1]);
        for (let i = h2.length; i < 32; i ++) h2.push(0x20);
        const h1 = hash(Buffer.concat([Buffer.from(h2), nonce1, charsToBuffer(uID)]));
        console.log(`${formatBuf(h2)}\n${formatBuf(nonce1)}\n${formatBuf(charsToBuffer(uID))}`);
        console.log(`${formatBuf(h1)}\n${formatBuf(ghash)}`);
        if (h1.some((v, i) => v !== ghash[i])) {
            logger.mkLog(`${usingID} authentication failure: player secret not validated`);
            failed();
            return;
        }
        write(0x63);
        if (!(reduceToHex(buf.slice(0, 8)) in serverIdDb)) {
            logger.mkLog(`${usingID} authentication failure: server id "${targetSID}" does not exist"`);
            failed();
            return;
        }
        write(0x63);
        let h = Array.from(stringToBuffer(userIdDb[uID][0]));
        for (let i = h.length; i < 32; i ++) h.push(0x20);
        // console.log(h);
        // console.log(Buffer.from(h));
        // console.log(Buffer.from(nonce0));
        // console.log(serverIdDb[targetSID]);
        // console.log(Buffer.from([0, 0, 0, 0, 0, 0, 0, 5]));
        const b = Buffer.from(hash(Buffer.concat([Buffer.from(h), Buffer.from(nonce0), serverIdDb[targetSID][1], charsToBuffer(uID)])));
        logger.mkLog(`${usingID} was authenticated` + (unsafe_logs ? ` computed hash = ${logger.formatBuf(b)}` : ""));
        socket.write(b);
        // console.log(buf);
    } else if (buf[0] === 0x32) {
        buf = await read(socket, 1);
        const exp = stringToBuffer(publicKey.export({type:"spki",format:"pem"}), true);
        // console.log(exp, exp.length, exp.length & 0xff00, exp.length & 0xff);
        write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
        socket.write(exp);
        buf = await read(socket, 4);
        const buf2 = await read(socket, buf[0] << 8 | buf[1]);
        console.log(buf2);
        const password = privateDecrypt(privateKey, Buffer.from(buf2)).toString("utf-8");
        buf = await read(socket, buf[2] << 8 | buf[3]);
        // console.log(password, process.env.AUTH_ADMIN);
        if (password !== process.env.AUTH_ADMIN) {
            write(0x55);
            socket.end();
            return;
        }
        write(0x00);
        const clientPub = createPublicKey(Buffer.from(buf));
        const symmetricKey = randomBytes(32);
        const encrypted = publicEncrypt(clientPub, symmetricKey);
        write([(encrypted.length & 0xff00) >> 8, encrypted.length & 0xff]);
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
                    buf = cipher.crypt(await read(socket, 72));
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
    } else if (buf[0] === 0x33) {
        const SID = await read(socket, 8);
        logger.mkLog(`${usingID} has identified as server "${logger.formatBuf(SID)}" with op for server secret update`);
        if (!(reduceToHex(SID) in serverIdDb)) {
            logger.mkLog(`SERVER SECRET ${usingID} - server id not found`);
            failed(true);
            return;
        }
        write(0x63);
        const nonce0 = randomBytes(32);
        socket.write(nonce0);
        logger.mkLog(`SERVER SECRET ${usingID} - generated nonce`);
        buf = await read(socket, 32);
        if (hash(Buffer.concat([serverIdDb[reduceToHex(SID)][0], nonce0])).some((v, i) => v !== buf[i])) {
            logger.mkLog(`SERVER SECRET ${usingID} - failed to authenticate server`)
            failed(true);
            return;
        }
        write(0x63);
        const exp = stringToBuffer(publicKey.export({format:"pem",type:"spki"}), true);
        // console.log(exp.toString("utf-8"));
        // createPublicKey(exp);
        write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
        socket.write(exp);
        buf = await read(socket, 2);
        buf = await read(socket, (buf[0] << 8) | buf[1]);
        const sec = privateDecrypt(privateKey, Buffer.from(buf));
        serverIdDb[reduceToHex(SID)][1] = sec;
        write(0x63);
        logger.mkLog(`SERVER SECRET ${usingID} - updated server secret successfully` + (unsafe_logs ? ` to ${logger.formatBuf(sec)}` : ""));
    }
    socket.end();
}).listen(Number(argv[2]) || 15652);

// const interface = readline.createInterface(process.stdin, process.stdout);

// interface.on("line", (input) => {
//     if (input === "clear banish") {
//         banish = [];
//     }
// });
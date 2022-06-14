require("dotenv").config();
const net = require("net");
const { randomBytes, publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { read } = require("./block-read");
const { stringToBuffer, charsToBuffer } = require("./string-to-buf");
const { generateKeyPair, symmetricEncrypt, symmetricDecrypt } = require("./keygen");
const { hash } = require("./hash");
// const readline = require("readline");
const { Logger } = require("./logging");

function getTimeStamp () {
    const d = new Date();
    return `(${["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"][d.getDay()]} ${d.getMonth()} ${d.getDate()} ${d.getFullYear()} @ ${d.getHours()}:${d.getMinutes()}:${d.getSeconds()})`;
}

const logger = new Logger("logs", "auth-log.log");

logger.mkLog(`INSTANCE START ${getTimeStamp()}`);

let no_exit = false;

function onexit () {
    logger.no_logging = false;
    logger.mkLog(`INSTANCE END ${getTimeStamp()}`);
    if (!no_exit) {
        process.exit();
    }
}

// process.on("exit", ()=>{no_exit=true;onexit();no_exit=false;});
process.on("SIGINT", onexit);
process.on("SIGUSR1", onexit);
process.on("SIGUSR2", onexit);
process.on("uncaughtException", (l)=>{logger.no_logging=false;logger.mkLog(`UNHANDLED EXCEPTION ${getTimeStamp()}: ${l}`);onexit()});

const argv = process.argv;

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
    "0000000000000005" : ["guest", "password"],
};

let serverIdDb = {
    "0000000000000001" : Buffer.from([0x58, 0xe0, 0xd3, 0x14, 0x41, 0xd0, 0xe6, 0x6e, 0x8b, 0xa4, 0xf1, 0xd3, 0x4b, 0xc6, 0x46, 0x76, 0x10, 0xa7, 0x2f, 0x22, 0xbd, 0x04, 0x53, 0x2b, 0xf1, 0x8f, 0x0b, 0xb3, 0x35, 0xac, 0x72, 0xb0]),
}

const c = "0123456789abcdef";


/**
 * converts n to hex representation
 * @param {Number[]|Number} n thing to convert
 * @returns {String[]|String}
 */
function asHex (n) {
    const isa = Array.isArray(n);
    if (!isa) {n = [n];}
    let f = n.map(v => c[v >> 4]+c[v & 0x0f]);
    if (!isa) {f = f[0];}
    return f;
}

/**
 * reduces buffer contents to hex representation
 * @param {Int8Array} buf buffer to reduce
 * @returns {String}
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
    // if (banish.includes(socket.remoteAddress)) {socket.end();return;}
    // banish.push(socket.remoteAddress);
    if (socket.remoteAddress in banish) {
        return;
    }
    function failed () {
        banish[socket.remoteAddress] = 30;
        logger.mkLog(`${usingID} was banished`);
        write(0x55);
        socket.end();
    }
    const usingID = socketIDS;
    socketIDS ++;
    socket.pause();
    function write (data) {
        if (!Array.isArray(data)) {
            data = [data];
        }
        socket.write(Buffer.from(data));
    }
    let buf = [];
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
        let h2 = Array.from(stringToBuffer(userIdDb[uID][1]));
        for (let i = h2.length; i < 32; i ++) h2.push(0x20);
        const h1 = hash(Buffer.concat([Buffer.from(h2), nonce1, charsToBuffer(uID)]));
        // console.log(h1, ghash);
        if (h1.some((v, i) => v !== ghash[i])) {
            logger.mkLog(`${usingID} authentication failure: player secret not validated`);
            failed();
            return;
        }
        write(0x63);
        if (!(reduceToHex(buf.slice(0, 8)) in serverIdDb)) {
            logger.mkLog(`${usingID} authentication failure: server id "${reduceToHex(targetSID)}" does not exist"`);
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
        const b = Buffer.from(hash(Buffer.concat([Buffer.from(h), Buffer.from(nonce0), serverIdDb[targetSID], charsToBuffer(uID)])));
        const x = asHex(Array.from(b)).toString();
        logger.mkLog(`${usingID} computed hash: <Buffer ${x.slice(1, x.length-1)}>`);
        socket.write(b);
        // console.log(buf);
    } else if (buf[0] === 0x32) {
        buf = await read(socket, 1);
        const exp = stringToBuffer(publicKey.export({type:"spki",format:"pem"}));
        // console.log(exp, exp.length, exp.length & 0xff00, exp.length & 0xff);
        write([(exp.length & 0xff00) >> 8, exp.length & 0xff]);
        socket.write(exp);
        buf = await read(socket, 4);
        buf = await read(socket, buf[0] << 8 | buf[1]);
        // console.log(buf);
        const password = privateDecrypt(privateKey, Buffer.from(buf)).toString("utf-8");
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
        /**
         * @param {Number[]} data
         */
        function enwrite (data) {
            data = Buffer.from(Array.isArray(data) ? data : [data]);
            socket.write(symmetricEncrypt(symmetricKey, data));
        }
        let breakout;
        while (true) {
            buf = await read(socket, 1, {default:0x00,transform:{f:symmetricDecrypt,args:[symmetricKey]}});
            switch (buf[0]) {
                case 0x00:
                    console.log("EXITCODE");
                    socket.end();
                    breakout = true;
                    break;
                case 0x01:
                    console.log(`connection ${usingID} account registration attempt`);
                    buf = await read(socket, 72, {transform:{f:symmetricDecrypt,args:[symmetricKey]}});
                    const usrname = Buffer.from(buf.slice(0, 32)).toString("utf-8");
                    const usrid = buf.slice(32, 40);
                    const password = Buffer.from(buf.slice(40, 72)).toString("utf-8");
                    if (reduceToHex(usrid) in userIdDb) {
                        enwrite(0x01);
                        break;
                    }
                    userIdDb[reduceToHex(usrid)] = [usrname, password];
                    enwrite(0x03);
                    break;
                default:
                    enwrite(0xff);
                    break;
            }
            if (breakout) break;
        }
        console.log("exited");
    }
    socket.end();
}).listen(Number(argv[2]) || 15652);

// const interface = readline.createInterface(process.stdin, process.stdout);

// interface.on("line", (input) => {
//     if (input === "clear banish") {
//         banish = [];
//     }
// });
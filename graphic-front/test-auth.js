const net = require("net");
const { read } = require("./block-read");
const { hash } = require("./hash");
const { stringToBuffer } = require("./string-to-buf");
const { publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { generateKeyPair, symmetricEncrypt, symmetricDecrypt } = require("./keygen");
const { formatBuf } = require("./logging");

let [publicKey, privateKey] = generateKeyPair();

const clientID = Array.from(Buffer.alloc(8, 0));
const userName = Array.from(Buffer.alloc(32, 0));
const password = Array.from(Buffer.alloc(32, 0));

const port = [15651, 15652];
const host = ["127.0.0.1", "127.0.0.1"];

let c = 1;

let auth = new net.Socket();
// let server = new net.Socket();

const cv = "0123456789abcdef";
const asHex = (a) => a.map(v => cv[v >> 4] + cv[v & 0x0f]);

auth.pause();
// server.pause();

auth.connect(port[1], host[1], () => {if(c){start();}c=1;});
// server.connect(port[0], host[0], () => {if(c){start();}c=1;});

function server_write (data) {
    if (!Array.isArray(data)) {
        data = [data];
    }
    server.write(Buffer.from(data));
}

function auth_write (data) {
    if (!Array.isArray(data)) {
        data = [data];
    }
    auth.write(Buffer.from(data));
}

async function start () {
    auth_write([0x33, 1, 0, 0, 0, 0, 0, 0, 1]);
    if ((await read(auth, 1))[0] === 0x55) {
        console.log("server not exist");
        return;
    }
    const nonce0 = await read(auth, 32);
    auth.write(Buffer.from(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer("password"), Buffer.from([1,0,0,0,0,0,0,1])]))), Buffer.from(nonce0)]))));
    if ((await read(auth, 1))[0] === 0x55) {
        console.log("invalid server login");
        return;
    }
    let buf = await read(auth, 2);
    buf = await read(auth, buf[0] << 8 | buf[1]);
    const pubkey = createPublicKey(Buffer.from(buf).toString("utf-8"));
    const enc = publicEncrypt(pubkey, Buffer.from([0x00, 0xff]));
    auth_write([(enc.length & 0xff00) >> 8, enc.length & 0xff]);
    auth.write(enc);
    if ((await read(auth, 1))[0] === 0x55) {
        console.log("failed");
        return;
    }
    console.log("success");
}

async function start3 () {
    auth_write([0x32, 0x00]);
    let buf = await read(auth, 2);
    console.log(buf);
    buf = await read(auth, buf[0] << 8 | buf[1]);
    console.log(Buffer.from(buf).toString("utf-8"));
    // let ret = Array.from(Buffer.alloc(32, 0));
    let ret = stringToBuffer("password");
    const encryptedPassword = publicEncrypt(createPublicKey(Buffer.from(buf).toString("utf-8")), Buffer.from(ret));
    const exp = stringToBuffer(publicKey.export({format:"pem",type:"spki"}));
    auth_write([(encryptedPassword.length & 0xff00) >> 8, encryptedPassword.length & 0xff, (exp.length & 0xff00) >> 8, exp.length & 0xff]);
    auth.write(encryptedPassword);
    auth.write(exp);
    buf = await read(auth, 1);
    if (buf[0] === 0x55) {
        console.log("invalid password");
        return;
    }
    buf = await read(auth, 2);
    const symmetricKey = await read(auth, buf[0] << 8 | buf[1], {transform:{f:privateDecrypt,args:[privateKey]}});
    /**
     * @param {Number[]} data
     */
    function enwrite (data) {
        data = Buffer.from(Array.isArray(data) ? data : [data]);
        auth.write(symmetricEncrypt(symmetricKey, data));
    }
    if (process.argv[2] === "create-user") {
        //
    }
}

async function start2 () {
    server_write(userName);
    let buf = [];
    buf = await read(server, 40);
    console.log(buf);
    const nonce0 = buf.slice(0, 32);
    const serverID = buf.slice(32, 40);
    auth_write([0x63, ...clientID]);
    buf = await read(auth, 1);
    console.log(asHex(buf));
    if (buf[0] === 0x55) {
        return;
    }
    buf = await read(auth, 32);
    console.log(asHex(buf));
    const nonce1 = buf;
    auth_write([...serverID, ...nonce0, ...hash([...password, ...nonce1, ...clientID])]);
    buf = await read(auth, 1);
    // console.log(asHex(buf)[0]);
    if (buf[0] == 0x55) {
        console.log("bad secret");
        return;
    }
    buf = await read(auth, 1);
    if (buf[0] === 0x55) {
        console.log("no server");
        return;
    }
    buf = await read(auth, 32);
    console.log(buf);
    const authHash = buf;
    server_write([...userName, ...clientID, ...authHash]);
    buf = await read(server, 1);
    if (buf[0] === 0x55) {
        console.log("server rejection");
        return;
    }
    console.log("authenticated");
}
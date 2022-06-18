const { publicEncrypt, privateDecrypt, createPublicKey } = require("crypto");
const { NSocket, formatBuf, SymmetricCipher, generateKeyPair, stringToBuffer, hash, asHex } = require("./defs");

let [publicKey, privateKey] = generateKeyPair();

const clientID = Array.from(Buffer.alloc(8, 0));
const userName = Array.from(Buffer.alloc(32, 0));
const password = Array.from(Buffer.alloc(32, 0));

const port = [15651, 15652];
const host = ["127.0.0.1", "127.0.0.1"];

let c = 1;

let auth = new NSocket();
// let server = new net.Socket();

// const cv = "0123456789abcdef";
// const asHex = (a) => a.map(v => cv[v >> 4] + cv[v & 0x0f]);

auth.connect(port[1], host[1], () => {if(c){start();}c=1;});
// server.connect(port[0], host[0], () => {if(c){start();}c=1;});

async function start3 () {
    auth.write([0x33, 1, 0, 0, 0, 0, 0, 0, 1]);
    if ((await auth.read(1))[0] === 0x55) {
        console.log("server not exist");
        return;
    }
    const nonce0 = await auth.read(32);
    auth.write(Buffer.from(hash(Buffer.concat([Buffer.from(hash(Buffer.concat([stringToBuffer("password"), Buffer.from([1,0,0,0,0,0,0,1])]))), Buffer.from(nonce0)]))));
    if ((await auth.read(1))[0] === 0x55) {
        console.log("invalid server login");
        return;
    }
    let buf = await auth.read(2);
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

async function start () {
    auth.write(0x32);
    let buf = await read(auth, 2);
    // console.log(buf);
    buf = await auth.read(buf[0] << 8 | buf[1]);
    // console.log(Buffer.from(buf).toString("utf-8"));
    // let ret = Array.from(Buffer.alloc(32, 0));
    let ret = stringToBuffer("password", true);
    const encryptedPassword = publicEncrypt(createPublicKey(Buffer.from(buf).toString("utf-8")), Buffer.from(ret));
    const exp = stringToBuffer(publicKey.export({format:"pem",type:"spki"}), true);
    auth.write([(encryptedPassword.length & 0xff00) >> 8, encryptedPassword.length & 0xff, (exp.length & 0xff00) >> 8, exp.length & 0xff]);
    auth.write(encryptedPassword);
    auth.write(exp);
    buf = await auth.read(1);
    if (buf[0] === 0x55) {
        console.log("invalid password");
        return;
    }
    buf = await auth.read(2);
    const cipher = new SymmetricCipher(privateDecrypt(privateKey, Buffer.from(await auth.read(buf[0] << 8 | buf[1]))));
    /**
     * @param {number[] | number | Buffer} data
     */
    function enwrite (data) {
        data = Buffer.isBuffer(data) ? data : Buffer.from(Array.isArray(data) ? data : [data]);
        auth.write(cipher.crypt(data));
        // auth.write(symmetricEncrypt(symmetricKey, data));
    }
    if (process.argv[2] === "create-user") {
        enwrite(0x01);
        const ret = Buffer.concat([Buffer.alloc(32, 0x20), Buffer.concat([Buffer.alloc(7, 0), Buffer.from([0x05])]), Buffer.alloc(32, 0x20)]);
        // console.log(ret);
        enwrite(ret);
        const res = cipher.crypt(await auth.read(1));
        if (res === 0x01) {
            console.log("userid in use");
        } else if (res === 0x02) {
            console.log("username in use");
        } else if (res === 0x03) {
            console.log("success");
        }
    }
    enwrite(0x00);
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
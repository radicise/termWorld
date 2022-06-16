const net = require("net");
const { hash } = require("../hash");
const { read } = require("../block-read");
const { formatBuf } = require("../logging");
const { charsToBuffer, stringToBuffer } = require("../string-to-buf");
const { log, fatal_err, invoke } = require("../js/common");

/**@type {net.Socket} */
let serverConnection;
/**@type {net.Socket} */
let authConnection;

/**
 * authenticates a user
 * @param {number[]} userName
 * @param {number[]} password
 * @param {number[]} clientID
 * @returns {Promise<void|true>}
 */
async function authenticate (userName, password, clientID) {
    const server = serverConnection;
    const auth = authConnection;
    function server_write (data) {
        if (Buffer.isBuffer(data)) {server.write(data);return;}
        if (!Array.isArray(data)) {data = [data];}
        server.write(Buffer.from(data));
    }
    function auth_write (data) {
        if (Buffer.isBuffer(data)) {auth.write(data);return;}
        if (!Array.isArray(data)) {data = [data];}
        auth.write(Buffer.from(data));
    }
    server_write(userName);
    log('after init write');
    let buf = [];
    buf = await read(server, 40);
    log(formatBuf(buf));
    const nonce0 = buf.slice(0, 32);
    const serverID = buf.slice(32, 40);
    auth_write([0x63, ...clientID]);
    buf = await read(auth, 1);
    // log(formatBuf(buf));
    if (buf[0] === 0x55) {
        return;
    }
    buf = await read(auth, 32);
    log(formatBuf(buf));
    const nonce1 = buf;
    const sec = [...serverID, ...nonce0, ...hash([...hash([...password, ...clientID]), ...nonce1, ...clientID])];
    log(`password: ${formatBuf(password)}`);
    log(`hash1: ${formatBuf(hash([...password, ...clientID]))}\nnonce1: ${formatBuf(nonce1)}\nclientID: ${formatBuf(clientID)}`);
    log(formatBuf(sec));
    auth_write(sec);
    buf = await read(auth, 1);
    // console.log(asHex(buf)[0]);
    if (buf[0] == 0x55) {
        log("bad secret");
        return;
    }
    buf = await read(auth, 1);
    if (buf[0] === 0x55) {
        log("no server");
        return;
    }
    buf = await read(auth, 32);
    log(formatBuf(buf));
    const authHash = buf;
    server_write([...userName, ...clientID, ...authHash]);
    buf = await read(server, 1);
    if (buf[0] === 0x55) {
        log("server rejection");
        return;
    }
    log("authenticated");
    return true;
}

async function main () {
    /**@type {string[]} */
    const argv = await invoke("request:args");

    // log(argv);

    if (argv.length < 7) {
        fatal_err("insufficent args");
        return;
    }
    // try{
    let username = Buffer.alloc(32, 0x20);
    stringToBuffer(argv[2]).copy(username);
    // username.write(argv[2]);
    username = Array.from(username);
    let password = Buffer.alloc(32, 0x20);
    stringToBuffer(argv[3]).copy(password);
    // password.write(argv[3]);
    password = Array.from(password);
    const userid = Array.from(charsToBuffer(argv[4]));
    log(formatBuf(username));
    log(formatBuf(password));
    log(formatBuf(userid));
    // } catch (e) {log(e.toString(), e.stack.toString());}
    // const dthconv = "0123456789abcdef";
    // const pfuncA0001 = (s) => {
    //     s = s.padEnd(16, "0");
    //     let f = [];
    //     for (let i = 0; i < s.length; i += 2) {
    //         n = s[i] + s[i+1];
    //         f.push(dthconv.indexOf(n[0]) << 4 | dthconv.indexOf(n[1]));
    //     }
    //     return f;
    // };
    // const userid = pfuncA0001(argv[4]);
    if (username.length > 32) {log("name too long");return;}
    if (password.length > 32) {log("password too long");return;}
    if (userid.length !== 8) {log("invalid userid");return;}
    const servePort = Number(argv[5].slice(argv[5].indexOf(":")+1)) || 15651;
    const serveHost = argv[5].slice(0,argv[5].indexOf(":")) || "127.0.0.1";
    const authPort = Number(argv[6].slice(argv[6].indexOf(":")+1)) || 15652;
    const authHost = argv[6].slice(0,argv[6].indexOf(":")) || "127.0.0.1";
    log(serveHost, servePort, authHost, authPort);
    // const server = net.createServer((c) => {
    //     c.on("data", (d) => {log(`${d}`)});
    // }).listen(servePort, serveHost);
    serverConnection = new net.Socket();
    await new Promise((res, _) => {
        serverConnection.connect(servePort, serveHost, () => {
            // serverConnection.write("connection established");
            res();
            // setTimeout(res, 200);
        });
    });
    serverConnection.pause();
    // serverConnection.on("data", serveCB);
    authConnection = new net.Socket();
    await new Promise((res, _) => {
        authConnection.connect(authPort, authHost, () => {
            res();
        });
    });
    authConnection.pause();
    // authConnection.on("data", authCB);
    log("after connection");
    if (!(await authenticate(username, password, userid))) {
        authConnection.end();
        serverConnection.end();
    }
    // serverConnection.connect(servePort, serveHost, () => {
    //     serverConnection.write("data");
    // });
    // serverConnection.write("data", "utf-8");
}

try {
    main();
} catch (e) {
    fatal_err(e.toString());
}
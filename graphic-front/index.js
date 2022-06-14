const net = require("net");
const { ipcRenderer } = require("electron");
const { hash } = require("./hash");
const { read } = require("./block-read");

function log (...data) {
    ipcRenderer.send("console:log", data.map(v => JSON.stringify(v)));
}

/**
 * @param {String} msg
 */
function fatal_err (msg) {
    ipcRenderer.send("console:fatal", msg);
}

/**@type {net.Socket} */
let serverConnection;
/**@type {net.Socket} */
let authConnection;

/**@type {(data:Buffer)=>void} */
let serveCB = () => {};
/**@type {(data:Buffer)=>void} */
let authCB = () => {};

/**
 * authenticates a user
 * @param {Number[]} userName
 * @param {Number[]} password
 * @param {Number[]} clientID
 * @returns {Promise<void>}
 */
async function authenticate (userName, password, clientID) {
    for (let i = userName.length; i < 32; i ++) {userName.push(0x32);}
    for (let i = password.length; i < 32; i ++) {password.push(0x32);}
    // userName = userName.pa;
    // password = password.fill(0x20, password.length, 32);
    // userName = Buffer.alloc(32);
    const server = serverConnection;
    const auth = authConnection;
    function server_write (data) {
        if (!Array.isArray(data)) {data = [data];}
        server.write(Buffer.from(data));
    }
    function auth_write (data) {
        if (!Array.isArray(data)) {data = [data];}
        auth.write(Buffer.from(data));
    }
    server_write(userName);
    let buf = [];
    buf = await read(server, 40);
    log(buf);
    const nonce0 = buf.slice(0, 32);
    const serverID = buf.slice(32, 40);
    auth_write([0x63, ...clientID]);
    buf = await read(auth, 1);
    log(asHex(buf));
    if (buf[0] === 0x55) {
        return;
    }
    buf = await read(auth, 32);
    log(asHex(buf));
    const nonce1 = buf;
    auth_write([...serverID, ...nonce0, ...hash([...password, ...nonce1, ...clientID])]);
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
    log(buf);
    const authHash = buf;
    server_write([...userName, ...clientID, ...authHash]);
    buf = await read(server, 1);
    if (buf[0] === 0x55) {
        log("server rejection");
        return;
    }
    log("authenticated");
}

async function main () {
    /**@type {String[]} */
    const argv = await ipcRenderer.invoke("request:args");

    // log(argv);

    if (argv.length < 7) {
        fatal_err("insufficent args");
        return;
    }
    const username = argv[2].split("").map(v => v.charCodeAt(0));
    const password = argv[3].split("").map(v => v.charCodeAt(0));
    const dthconv = "0123456789abcdef";
    const pfuncA0001 = (s) => {
        s = s.padEnd(16, "0");
        let f = [];
        for (let i = 0; i < s.length; i += 2) {
            n = s[i] + s[i+1];
            f.push(dthconv.indexOf(n[0]) << 4 | dthconv.indexOf(n[1]));
        }
        return f;
    };
    const userid = pfuncA0001(argv[4]);
    if (username.length > 32) {log("name too long");return;}
    if (password.length > 32) {log("password too long");return;}
    if (userid.length !== 8) {log("invalid userid");return;}
    const servePort = Number(argv[5].slice(argv[5].indexOf(":")+1)) || 5000;
    const serveHost = argv[5].slice(0,argv[5].indexOf(":")) || "127.0.0.1";
    const authPort = Number(argv[6].slice(argv[6].indexOf(":")+1)) || 4000;
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
    serverConnection.on("data", serveCB);
    authConnection = new net.Socket();
    await new Promise((res, _) => {
        authConnection.connect(authPort, authHost, () => {
            res();
        });
    });
    authConnection.on("data", authCB);
    log("after connection");
    await authenticate(username, password, userid);
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
const { send, log, load_view, NSocket, ServerData, mkTmp } = require("../js/common");
const { readFileSync, writeFileSync } = require("fs");
const join_path = require("path").join;
const { bigToBytes, bytesToBig, resolveServerAddr, bufferToString, vConnect, eat, SymmetricCipher, generateKeyPair, stringToBuffer, formatBuf } = require("../defs");
const { createPublicKey, privateDecrypt, publicEncrypt } = require("crypto");

let [ publicKey, privateKey ] = generateKeyPair();

const hosts_path = join_path(__dirname, "..", "data", "managed_hosts.dat");
const auths_path = join_path(__dirname, "..", "data", "managed_auths.dat");

mkTmp({defaultData:0x00, path:["../data", "managed_hosts.dat"]});
mkTmp({defaultData:0x00, path:["../data", "managed_auths.dat"]});

writeFileSync(hosts_path, Buffer.concat([Buffer.of(0, 0, 0, 2), Buffer.of(0, ...bigToBytes(15651, 2), 127, 0, 0, 1), Buffer.of(0, ...bigToBytes(3000, 2), 127, 0, 0, 1)]));
writeFileSync(auths_path, Buffer.concat([Buffer.of(0, 0, 0, 2), Buffer.of(0, ...bigToBytes(15652, 2), 127, 0, 0, 1), Buffer.of(0, ...bigToBytes(3001, 2), 127, 0, 0, 1)]));

/**
 * @param {string} path
 * @param {object} meta
 * @returns {ServerData[]}
 */
function fetchServers (path, meta) {
    let data = Array.from(readFileSync(path));
    if (data.length === 1) return [];
    /**@type {ServerData[]} */
    let f = [];
    const server_count = bytesToBig(eat(data, 4));
    for (let i = 0; i < server_count; i ++) {
        const formatId = eat(data, 1)[0];
        const port = bytesToBig(eat(data, 2));
        let addr;
        switch (formatId) {
            case 0:
                addr = eat(data, 4).join(".");
                break;
            case 1:
                addr = eat(data, 16);
                let x = [];
                let b = "";
                for (let i = 0; i < 16; i += 2) {
                    x.push(asHex([addr[i], addr[i+1]]));
                }
                x.forEach(v => {
                    if (v !== "0000") {
                        b += v;
                    }
                    b += ":";
                });
                addr = x.slice(0, x.length-1);
                break;
            case 2:
                addr = bufferToString(eat(data, bytesToBig(eat(data, 4))));
                break;
        }
        f.push({port:port,ip_format:formatId,ip:addr,meta:meta});
    }
    return f;
}

/**@type {ServerData} */
let cSelAddr = null;

/**@type {HTMLDivElement} */
const hostList = document.getElementById("host-list");
/**@type {HTMLDivElement} */
const authList = document.getElementById("auth-list");
/**@type {HTMLInputElement} */
const confServ = document.getElementById("config-btn");
/**@type {HTMLDivElement} */
const configPanel = document.getElementById("config-panel");
/**@type {HTMLDivElement} */
const configLoginPanel = document.getElementById("conf-login-panel");
/**@type {HTMLInputElement} */
const confLoginPassword = document.getElementById("conf-login-pass");

/**@type {HTMLDivElement} */
const hostConfigPanel = document.getElementById("host-config-panel");
/**@type {HTMLDivElement} */
const authConfigPanel = document.getElementById("auth-config-panel");

/**@type {HTMLDivElement} */
const hostPolicyList = document.getElementById("host-policies");
/**@type {HTMLDivElement} */
const authPolicyList = document.getElementById("auth-policies");

confLoginPassword.addEventListener("keyup", (e) => {
    if (e.code.toString() === "Enter") {
        document.getElementById("conf-login-btn").click();
    }
});

configPanel.querySelector("input#exit-config-btn").addEventListener("click", () => {
    close_config_panel();
});

/**@type {NSocket} */
let manConnection = null;
/**@type {SymmetricCipher} */
let cipher = null;

window.addEventListener("beforeunload", () => {
    if (manConnection?.readable) {
        manConnection.end();
    }
});

/**
 * @param {string} password
 * @returns {Promise<boolean>}
 */
function tryLogin (password) {
    return new Promise(async (res, _) => {
        console.log("LOGIN");
        const socket = new NSocket();
        await new Promise((res, _) => {
            socket.connect(cSelAddr.port, cSelAddr.ip, res);
        });
        // if (!(await vConnect(cSelAddr, socket, (ret, he) => {console.log(ret, he)}))) return res(false);
        socket.write(0x01);
        const dat = await socket.read(bytesToBig(await socket.read(2)), {format:"buffer"});
        // console.log(bufferToString(dat, "utf-8"));
        const serverPubKey = createPublicKey(dat);
        const encPass = publicEncrypt(serverPubKey, stringToBuffer(password));
        socket.bundle();
        socket.write(bigToBytes(encPass.length, 2));
        socket.write(encPass);
        socket.flush();
        if ((await socket.read(1, {format:"number"})) === 0x55) return res(false);
        manConnection = socket;
        res(true);
    });
}

async function manage_op (opid) {
    const socket = manConnection;
    if (socket === null || socket.readableEnded) return console.log("FINISHED");
    switch (opid) {
        case 0:
            socket.write(0x00);
            manConnection.end();
            manConnection = null;
            break;
    }
}

/**
 * @param {HTMLDivElement} elem
 * @param {{id:number,name:string,type:string,value:any}[]} policies
 */
function mkPolicyList (elem, policies) {
    elem.replaceChildren();
    for (const policy of policies) {
        const cont = document.createElement("div");
        const dat = document.createElement("span");
        const id = document.createElement("span");
        const val = document.createElement("span");
        id.textContent = `${policy.id} - ${policy.type}`;
        dat.textContent = policy.name;
        val.textContent = `${policy.value}`;
        cont.replaceChildren(id, dat, val);
        elem.appendChild(cont);
    }
}

async function getServerPolicies (elem) {
    const socket = manConnection;
    socket.write(0x03);
    /**@type {{id:number,name:string,type:string,value:any}[]} */
    let policies = [];
    const polnum = bytesToBig(await socket.read(2));
    let typelist = ["INVALID TYPE", "boolean", "number", "string", "un-serializable"];
    for (let i = 0; i < polnum; i ++) {
        const id = await socket.read(1, {format:"number"});
        const nlen = bytesToBig(await socket.read(4));
        const name = bufferToString(await socket.read(nlen, {format:"buffer"}));
        const type = await socket.read(1, {format:"number"});
        let value;
        switch (type) {
            case 0x01:
                value = (await socket.read(1, {format:"number"})) === 0x02;
                break;
            case 0x02:
                value = bytesToBig(await socket.read(4));
                break;
            case 0x03:
                value = bufferToString(await socket.read(bytesToBig(await socket.read(4)), {format:"buffer"}));
                break;
            case 0x04:
                value = "CANNOT SERIALIZE";
                break;
        }
        policies.push({id:id,name:name,type:typelist[type],value:value});
    }
    mkPolicyList(elem, policies);
}

document.getElementById("conf-login-btn").addEventListener("click", async () => {
    const result = await tryLogin(confLoginPassword.value);
    confLoginPassword.value = "";
    if (!result) return pop("login not successful");
    const socket = manConnection;
    const exportedPubKey = stringToBuffer(publicKey.export({"format":"pem","type":"spki"}), true);
    socket.bundle();
    socket.write(bigToBytes(exportedPubKey.length, 2));
    socket.write(exportedPubKey);
    socket.flush();
    cipher = new SymmetricCipher(privateDecrypt(privateKey, await socket.read(bytesToBig(await socket.read(2)), {format:"buffer"})));
    socket.setCryptor(cipher);
    configLoginPanel.hidden = true;
    if (cSelAddr.meta.hostauth === 0) {
        hostConfigPanel.hidden = false;
        getServerPolicies(hostPolicyList);
    } else {
        authConfigPanel.hidden = false;
        getServerPolicies(authPolicyList);
    }
});

function close_config_panel () {
    if (manConnection?.readable) {
        manage_op(0);
    }
    configLoginPanel.hidden = false;
    hostConfigPanel.hidden = true;
    authConfigPanel.hidden = true;
    configPanel.hidden = true;
}

function open_config_panel () {
    configPanel.hidden = false;
}

confServ.addEventListener("click", () => {
    if (!cSelAddr) return;
    open_config_panel();
});

function deselect () {
    confServ.classList.add("disabled");
    cSelAddr = null;
    const sel = document.querySelector(".server-address.selected");
    if (sel) {
        sel.classList.remove("selected");
    }
}

/**
 * @param {HTMLDivElement} d
 * @param {ServerData} a
 */
function selectAddress (d, a) {
    const alsel = d.classList.contains("selected");
    deselect();
    if (alsel) return;
    confServ.classList.remove("disabled");
    cSelAddr = a;
    d.classList.toggle("selected");
}

/**
 * @param {ServerData[]} addr 
 * @param {HTMLDivElement} list 
 */
function mkServerList (addrs, list) {
    list.replaceChildren();
    for (const addr of addrs) {
        const d = document.createElement("div");
        d.className = "server-address";
        d.textContent = `${addr.ip}:${addr.port}`;
        d.addEventListener("click", () => {
            selectAddress(d, addr);
        });
        list.appendChild(d);
    }
}

function refresh () {
    mkServerList(fetchServers(hosts_path, {hostauth:0}), hostList);
    mkServerList(fetchServers(auths_path, {hostauth:1}), authList);
}

refresh();

document.getElementById("back-btn").addEventListener("click", () => {
    load_view("title");
});
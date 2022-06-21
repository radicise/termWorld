const { send, log, load_view, NSocket, ServerData, mkTmp } = require("../js/common");
const { readFileSync, writeFileSync } = require("fs");
const join_path = require("path").join;
const { bigToBytes, bytesToBig, resolveServerAddr, bufferToString, vConnect, eat } = require("../defs");

const hosts_path = join_path(__dirname, "..", "data", "managed_hosts.dat");
const auths_path = join_path(__dirname, "..", "data", "managed_auths.dat");

mkTmp({defaultData:0x00, path:["../data", "managed_hosts.dat"]});
mkTmp({defaultData:0x00, path:["../data", "managed_auths.dat"]});

writeFileSync(hosts_path, Buffer.of(0, 0, 0, 1, 0, ...bigToBytes(15651, 2), 127, 0, 0, 1));

/**
 * @param {string} path
 * @returns {ServerData[]}
 */
function fetchServers (path) {
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
                addr = eat(data, 4);
                break;
            case 1:
                addr = eat(data, 16);
                break;
            case 2:
                addr = bufferToString(eat(data, bytesToBig(eat(data, 4))));
                break;
        }
        f.push({port:port,ip_format:formatId,ip:addr});
    }
    return f;
}

/**@type {HTMLDivElement} */
const hostList = document.getElementById("host-list");
/**@type {HTMLDivElement} */
const authList = document.getElementById("auth-list");

function refresh () {
    hostList.replaceChildren();
    const hosts = fetchServers(hosts_path);
    for (const host of hosts) {
        const d = document.createElement("div");
        d.textContent = `${host.ip}, ${host.port}`;
        hostList.appendChild(d);
    }
}

refresh();

document.getElementById("back-btn").addEventListener("click", () => {
    load_view("title");
});
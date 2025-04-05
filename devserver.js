const http = require('http');
const fs = require('fs');
const path = require('path');
const httpProxy = require('http-proxy');

// Základná konfigurácia
const config = {
    port: 3000,
    staticDir: 'src/main/resources/public',
    proxies: {
        '/web/': 'https://flow-test.aidental.ai',
        //'/web/': 'http://localhost:8080',
        '/flow/': 'https://flow-test.aidental.ai',
    }
};

// Vytvorenie proxy servera
const proxy = httpProxy.createProxyServer({
    changeOrigin: true,
    secure: false
});

// Vytvorenie HTTP servera
const server = http.createServer((req, res) => {
    // Kontrola či URL zodpovedá niektorému proxy pravidlu
    for (const [path, target] of Object.entries(config.proxies)) {
        if (req.url.startsWith(path)) {
            return proxy.web(req, res, { target });
        }
    }

    // Ak nejde o proxy, servujeme statické súbory
    let filePath = path.join(config.staticDir, req.url);
    
    // Ak URL končí /, pridáme index.html
    if (filePath.endsWith('/')) {
        filePath = path.join(filePath, 'index.html');
    }

    fs.readFile(filePath, (err, data) => {
        if (err) {
            res.writeHead(404);
            res.end('File not found');
            return;
        }

        // Jednoduché MIME type určenie
        const ext = path.extname(filePath);
        const mimeTypes = {
            '.html': 'text/html',
            '.js': 'text/javascript',
            '.css': 'text/css',
            '.json': 'application/json',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.gif': 'image/gif'
        };

        res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'text/plain' });
        res.end(data);
    });
});

server.listen(config.port, () => {
    console.log(`Dev server beží na porte ${config.port}`);
    console.log(`Statické súbory sa servujú z: ${config.staticDir}`);
    console.log('Nakonfigurované proxy cesty:');
    Object.entries(config.proxies).forEach(([path, target]) => {
        console.log(`${path} -> ${target}`);
    });
}); 
package com.bprvt.bprdownloader.send

import com.bprvt.bprdownloader.data.HistoryEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * A tiny LAN web server so a phone can type URLs for the Fire TV.
 *
 * Deliberately minimal: it runs only while the Send screen is open, serves one
 * page, and gates every action behind a PIN shown on the TV. It is not a
 * general-purpose HTTP server and should never be exposed beyond the local
 * network.
 */
class SendServer(
    private val onSubmit: (url: String) -> Unit,
    private val historyProvider: () -> List<HistoryEntry>
) {

    @Volatile
    private var serverSocket: ServerSocket? = null
    private val workers = Executors.newCachedThreadPool()
    private var acceptThread: Thread? = null

    var pin: String = ""
        private set
    var port: Int = 0
        private set

    val isRunning: Boolean get() = serverSocket != null

    fun start(): Boolean {
        if (isRunning) return true
        pin = Random.nextInt(1000, 10000).toString()
        val socket = openSocket() ?: return false
        serverSocket = socket
        port = socket.localPort
        acceptThread = Thread {
            while (true) {
                val client = try {
                    socket.accept()
                } catch (_: Throwable) {
                    break // socket closed by stop()
                }
                workers.execute { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }.apply { isDaemon = true; start() }
        return true
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        pin = ""
        port = 0
    }

    private fun openSocket(): ServerSocket? {
        for (candidate in 8080..8090) {
            runCatching { return ServerSocket(candidate) }
        }
        return runCatching { ServerSocket(0) }.getOrNull()
    }

    /** The address to show on the TV, e.g. http://192.168.1.42:8080 */
    fun address(): String? {
        val ip = localIpv4() ?: return null
        return "http://$ip:$port"
    }

    private fun localIpv4(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        for (nic in interfaces) {
            if (!nic.isUp || nic.isLoopback) continue
            for (address in nic.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    // --- request handling --------------------------------------------------

    private fun handle(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1].substringBefore('?')

        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }

        val body = if (contentLength > 0 && contentLength < MAX_BODY) {
            val buffer = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = reader.read(buffer, read, contentLength - read)
                if (n < 0) break
                read += n
            }
            String(buffer, 0, read)
        } else {
            ""
        }

        val output = client.getOutputStream()
        when {
            method == "GET" && path == "/" -> respond(output, 200, "text/html; charset=utf-8", PAGE)

            method == "POST" && path == "/send" -> {
                val form = parseForm(body)
                if (!checkPin(form["pin"])) {
                    respond(output, 200, JSON, """{"ok":false,"message":"Wrong PIN"}""")
                    return
                }
                val url = form["url"]?.trim().orEmpty()
                if (url.isEmpty()) {
                    respond(output, 200, JSON, """{"ok":false,"message":"Enter a link"}""")
                    return
                }
                onSubmit(url)
                val message = quote("Downloading on the TV")
                respond(output, 200, JSON, """{"ok":true,"message":$message}""")
            }

            method == "POST" && path == "/history" -> {
                if (!checkPin(parseForm(body)["pin"])) {
                    respond(output, 200, JSON, """{"ok":false,"message":"Wrong PIN"}""")
                    return
                }
                val items = runCatching { historyProvider() }.getOrDefault(emptyList())
                    .take(40)
                    .joinToString(",") { entry ->
                        """{"label":${quote(entry.label)},"url":${quote(entry.url)}}"""
                    }
                respond(output, 200, JSON, """{"ok":true,"items":[$items]}""")
            }

            else -> respond(output, 404, "text/plain", "Not found")
        }
    }

    /** Length-independent compare; the PIN is short so this is cheap insurance. */
    private fun checkPin(supplied: String?): Boolean {
        val expected = pin
        if (supplied == null || expected.isEmpty()) return false
        if (supplied.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (supplied[i].code xor expected[i].code)
        // Slow down anyone walking the 10k keyspace over the network.
        if (diff != 0) Thread.sleep(250)
        return diff == 0
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            runCatching {
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }.getOrNull()
        }.toMap()

    private fun quote(value: String): String {
        val escaped = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '"' -> escaped.append("\\\"")
                '\\' -> escaped.append("\\\\")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                else -> if (c < ' ') escaped.append(String.format("\\u%04x", c.code)) else escaped.append(c)
            }
        }
        return escaped.append('"').toString()
    }

    private fun respond(output: OutputStream, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    companion object {
        private const val MAX_BODY = 16 * 1024
        private const val JSON = "application/json; charset=utf-8"

        private val PAGE = """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Send to Fire TV</title>
<style>
*{box-sizing:border-box}
body{margin:0;padding:20px;background:#12151A;color:#F2F5F8;
     font:16px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
h1{font-size:20px;margin:0 0 18px;color:#FF7A00}
label{display:block;font-size:13px;color:#9AA6B4;margin:14px 0 6px}
input{width:100%;padding:14px;font-size:17px;border-radius:10px;
      border:1px solid #2A313C;background:#1C212A;color:#F2F5F8}
input:focus{outline:none;border-color:#FF7A00}
.row{display:flex;gap:10px;margin-top:16px}
button{flex:1;padding:15px;font-size:16px;font-weight:600;border:0;border-radius:10px;
       background:#FF7A00;color:#fff}
button.alt{background:#1C212A;color:#F2F5F8;border:1px solid #2A313C}
#msg{margin-top:16px;padding:12px;border-radius:8px;display:none}
#msg.ok{display:block;background:#12301A;color:#3FB950}
#msg.err{display:block;background:#2E1615;color:#E5534B}
h2{font-size:13px;color:#9AA6B4;margin:28px 0 10px;text-transform:uppercase;letter-spacing:.08em}
.item{width:100%;text-align:left;background:#1C212A;border:1px solid #2A313C;color:#F2F5F8;
      padding:13px;border-radius:10px;margin-bottom:8px;font-size:15px}
.item small{display:block;color:#9AA6B4;font-size:12px;margin-top:3px;
            overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style></head><body>
<h1>Send to Fire TV</h1>

<label>PIN (shown on the TV)</label>
<input id="pin" inputmode="numeric" maxlength="4" placeholder="0000">

<label>Link</label>
<input id="url" type="url" inputmode="url" autocapitalize="off" autocorrect="off"
       placeholder="Paste a link">

<div class="row">
  <button onclick="send()">Send to TV</button>
</div>

<div id="msg"></div>

<h2>Recent</h2>
<button class="alt item" onclick="loadHistory()">Load history</button>
<div id="hist"></div>

<script>
var pin = document.getElementById('pin');
pin.value = localStorage.getItem('pin') || '';
pin.addEventListener('change', function(){ localStorage.setItem('pin', pin.value); });

function show(ok, text){
  var m = document.getElementById('msg');
  m.className = ok ? 'ok' : 'err';
  m.textContent = text;
}

function post(path, data){
  var body = Object.keys(data).map(function(k){
    return encodeURIComponent(k) + '=' + encodeURIComponent(data[k]);
  }).join('&');
  return fetch(path, {
    method:'POST',
    headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body: body
  }).then(function(r){ return r.json(); });
}

function send(){
  localStorage.setItem('pin', pin.value);
  post('/send', {pin: pin.value, url: document.getElementById('url').value})
    .then(function(r){
      show(r.ok, r.message);
      if (r.ok) document.getElementById('url').value = '';
    })
    .catch(function(){ show(false, 'Could not reach the TV'); });
}

function loadHistory(){
  post('/history', {pin: pin.value}).then(function(r){
    if (!r.ok) { show(false, r.message); return; }
    var box = document.getElementById('hist');
    box.innerHTML = '';
    r.items.forEach(function(it){
      var b = document.createElement('button');
      b.className = 'item';
      b.innerHTML = '';
      b.appendChild(document.createTextNode(it.label));
      var s = document.createElement('small');
      s.appendChild(document.createTextNode(it.url));
      b.appendChild(s);
      b.onclick = function(){
        document.getElementById('url').value = it.url;
        send();
      };
      box.appendChild(b);
    });
  }).catch(function(){ show(false, 'Could not reach the TV'); });
}
</script>
</body></html>
""".trimIndent()
    }
}

// server.js - Zero-dependency Life Pro.AI Chatbot Server, Web Host & Safety API
const http = require("http");
const fs = require("fs");
const path = require("path");

// Try reading .env manually without external packages
const envPath = path.join(__dirname, ".env");
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, "utf-8");
  envContent.split("\n").forEach(line => {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith("#") && trimmed.includes("=")) {
      const [key, ...values] = trimmed.split("=");
      const val = values.join("=").trim().replace(/^["']|["']$/g, "");
      process.env[key.trim()] = val;
    }
  });
}

const PORT = process.env.PORT || 3000;
const NEBIUS_API_KEY = process.env.NEBIUS_API_KEY || "";
const NEMOTRON_MODEL = process.env.NEMOTRON_MODEL || "nvidia/llama-3.1-nemotron-70b-instruct";

// Live Safety State Store
const state = {
  pairingCodes: new Map(), // code -> { userId, userName, expiresAt }
  locations: new Map(),    // userId -> { latitude, longitude, timestamp, address }
  alerts: []               // list of safety events
};

// Seed initial default pair code for instant demo
state.pairingCodes.set("LP-782941", {
  userId: "user_ananya_01",
  userName: "Ananya Sharma (Senior User)",
  expiresAt: Date.now() + 86400000
});

// Helper to locate static files across directory structures
function findFile(filename) {
  const candidates = [
    path.join(__dirname, filename),
    path.join(__dirname, "public", filename),
    path.join(__dirname, "..", filename),
    path.join(__dirname, "..", "public", filename)
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }
  return null;
}

const server = http.createServer(async (req, res) => {
  // Allow all origins (CORS) so the phone app and web portal can connect
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    return res.end();
  }

  // Serve landing page at / and /index.html
  if ((req.method === "GET" || req.method === "HEAD") && (req.url === "/" || req.url === "/index.html")) {
    const htmlPath = findFile("index.html");
    if (htmlPath) {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      if (req.method === "HEAD") return res.end();
      return fs.createReadStream(htmlPath).pipe(res);
    }
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ status: "online", name: "Life Pro.AI" }));
  }

  // Serve direct APK download
  if ((req.method === "GET" || req.method === "HEAD") && (req.url === "/LifePro-AI.apk" || req.url === "/download")) {
    const apkPath = findFile("LifePro-AI.apk");
    if (apkPath) {
      const stat = fs.statSync(apkPath);
      res.writeHead(200, {
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Disposition": 'attachment; filename="LifePro-AI.apk"',
        "Content-Length": stat.size
      });
      if (req.method === "HEAD") return res.end();
      return fs.createReadStream(apkPath).pipe(res);
    }
    // Redirect to GitHub release if local APK not found
    res.writeHead(302, {
      Location: "https://github.com/lifehaishetty/LifePro-AI/releases/latest/download/LifePro-AI.apk"
    });
    return res.end();
  }

  // Health check endpoint
  if ((req.method === "GET" || req.method === "HEAD") && (req.url === "/health" || req.url === "/api/health")) {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(
      JSON.stringify({
        status: "online",
        name: "Life Pro.AI Chatbot Server",
        port: PORT,
        activePairCodes: state.pairingCodes.size,
        hasNebiusKey: Boolean(NEBIUS_API_KEY && NEBIUS_API_KEY !== "YOUR_NEBIUS_API_KEY_HERE")
      })
    );
  }

  // =========================================================================
  // PAIRING API ENDPOINTS
  // =========================================================================
  if (req.method === "POST" && req.url === "/api/pair/create") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const code = "LP-" + Math.floor(100000 + Math.random() * 900000);
        state.pairingCodes.set(code, {
          userId: payload.userId || "user_" + Date.now(),
          userName: payload.userName || "User",
          expiresAt: Date.now() + 3600000 // 1 hour
        });
        res.writeHead(200, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ success: true, code, expiresAt: Date.now() + 3600000 }));
      } catch (e) {
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  if (req.method === "POST" && req.url === "/api/pair/verify") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const code = (payload.code || "").toUpperCase().trim();
        const record = state.pairingCodes.get(code);
        if (record && record.expiresAt > Date.now()) {
          res.writeHead(200, { "Content-Type": "application/json" });
          return res.end(JSON.stringify({ success: true, pairedUser: record }));
        }
        res.writeHead(404, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ success: false, message: "Invalid or expired pairing code." }));
      } catch (e) {
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  // =========================================================================
  // LIVE LOCATION SYNC ENDPOINTS
  // =========================================================================
  if (req.method === "POST" && req.url === "/api/location/update") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const userId = payload.userId || "default_user";
        state.locations.set(userId, {
          latitude: payload.latitude || 19.0760,
          longitude: payload.longitude || 72.8777,
          timestamp: Date.now(),
          address: payload.address || "Live GPS Location"
        });
        res.writeHead(200, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ success: true, updated: Date.now() }));
      } catch (e) {
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  if (req.method === "GET" && req.url.startsWith("/api/location/get")) {
    const loc = state.locations.get("default_user") || {
      latitude: 19.0760,
      longitude: 72.8777,
      timestamp: Date.now(),
      address: "Mumbai, Maharashtra (Live GPS Lock)"
    };
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify(loc));
  }

  // =========================================================================
  // EMERGENCY ALERTS ENDPOINTS
  // =========================================================================
  if (req.method === "POST" && req.url === "/api/alert/trigger") {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const alert = {
          id: "alert_" + Date.now(),
          type: payload.type || "SOS_EMERGENCY",
          userId: payload.userId || "default_user",
          target: payload.target || "112",
          timestamp: Date.now(),
          location: payload.location || "Coordinates Shared"
        };
        state.alerts.unshift(alert);
        if (state.alerts.length > 50) state.alerts.pop();
        res.writeHead(200, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ success: true, alert }));
      } catch (e) {
        res.writeHead(400, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  if (req.method === "GET" && req.url === "/api/alert/list") {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ alerts: state.alerts }));
  }

  // =========================================================================
  // CONVERSATIONAL AI CHAT ENDPOINT
  // =========================================================================
  if (req.method === "POST" && (req.url === "/api/chat" || req.url === "/chat")) {
    let body = "";
    req.on("data", chunk => body += chunk);

    req.on("end", async () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const userMessage = payload.message || "";
        console.log(`[Life Pro.AI] Message received: "${userMessage}"`);

        // If no live Nebius key is set yet, provide intelligent structured safety logic
        if (!NEBIUS_API_KEY || NEBIUS_API_KEY === "YOUR_NEBIUS_API_KEY_HERE") {
          const lower = userMessage.toLowerCase();
          let mockAction = "none";
          let mockContact = "";
          let mockReply = `I hear you loud and clear: "${userMessage}". I'm right here with you.`;

          if (lower.includes("help") || lower.includes("ambulance") || lower.includes("emergency") || lower.includes("danger") || lower.includes("police")) {
            mockAction = "sos";
            mockContact = "112";
            mockReply = "Emergency detected! Initiating 112 Indian SOS dispatch now.";
          } else if (lower.includes("fell down") || lower.includes("i fell") || lower.includes("hurt")) {
            mockAction = "none";
            mockReply = "I heard you fell. Are you hurt? Do you need me to alert your guardian or call 112?";
          } else if (lower.includes("call")) {
            mockAction = "call";
            mockContact = userMessage.replace(/call/i, "").trim() || "Emergency Contact";
            mockReply = `Calling ${mockContact} immediately.`;
          } else if (lower.includes("camera") || lower.includes("video")) {
            mockAction = "camera";
            mockReply = "Opening live video feed for guardian check-in.";
          } else if (lower.includes("location") || lower.includes("where am i")) {
            mockAction = "location";
            mockReply = "Sharing your live GPS location.";
          }

          res.writeHead(200, { "Content-Type": "application/json" });
          return res.end(JSON.stringify({
            reply: mockReply,
            action: mockAction,
            contact: mockContact
          }));
        }

        // Call NVIDIA Nemotron on Nebius Token Factory
        const apiResponse = await fetch("https://api.studio.nebius.ai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${NEBIUS_API_KEY}`
          },
          body: JSON.stringify({
            model: NEMOTRON_MODEL,
            messages: [
              {
                role: "system",
                content: `You are Life Pro AI, a fast, reliable personal safety and emergency assistant designed for visually challenged individuals and elderly care.
Always respond strictly with a valid JSON object matching this schema:
{
  "reply": "Your brief, calm, and direct response",
  "action": "none|call|sos|camera|location",
  "contact": ""
}

ACTION RULES:
1. If the user shouts or mentions "help", "emergency", "save me", "call ambulance", "danger", or "call police":
   - Set "action": "sos"
   - Set "contact": "112"
2. If the user reports falling down:
   - Ask gently if they are hurt and need emergency assistance. Set "action": "none".
3. If the user asks to call someone:
   - Set "action": "call"
   - Set "contact": extracted name or number.
4. If the user asks for camera/video check-in:
   - Set "action": "camera"
5. If the user asks for location or tracking:
   - Set "action": "location"
6. Casual conversation:
   - Set "action": "none"

Do not include markdown codeblocks or extra text. Return only raw JSON.`
              },
              { role: "user", content: userMessage }
            ],
            temperature: 0.3
          })
        });

        if (!apiResponse.ok) {
          res.writeHead(502, { "Content-Type": "application/json" });
          return res.end(JSON.stringify({
            reply: "Emergency fallback active. You can say 'Call my daughter' or shout 'Help' anytime.",
            action: "none",
            contact: ""
          }));
        }

        const data = await apiResponse.json();
        const rawContent = data.choices?.[0]?.message?.content || "{}";
        const cleaned = rawContent.replace(/```json/gi, "").replace(/```/g, "").trim();
        const parsed = JSON.parse(cleaned);

        res.writeHead(200, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({
          reply: parsed.reply || "I am right here with you.",
          action: parsed.action || "none",
          contact: parsed.contact || ""
        }));
      } catch (err) {
        res.writeHead(500, { "Content-Type": "application/json" });
        return res.end(JSON.stringify({
          reply: "Life Pro AI is standing by to protect you.",
          action: "none",
          contact: ""
        }));
      }
    });
    return;
  }

  // Fallback 404
  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "Endpoint not found" }));
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Life Pro.AI Server running on port ${PORT}`);
});

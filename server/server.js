// server.js - Zero-dependency Life Pro.AI Chatbot Server
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

const server = http.createServer(async (req, res) => {
  // Allow all origins (CORS) so the phone app can connect
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    return res.end();
  }

  // Health check endpoint
  if (req.method === "GET" && (req.url === "/" || req.url === "/health")) {
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(
      JSON.stringify({
        status: "online",
        name: "Life Pro.AI Chatbot Server",
        port: PORT,
        hasNebiusKey: Boolean(NEBIUS_API_KEY && NEBIUS_API_KEY !== "YOUR_NEBIUS_API_KEY_HERE")
      })
    );
  }

  // Chat endpoint called by Android app
  if (req.method === "POST" && req.url === "/api/chat") {
    let body = "";
    req.on("data", chunk => {
      body += chunk;
    });

    req.on("end", async () => {
      try {
        const payload = body ? JSON.parse(body) : {};
        const userMessage = payload.message || "";
        console.log(`[Life Pro.AI] Received message from phone: "${userMessage}"`);

        // If no live Nebius key is set yet, provide an intelligent test simulated reply
        if (!NEBIUS_API_KEY || NEBIUS_API_KEY === "YOUR_NEBIUS_API_KEY_HERE") {
          console.log("[Life Pro.AI] Returning smart simulation reply (add NEBIUS_API_KEY to .env for live Nemotron)");
          const lower = userMessage.toLowerCase();
          let mockAction = "none";
          let mockContact = "";
          let mockReply = `I hear you loud and clear: "${userMessage}". I'm right here with you.`;

          if (lower.includes("help") || lower.includes("ambulance") || lower.includes("emergency") || lower.includes("danger") || lower.includes("police")) {
            mockAction = "sos";
            mockContact = "112";
            mockReply = "Emergency detected! Initiating 112 Indian SOS dispatch now.";
          } else if (lower.includes("call")) {
            mockAction = "call";
            mockContact = userMessage.replace(/call/i, "").trim() || "Emergency Contact";
            mockReply = `Calling ${mockContact} immediately.`;
          } else if (lower.includes("camera") || lower.includes("video")) {
            mockAction = "camera";
            mockReply = "Opening live video feed for guardian check-in.";
          }

          const mockResponse = {
            reply: mockReply,
            action: mockAction,
            contact: mockContact
          };
          res.writeHead(200, { "Content-Type": "application/json" });
          return res.end(JSON.stringify(mockResponse));
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
                content: `You are Life Pro AI, a fast and responsive personal safety and emergency assistant.
Always respond strictly with a valid JSON object matching this schema:
{
  "reply": "Your brief, calm, and direct response",
  "action": "none|call|sos|camera|location",
  "contact": ""
}

ACTION RULES:
1. If the user shouts or mentions "help", "emergency", "save me", "call ambulance", "danger", or "call police":
   - Set "action": "sos"
   - Set "contact": "112" (Indian national all-in-one emergency number covering Police, Fire, and Ambulance).
   - Reply reassuringly that emergency SOS is triggered.
2. If the user asks to call someone (e.g. "call mom", "call rahul", "call 9876543210"):
   - Set "action": "call"
   - Set "contact": extract the name or phone number requested.
3. If the user asks for camera/video or guardian video check:
   - Set "action": "camera"
4. If the user asks for location or tracking:
   - Set "action": "location"
5. For casual greetings like "hello life", "how are you", or walking chatter:
   - Set "action": "none"
   - Keep replies short, reassuring, and conversational.

Do not include markdown codeblocks or extra text. Return only raw JSON.`
              },
              { role: "user", content: userMessage }
            ],
            temperature: 0.3
          })
        });

        if (!apiResponse.ok) {
          const errText = await apiResponse.text();
          console.error("[Nebius API Error]", apiResponse.status, errText);
          res.writeHead(502, { "Content-Type": "application/json" });
          return res.end(
            JSON.stringify({
              reply: "Error communicating with Nebius AI engine. Please verify your API key.",
              action: "none",
              contact: ""
            })
          );
        }

        const data = await apiResponse.json();
        const rawContent = data.choices?.[0]?.message?.content || "{}";
        const cleaned = rawContent.replace(/```json/gi, "").replace(/```/g, "").trim();
        const parsed = JSON.parse(cleaned);

        console.log("[Life Pro.AI] Sending reply to phone:", parsed);
        res.writeHead(200, { "Content-Type": "application/json" });
        return res.end(
          JSON.stringify({
            reply: parsed.reply || "I am right here with you.",
            action: parsed.action || "none",
            contact: parsed.contact || ""
          })
        );
      } catch (err) {
        console.error("[Server Error]", err);
        res.writeHead(500, { "Content-Type": "application/json" });
        return res.end(
          JSON.stringify({
            reply: "Life Pro AI encountered a server processing error.",
            action: "none",
            contact: ""
          })
        );
      }
    });
    return;
  }

  // Fallback 404
  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "Endpoint not found" }));
});

server.listen(PORT, "0.0.0.0", () => {
  console.log("==================================================");
  console.log(` Life Pro.AI Server running on port ${PORT}`);
  console.log(` Active Nemotron Model: ${NEMOTRON_MODEL}`);
  console.log(` Health check: http://localhost:${PORT}/health`);
  console.log(` Chat endpoint: http://localhost:${PORT}/api/chat`);
  console.log("==================================================");
});

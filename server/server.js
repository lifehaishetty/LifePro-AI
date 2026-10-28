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

        // If no live Nebius key is set yet, provide a test simulated reply
        if (!NEBIUS_API_KEY || NEBIUS_API_KEY === "YOUR_NEBIUS_API_KEY_HERE") {
          console.log("[Life Pro.AI] Returning test simulation reply (add NEBIUS_API_KEY to .env for live Nemotron)");
          const mockResponse = {
            reply: `Hello from Life Pro.AI! I received your message: "${userMessage}". You are connected successfully!`,
            action: "none",
            contact: ""
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
            model: "nvidia/nemotron-4-340b-instruct",
            messages: [
              {
                role: "system",
                content: `You are Life Pro AI, an intelligent personal safety assistant. 
Always respond strictly with a valid JSON object matching this schema:
{
  "reply": "your calm, reassuring response",
  "action": "none|call|sos|camera",
  "contact": ""
}
Do not include markdown codeblocks. Return only raw JSON.`
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
  console.log(` Health check: http://localhost:${PORT}/health`);
  console.log(` Chat endpoint: http://localhost:${PORT}/api/chat`);
  console.log("==================================================");
});

// api/chat.js - Vercel Serverless Function for Life Pro.AI
export default async function handler(req, res) {
  // CORS configuration
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization'
  );

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // Health check endpoint
  if (req.method === 'GET') {
    const hasKey = Boolean(process.env.NEBIUS_API_KEY && process.env.NEBIUS_API_KEY !== 'YOUR_NEBIUS_API_KEY_HERE');
    return res.status(200).json({
      status: 'online',
      service: 'Life Pro.AI Chatbot Serverless Engine',
      model: process.env.NEMOTRON_MODEL || 'nvidia/llama-3.1-nemotron-70b-instruct',
      hasNebiusKey: hasKey,
      timestamp: new Date().toISOString()
    });
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method Not Allowed' });
  }

  try {
    const body = typeof req.body === 'string' ? JSON.parse(req.body) : (req.body || {});
    const userMessage = body.message || '';
    const NEBIUS_API_KEY = process.env.NEBIUS_API_KEY || '';
    const NEMOTRON_MODEL = process.env.NEMOTRON_MODEL || 'nvidia/llama-3.1-nemotron-70b-instruct';

    // If no live Nebius key is configured, return intelligent simulated safety logic
    if (!NEBIUS_API_KEY || NEBIUS_API_KEY === 'YOUR_NEBIUS_API_KEY_HERE') {
      const lower = userMessage.toLowerCase();
      let mockAction = 'none';
      let mockContact = '';
      let mockReply = `I hear you loud and clear: "${userMessage}". I am right here with you.`;

      if (
        lower.includes('help') ||
        lower.includes('ambulance') ||
        lower.includes('emergency') ||
        lower.includes('danger') ||
        lower.includes('police')
      ) {
        mockAction = 'sos';
        mockContact = '112';
        mockReply = 'Emergency detected! Initiating 112 Indian National SOS dispatch now.';
      } else if (lower.includes('call')) {
        mockAction = 'call';
        mockContact = userMessage.replace(/call/i, '').trim() || 'Emergency Contact';
        mockReply = `Calling ${mockContact} immediately.`;
      } else if (lower.includes('camera') || lower.includes('video')) {
        mockAction = 'camera';
        mockReply = 'Opening live camera feed for guardian safety check.';
      } else if (lower.includes('location') || lower.includes('where am i')) {
        mockAction = 'location';
        mockReply = 'Sharing your real-time GPS location with your registered guardians.';
      }

      return res.status(200).json({
        reply: mockReply,
        action: mockAction,
        contact: mockContact
      });
    }

    // Call live NVIDIA Nemotron via Nebius Token Factory
    const apiResponse = await fetch('https://api.studio.nebius.ai/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${NEBIUS_API_KEY}`
      },
      body: JSON.stringify({
        model: NEMOTRON_MODEL,
        messages: [
          {
            role: 'system',
            content: `You are Life Pro AI, a fast and responsive personal safety and emergency assistant designed for the elderly and visually challenged.
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
          { role: 'user', content: userMessage }
        ],
        temperature: 0.3
      })
    });

    if (!apiResponse.ok) {
      const errText = await apiResponse.text();
      console.error('[Nebius API Error]', apiResponse.status, errText);
      return res.status(502).json({
        reply: 'Error communicating with Nebius AI engine. Please verify your API key in Vercel settings.',
        action: 'none',
        contact: ''
      });
    }

    const data = await apiResponse.json();
    const rawContent = data.choices?.[0]?.message?.content || '{}';
    const cleaned = rawContent.replace(/```json/gi, '').replace(/```/g, '').trim();
    const parsed = JSON.parse(cleaned);

    return res.status(200).json({
      reply: parsed.reply || 'I am right here with you.',
      action: parsed.action || 'none',
      contact: parsed.contact || ''
    });
  } catch (err) {
    console.error('[Server Error]', err);
    return res.status(500).json({
      reply: 'Life Pro AI encountered a server processing error.',
      action: 'none',
      contact: ''
    });
  }
}

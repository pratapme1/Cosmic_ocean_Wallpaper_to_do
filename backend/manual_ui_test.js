const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
const fs = require('fs');

// Use current server setup
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const { parseTask } = require('./utils/task-parser');
const { parseLLM } = require('./utils/llm-task-parser');
const { prioritizeTasks } = require('./utils/priority-scorer');
const { generateEnhancedWallpaper } = require('./services/wallpaper-generator-enhanced');
const { MockClient } = require('./utils/mock-client');

const app = express();
app.use(cors());
app.use(bodyParser.json());

const client = new MockClient();
client.connect();

const scenarios = [
    { id: 1, input: "Submit Q4 report", label: "LLM Implicit Date", type: 'llm' },
    { id: 2, input: "Fix critical login bug ASAP", label: "LLM Urgent Priority", type: 'llm' },
    { id: 3, input: "Meet team at 2pm", label: "Local Time Extraction", type: 'local' },
    { id: 4, input: "Clean house Friday", label: "Local Implicit Day", type: 'local' },
    { id: 5, input: "Workout 45m", label: "Estimate (Duration) Only", type: 'local' },
    { id: 6, input: "Email boss regarding promotion", label: "Category: Work", type: 'llm' },
    { id: 7, input: "Buy groceries @errands", label: "Context: Errands", type: 'local' },
    { id: 8, input: "Dinner tonight 8pm", label: "Time Context & Exact Time", type: 'local' },
    { id: 9, input: "Mediate every morning", label: "Recurrence Pattern", type: 'local' },
    { id: 10, input: "Brainstorm strategy @office", label: "High Energy & Location", type: 'llm' },
    { id: 11, input: "Emergency meeting NOW", label: "Forced Urgency P1", type: 'local' },
    { id: 12, input: "Visit dentist 2026-02-15", label: "Explicit ISO Date", type: 'local' },
    { id: 13, input: "Finish proposal tonight", label: "Timezone Boundary: Tonight", type: 'local' },
    { id: 14, input: "Read 10 pages tomorrow evening", label: "Combined Date & Context", type: 'local' },
    { id: 15, input: "DONE", label: "Celebration Mode (Empty State)", type: 'local' }
];

let currentTasks = [];

app.get('/', (req, res) => {
    res.send(`
    <html>
      <head>
        <title>Cosmic Ocean - Manual Test Dashboard</title>
        <style>
          body { font-family: 'Inter', sans-serif; background: #0a0a0b; color: #fff; padding: 40px; }
          .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; }
          .card { background: #1a1a1c; padding: 20px; border-radius: 12px; }
          .wallpaper-preview { width: 360px; height: 640px; background: #000; border: 4px solid #333; border-radius: 20px; overflow: hidden; }
          button { background: #6366f1; color: #fff; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; }
          pre { background: #000; padding: 10px; border-radius: 6px; font-size: 12px; }
        </style>
      </head>
      <body>
        <h1>🌌 Cosmic Ocean: Manual E2E Testing</h1>
        <div class="grid">
          <div>
            <h3>Scenarios</h3>
            <div id="scenarios"></div>
          </div>
          <div>
            <h3>Live Preview (360x640)</h3>
            <div id="preview-container">
              <img id="wallpaper" class="wallpaper-preview" style="display:none;" />
              <div id="loader" style="display:none;">Generating cosmic waves...</div>
            </div>
            <h4>Extraction Logic</h4>
            <pre id="extraction-log"></pre>
          </div>
        </div>

        <script>
          const scenarios = ${JSON.stringify(scenarios)};
          const scenarioList = document.getElementById('scenarios');
          
          scenarios.forEach(s => {
            const btn = document.createElement('button');
            btn.innerText = s.label + ": " + s.input;
            btn.style.display = 'block';
            btn.style.marginBottom = '10px';
            btn.onclick = () => runScenario(s);
            scenarioList.appendChild(btn);
          });

          async function runScenario(s) {
            document.getElementById('loader').style.display = 'block';
            document.getElementById('wallpaper').style.display = 'none';
            
            const res = await fetch('/api/test/run?id=' + s.id);
            const data = await res.json();
            
            document.getElementById('extraction-log').innerText = JSON.stringify(data.parsed, null, 2);
            document.getElementById('wallpaper').src = '/api/wallpaper?t=' + Date.now();
            document.getElementById('wallpaper').style.display = 'block';
            document.getElementById('loader').style.display = 'none';
          }
        </script>
      </body>
    </html>
  `);
});

app.get('/api/test/run', async (req, res) => {
    const scenario = scenarios.find(s => s.id == req.query.id);
    if (!scenario) return res.status(404).json({ error: 'Scenario not found' });

    // 1. Parse
    let parsed;
    if (scenario.type === 'llm') {
        parsed = await parseLLM(scenario.input);
    } else {
        parsed = parseTask(scenario.input);
    }

    // 2. Clear & Add task
    currentTasks = [{
        id: 1,
        user_id: 1,
        title: parsed.title || parsed.task,
        due_date: parsed.dueDate,
        due_time: parsed.dueTime,
        priority: parsed.priority || 2,
        completed: false,
        created_at: new Date().toISOString()
    }];

    res.json({ scenario, parsed });
});

app.get('/api/wallpaper', async (req, res) => {
    const timestamp = new Date();
    const user = {
        id: 1,
        timezone: 'Asia/Kolkata',
        wallpaper_style: 'nebula',
        default_privacy_level: 0
    };

    const prioritized = prioritizeTasks(currentTasks, { timezone: 'Asia/Kolkata' });

    try {
        const buffer = await generateEnhancedWallpaper(user, {
            tasks: prioritized,
            allTasks: prioritized
        }, timestamp, 'Asia/Kolkata');

        res.set('Content-Type', 'image/png');
        res.send(buffer);
    } catch (err) {
        console.error(err);
        res.status(500).send('Generation failed: ' + err.message);
    }
});

const port = 3333;
app.listen(port, () => {
    console.log(`🚀 Manual Test Dashboard running at http://localhost:${port}`);
});

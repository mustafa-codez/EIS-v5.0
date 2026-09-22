package com.eis.server;

import java.io.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class HospitalServer {

    private static final int    PORT    = 9090;
    private static final String DB_FILE = System.getProperty("user.home") + java.io.File.separator + "hospital_records.json";
    private static final ObjectMapper OM = new ObjectMapper();

    // ─── Data model ────────────────────────────────────────────────────────────

    static class DxCandidate {
        final String medicalName;   // e.g. "Myocardial Infarction"
        final String commonName;    // e.g. "Heart Attack"
        final String urgency;       // EMERGENCY / SERIOUS / SAFE
        final String action;        // clinical action string
        final String[] symptoms;    // symptoms that contribute points
        final int[]    weights;     // points per symptom hit
        final int      threshold;   // minimum score to be considered at all
        // Modifiers
        final int minAge, maxAge;   // -1 = no limit
        final String gender;        // "male","female","any"
        // Exclusion: if ALL these symptoms present, skip this candidate
        final String[] excludeIfAll;

        DxCandidate(String med, String com, String urg, String act,
                    String[] syms, int[] wts, int thresh,
                    int minAge, int maxAge, String gender,
                    String[] excludeIfAll) {
            this.medicalName   = med;
            this.commonName    = com;
            this.urgency       = urg;
            this.action        = act;
            this.symptoms      = syms;
            this.weights       = wts;
            this.threshold     = thresh;
            this.minAge        = minAge;
            this.maxAge        = maxAge;
            this.gender        = gender;
            this.excludeIfAll  = excludeIfAll;
        }
    }

    // ─── Knowledge base ────────────────────────────────────────────────────────

    private static final DxCandidate[] CANDIDATES = {

            // ── Cardiac ────────────────────────────────────────────────────────────
            new DxCandidate(
                    "Myocardial Infarction (STEMI/NSTEMI)", "Heart Attack",
                    "EMERGENCY", "12-lead ECG immediately. Aspirin 300mg. Activate cath lab. IV access.",
                    new String[]{"chest pain","arm numbness","rapid heartbeat","lightheaded","blurred vision","loss of consciousness"},
                    new int[]   {      35,          25,              15,             15,             5,                  10},
                    30, 30, -1, "any", new String[]{}),

            new DxCandidate(
                    "Unstable Angina Pectoris", "Severe Angina / Pre-Heart-Attack",
                    "EMERGENCY", "Rest, GTN spray, 12-lead ECG, Troponin serial, cardiology review.",
                    new String[]{"chest pain","arm numbness","rapid heartbeat","lightheaded"},
                    new int[]   {      30,         20,              10,             10},
                    25, 40, -1, "any", new String[]{}),

            new DxCandidate(
                    "Acute Pericarditis", "Heart Sac Inflammation",
                    "SERIOUS", "ECG (saddle ST), CRP, Echo. NSAIDs + Colchicine.",
                    new String[]{"chest pain","rapid heartbeat","lightheaded"},
                    new int[]   {      25,          15,              10},
                    25, 15, 50, "any", new String[]{"arm numbness"}),

            new DxCandidate(
                    "Supraventricular Tachycardia (SVT)", "Racing Heart Episode",
                    "SERIOUS", "Valsalva. Adenosine IV if no response. 12-lead ECG.",
                    new String[]{"rapid heartbeat","lightheaded","chest pain","blurred vision"},
                    new int[]   {       35,              20,           15,            10},
                    30, -1, -1, "any", new String[]{}),

            new DxCandidate(
                    "Hypertensive Emergency", "Dangerously High Blood Pressure Crisis",
                    "EMERGENCY", "IV labetalol/nicardipine. Target MAP reduction <25% in 1hr. ICU.",
                    new String[]{"severe headache","blurred vision","chest pain","rapid heartbeat","lightheaded"},
                    new int[]   {        30,               25,           15,           15,              10},
                    35, 35, -1, "any", new String[]{}),

            // ── Neurological ───────────────────────────────────────────────────────
            new DxCandidate(
                    "Ischaemic Stroke (CVA)", "Stroke",
                    "EMERGENCY", "FAST protocol. CT head now. Stroke team. tPA if within 4.5h window.",
                    new String[]{"facial droop","slurred speech","arm numbness","blurred vision","severe headache","lightheaded"},
                    new int[]   {      35,            30,              20,             15,              10,               5},
                    30, 40, -1, "any", new String[]{}),

            new DxCandidate(
                    "Transient Ischaemic Attack (TIA)", "Mini-Stroke",
                    "SERIOUS", "ABCD2 score. CT/MRI. Aspirin. Cardiology + neurology same day.",
                    new String[]{"facial droop","slurred speech","arm numbness","blurred vision","lightheaded"},
                    new int[]   {      25,            20,              20,             15,             10},
                    25, 40, -1, "any", new String[]{}),

            new DxCandidate(
                    "Status Epilepticus", "Prolonged Seizure",
                    "EMERGENCY", "Lorazepam 4mg IV. Airway management. Call neurology. Glucose check.",
                    new String[]{"twitching","loss of consciousness","rapid heartbeat","blurred vision"},
                    new int[]   {     40,              30,                  10,              5},
                    40, -1, -1, "any", new String[]{}),

            new DxCandidate(
                    "Febrile Seizure", "Fever-Induced Seizure",
                    "SERIOUS", "Paediatric assessment. Antipyretics. Diazepam PR if prolonged.",
                    new String[]{"twitching","loss of consciousness"},
                    new int[]   {     40,              35},
                    40, 0, 5, "any", new String[]{}),

            new DxCandidate(
                    "Migraine with Aura", "Severe Migraine",
                    "SAFE", "Dark quiet room. Triptans or IV metoclopramide. Analgesia.",
                    new String[]{"severe headache","blurred vision","lightheaded","facial droop","slurred speech"},
                    new int[]   {       35,               20,             10,           5,             5},
                    30, 10, 50, "female", new String[]{"chest pain","arm numbness"}),

            new DxCandidate(
                    "Subarachnoid Haemorrhage", "Brain Bleed (Aneurysm Rupture)",
                    "EMERGENCY", "Immediate CT head. Neurosurgery. Nimodipine. ICU.",
                    new String[]{"severe headache","loss of consciousness","blurred vision","facial droop"},
                    new int[]   {        45,                  20,                 15,            10},
                    40, 30, -1, "any", new String[]{}),

            new DxCandidate(
                    "Tension Headache", "Stress Headache",
                    "SAFE", "Analgesia (paracetamol/ibuprofen). Rest. Hydration.",
                    new String[]{"severe headache","lightheaded"},
                    new int[]   {       30,             10},
                    25, 10, -1, "any", new String[]{"facial droop","slurred speech","blurred vision","chest pain"}),

            // ── Respiratory / Allergic ─────────────────────────────────────────────
            new DxCandidate(
                    "Anaphylaxis", "Severe Allergic Reaction",
                    "EMERGENCY", "Adrenaline 0.5mg IM (0.3mg child). O2 15L. IV fluids. Antihistamine.",
                    new String[]{"skin rash","throat swelling","wheezing","rapid heartbeat","lightheaded","fever / chills"},
                    new int[]   {     30,           35,             25,          15,              10,             5},
                    35, -1, -1, "any", new String[]{}),

            new DxCandidate(
                    "Acute Severe Asthma", "Dangerous Asthma Attack",
                    "EMERGENCY", "High-flow O2. Back-to-back salbutamol nebs. IV hydrocortisone.",
                    new String[]{"wheezing","rapid heartbeat","lightheaded","chest pain"},
                    new int[]   {    40,           15,              10,          10},
                    35, -1, -1, "any", new String[]{"skin rash","throat swelling"}),

            new DxCandidate(
                    "Pulmonary Embolism", "Blood Clot in Lung",
                    "EMERGENCY", "O2, CTPA urgently. LMWH or thrombolysis. Haematology.",
                    new String[]{"chest pain","rapid heartbeat","lightheaded","blurred vision","loss of consciousness"},
                    new int[]   {     25,           25,              20,             10,                10},
                    35, 25, -1, "any", new String[]{"arm numbness","facial droop"}),

            // ── Musculoskeletal / Low acuity ───────────────────────────────────────
            new DxCandidate(
                    "Musculoskeletal Chest Pain", "Pulled Muscle / Costochondritis",
                    "SAFE", "NSAIDs, rest, reassurance. ECG to exclude cardiac.",
                    new String[]{"chest pain","lightheaded"},
                    new int[]   {     35,         5},
                    30, 10, -1, "any", new String[]{"arm numbness","rapid heartbeat","facial droop","twitching"}),

            new DxCandidate(
                    "Vasovagal Syncope", "Fainting Episode",
                    "SAFE", "Lie flat, legs elevated. Fluids. Identify trigger. ECG.",
                    new String[]{"loss of consciousness","lightheaded","rapid heartbeat","blurred vision"},
                    new int[]   {         30,                   25,           10,              10},
                    30, -1, -1, "any", new String[]{"twitching","facial droop","chest pain"}),

            // ── Paediatric-specific ────────────────────────────────────────────────
            new DxCandidate(
                    "Kawasaki Disease", "Childhood Inflammatory Artery Disease",
                    "SERIOUS", "Paediatric cardiology. IVIG + aspirin. Echo for coronary involvement.",
                    new String[]{"skin rash","rapid heartbeat","chest pain","fever / chills"},
                    new int[]   {     30,         25,               20,           20},
                    30, 0, 12, "any", new String[]{"wheezing","slurred speech"}),

            new DxCandidate(
                    "Intussusception", "Bowel Telescoping (Child)",
                    "SERIOUS", "Paediatric surgery. Air enema reduction. IV fluids.",
                    new String[]{"rapid heartbeat","loss of consciousness","lightheaded"},
                    new int[]   {       20,                15,                   10},
                    20, 0, 3, "any", new String[]{}),

            // ── Gender-specific ────────────────────────────────────────────────────
            new DxCandidate(
                    "Aortic Dissection", "Torn Main Artery",
                    "EMERGENCY", "CT aortogram. Cardiothoracic surgery. IV labetalol. ICU.",
                    new String[]{"chest pain","severe headache","arm numbness","rapid heartbeat","lightheaded"},
                    new int[]   {     30,            20,              20,             15,              10},
                    35, 40, -1, "male", new String[]{}),

            new DxCandidate(
                    "Ectopic Pregnancy Rupture", "Burst Ectopic Pregnancy",
                    "EMERGENCY", "Immediate OB-GYN. IV access x2. \u03b2hCG + USS. Theatre.",
                    new String[]{"lightheaded","loss of consciousness","rapid heartbeat","chest pain"},
                    new int[]   {     25,                20,                  15,            10},
                    20, 15, 45, "female", new String[]{"twitching","facial droop"}),

            new DxCandidate(
                    "POTS (Postural Tachycardia Syndrome)", "Dizzy-on-Standing Syndrome",
                    "SAFE", "Tilt test. Salt/fluid loading. Fludrocortisone. Cardiology.",
                    new String[]{"rapid heartbeat","lightheaded","blurred vision"},
                    new int[]   {       25,              25,             15},
                    30, 12, 40, "female", new String[]{"chest pain","facial droop"}),

            new DxCandidate(
                    "Sepsis / Septic Shock", "Blood Poisoning",
                    "EMERGENCY", "Blood cultures x2. IV broad-spectrum antibiotics within 1hr. IV fluids 30ml/kg. ICU.",
                    new String[]{"fever / chills","rapid heartbeat","lightheaded","loss of consciousness","blurred vision"},
                    new int[]   {       40,              25,               20,              10,                   5},
                    40, 0, -1, "any", new String[]{"chest pain","twitching","facial droop"}),

            new DxCandidate(
                    "Meningococcal Meningitis", "Brain Membrane Infection",
                    "EMERGENCY", "Ceftriaxone IV immediately. CT head. LP if no papilloedema. Isolation.",
                    new String[]{"severe headache","fever / chills","skin rash","loss of consciousness","blurred vision"},
                    new int[]   {        30,              35,           20,              15,                   10},
                    40, 0, -1, "any", new String[]{"chest pain","twitching"}),
    };

    /** Infer a broad category from the medical name for client-side card styling. */
    private static String inferCategory(String medName) {
        String n = medName.toLowerCase();
        if (n.contains("infarction") || n.contains("angina") || n.contains("pericarditis") ||
                n.contains("tachycardia") || n.contains("hypertensive") || n.contains("aortic") ||
                n.contains("dissection") || n.contains("pots") || n.contains("svt"))
            return "CARDIAC";
        if (n.contains("stroke") || n.contains("ischaemic") || n.contains("epileptic") ||
                n.contains("seizure") || n.contains("migraine") || n.contains("subarachnoid") ||
                n.contains("headache") || n.contains("meningitis"))
            return "NEURO";
        if (n.contains("asthma") || n.contains("embolism") || n.contains("anaphylaxis"))
            return "RESPIRATORY";
        if (n.contains("sepsis") || n.contains("kawasaki") || n.contains("ectopic") ||
                n.contains("intussusception") || n.contains("vasovagal") || n.contains("musculoskeletal"))
            return "SYSTEMIC";
        return "SYSTEMIC";
    }

    // ─── Scoring DDX engine ────────────────────────────────────────────────────

    private static int onsetModifier(String onset, String urgency) {
        return switch (onset.toUpperCase()) {
            case "SUDDEN" -> "EMERGENCY".equals(urgency) ? +20 : "SAFE".equals(urgency) ? -15 : 0;
            case "HOURS"  -> "EMERGENCY".equals(urgency) ?  +8 : 0;
            case "DAYS"   -> "SERIOUS".equals(urgency)   ?  +5 : "EMERGENCY".equals(urgency) ? -10 : 0;
            case "WEEKS"  -> "SAFE".equals(urgency)      ? +10 : "EMERGENCY".equals(urgency) ? -20 : 0;
            case "MONTHS" -> "SAFE".equals(urgency)      ? +12 : "EMERGENCY".equals(urgency) ? -25 : +5;
            default       -> 0;
        };
    }

    static List<Map<String,Object>> runDDX(List<String> symptoms, int age, String gender, String onset) {
        List<Map<String,Object>> scored = new ArrayList<>();

        for (DxCandidate c : CANDIDATES) {
            if (c.minAge >= 0 && age < c.minAge) continue;
            if (c.maxAge >= 0 && age > c.maxAge) continue;

            if (!"any".equals(c.gender)) {
                String g = gender.toLowerCase();
                boolean isMale   = g.equals("male");
                boolean isFemale = g.equals("female");
                if ("male".equals(c.gender)   && !isMale)   continue;
                if ("female".equals(c.gender) && !isFemale) continue;
            }

            if (c.excludeIfAll.length > 0) {
                boolean allPresent = true;
                for (String ex : c.excludeIfAll)
                    if (!symptoms.contains(ex)) { allPresent = false; break; }
                if (allPresent) continue;
            }

            int score = 0;
            int matched = 0;
            for (int i = 0; i < c.symptoms.length; i++) {
                if (symptoms.contains(c.symptoms[i])) {
                    score += c.weights[i];
                    matched++;
                }
            }

            if (score < c.threshold || matched == 0) continue;

            score += onsetModifier(onset, c.urgency);
            if (score < 0) score = 0;

            List<String> matchedList = new ArrayList<>();
            for (String sym : c.symptoms)
                if (symptoms.contains(sym)) matchedList.add(sym);

            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("medicalName",     c.medicalName);
            entry.put("commonName",      c.commonName);
            entry.put("urgency",         c.urgency);
            entry.put("action",          c.action);
            entry.put("score",           score);
            entry.put("matchedSymptoms", matchedList);
            entry.put("category",        inferCategory(c.medicalName));
            scored.add(entry);
        }

        scored.sort((a, b) -> Integer.compare((int)b.get("score"), (int)a.get("score")));
        return scored.size() > 3 ? scored.subList(0, 3) : scored;
    }

    // ─── DB helpers ────────────────────────────────────────────────────────────

    private static synchronized void appendRecord(ObjectNode record) {
        try {
            File f = new File(DB_FILE);
            ArrayNode arr = loadAll();
            arr.add(record);
            OM.writerWithDefaultPrettyPrinter().writeValue(f, arr);
        } catch (Exception e) { System.err.println("[DB ERROR] " + e.getMessage()); }
    }

    static synchronized ArrayNode loadAll() {
        File f = new File(DB_FILE);
        if (!f.exists() || f.length() == 0) return OM.createArrayNode();
        try {
            JsonNode root = OM.readTree(f);
            return root.isArray() ? (ArrayNode) root : OM.createArrayNode();
        } catch (Exception e) { return OM.createArrayNode(); }
    }

    static int recordCount() { return loadAll().size(); }

    // ─── Server ────────────────────────────────────────────────────────────────

    public static void startServer() {
        System.out.println("\n[SYSTEM] Attempting to launch Hospital Server...");
        try (ServerSocket ss = new ServerSocket(PORT)) {
            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║  HOSPITAL SERVER  ·  EIS v5.0            ║");
            System.out.println("║  Status : ONLINE (Port 9090)             ║");
            System.out.println("╚══════════════════════════════════════════╝");
            while (true) {
                Socket client = ss.accept();
                new Thread(new ClientHandler(client)).start();
            }
        } catch (IOException e) {
            System.err.println("[CRITICAL] Could not bind port 9090. Is it already in use? " + e.getMessage());
            System.err.println("[CRITICAL] Server failed to start. The UI will show SERVER OFFLINE.");
        }
    }

    public static void main(String[] args) { startServer(); }

    // ─── Client handler ────────────────────────────────────────────────────────

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        ClientHandler(Socket s) { this.socket = s; }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(5000);

                InputStream rawIn = socket.getInputStream();
                StringBuilder sb  = new StringBuilder();
                int depth = 0; boolean started = false; int b;
                while ((b = rawIn.read()) != -1) {
                    char c = (char) b; sb.append(c);
                    if (c == '{') { depth++; started = true; }
                    else if (c == '}') { depth--; }
                    if (started && depth == 0) break;
                }

                String jsonStr = sb.toString().trim();
                System.out.println("[DEBUG] Received: " + jsonStr);
                if (jsonStr.isEmpty()) return;

                JsonNode root = OM.readTree(jsonStr);
                if (root == null) return;

                @SuppressWarnings("unchecked")
                Map<String,Object> req = OM.convertValue(root, Map.class);
                String responseJson;

                if ("QUERY_ALL".equals(req.get("action"))) {
                    responseJson = OM.writeValueAsString(loadAll());

                } else if ("SAVE".equals(req.get("action"))) {
                    ObjectNode record = (ObjectNode) OM.readTree(OM.writeValueAsString(req.get("record")));
                    if (record.path("id").isMissingNode())
                        record.put("id", String.format("REC-%04d", recordCount() + 1));
                    if (record.path("savedAt").isMissingNode())
                        record.put("savedAt", java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    appendRecord(record);
                    responseJson = "{\"status\":\"OK\"}";

                } else {
                    @SuppressWarnings("unchecked")
                    List<String> symptoms = (List<String>) req.getOrDefault("symptoms", List.of());
                    int age    = req.containsKey("age")    ? ((Number) req.get("age")).intValue() : 30;
                    String gender = (String) req.getOrDefault("gender", "unknown");
                    String onset = (String) req.getOrDefault("onset", "UNKNOWN");
                    List<Map<String,Object>> ddx = runDDX(symptoms, age, gender, onset);

                    ObjectNode resp = OM.createObjectNode();
                    resp.put("name",   (String) req.getOrDefault("name", "Unknown"));
                    resp.put("age",    age);
                    resp.put("gender", gender);
                    resp.put("onset",  (String) req.getOrDefault("onset", "UNKNOWN"));

                    ArrayNode symArr = resp.putArray("symptoms");
                    for (String s : symptoms) symArr.add(s);

                    if (req.containsKey("chiefComplaint"))
                        resp.put("chiefComplaint", (String) req.get("chiefComplaint"));

                    String urgency = ddx.isEmpty() ? "SAFE" : (String) ddx.get(0).get("urgency");
                    resp.put("urgency", urgency);

                    ArrayNode dxArr = resp.putArray("diagnoses");
                    for (Map<String,Object> dx : ddx) {
                        ObjectNode node = dxArr.addObject();
                        node.put("medicalName", (String) dx.get("medicalName"));
                        node.put("commonName",  (String) dx.get("commonName"));
                        node.put("urgency",     (String) dx.get("urgency"));
                        node.put("action",      (String) dx.get("action"));
                        node.put("score",       (int)    dx.get("score"));
                        node.put("category",    (String) dx.get("category"));
                        ArrayNode mSyms = node.putArray("matchedSymptoms");
                        @SuppressWarnings("unchecked")
                        List<String> ms = (List<String>) dx.get("matchedSymptoms");
                        if (ms != null) for (String s : ms) mSyms.add(s);
                    }

                    String[] probs = {"HIGH", "MODERATE", "LOW"};
                    int pi = 0;
                    for (JsonNode dx : resp.path("diagnoses"))
                        ((ObjectNode) dx).put("probability", pi < probs.length ? probs[pi++] : "LOW");

                    responseJson = OM.writeValueAsString(resp);
                }

                OutputStream rawOut = socket.getOutputStream();
                rawOut.write((responseJson + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                rawOut.flush();
                System.out.println("[DEBUG] Sent: " + responseJson);

            } catch (Exception e) {
                System.err.println("[Handler Error] " + e.getMessage());
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }
}

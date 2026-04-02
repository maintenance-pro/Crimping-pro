import { useState, useEffect, useRef, useCallback, useMemo } from "react";

// ═══════════════════════════════════════════════════════════════
// MAINTENANCE MANAGEMENT — LEONI WIRING SYSTEM
// ERP Industriel · GMAO · NASA-Grade Control Center
// ═══════════════════════════════════════════════════════════════

// ── DATA STORE (Simulated Central DB) ──
const INITIAL_DB = {
  users: [
    { id: 1, username: "bilal", password: "bilal2024", role: "super_admin", section: "all", displayName: "Bilal" },
    { id: 2, username: "adil", password: "adil2024", role: "admin", section: "machine", displayName: "Adil" },
    { id: 3, username: "mouaden", password: "mouaden2024", role: "admin", section: "machine", displayName: "Mouaden" },
    { id: 4, username: "oussama", password: "oussama2024", role: "admin", section: "magasin", displayName: "Oussama" },
    { id: 5, username: "nahil", password: "nahil2024", role: "admin", section: "crimping", displayName: "Nahil" },
  ],
  machines: [
    { id: "M-001", name: "Komax Alpha 477", zone: "Coupe", status: "running", health: 92, mttr: 1.2, lastMaint: "2026-03-15", nextMaint: "2026-04-15", project: "BMW F45" },
    { id: "M-002", name: "Schleuniger ES9320", zone: "Coupe", status: "warning", health: 68, mttr: 2.8, lastMaint: "2026-02-20", nextMaint: "2026-04-05", project: "PSA W12" },
    { id: "M-003", name: "Komax Zeta 640", zone: "Coupe", status: "running", health: 95, mttr: 0.8, lastMaint: "2026-03-28", nextMaint: "2026-04-28", project: "VW MQB" },
    { id: "M-004", name: "TE Applicator AMP", zone: "Préparation", status: "stopped", health: 34, mttr: 5.1, lastMaint: "2026-01-10", nextMaint: "2026-04-02", project: "BMW F45" },
    { id: "M-005", name: "Schleuniger PF2000", zone: "Préparation", status: "running", health: 88, mttr: 1.5, lastMaint: "2026-03-20", nextMaint: "2026-04-20", project: "PSA W12" },
    { id: "M-006", name: "Crimping Press CP-1", zone: "Crimping", status: "running", health: 91, mttr: 1.1, lastMaint: "2026-03-25", nextMaint: "2026-04-25", project: "VW MQB" },
    { id: "M-007", name: "Crimping Press CP-2", zone: "Crimping", status: "warning", health: 55, mttr: 3.4, lastMaint: "2026-02-15", nextMaint: "2026-04-03", project: "BMW F45" },
    { id: "M-008", name: "Seal Insertion SI-01", zone: "Kit Joint", status: "running", health: 82, mttr: 1.8, lastMaint: "2026-03-10", nextMaint: "2026-04-10", project: "PSA W12" },
  ],
  spareParts: [
    { id: "SP-001", name: "Lame de coupe Komax", partNo: "KMX-BLD-477", qty: 12, min: 5, max: 30, location: "MAG-A1-R3", supplier: "Komax AG", type: "Lame", group: "Coupe", price: 245.00 },
    { id: "SP-002", name: "Poinçon conducteur TE", partNo: "TE-PC-2040", qty: 3, min: 4, max: 15, location: "MAG-B2-R1", supplier: "TE Connectivity", type: "Poinçon", group: "Crimping", price: 189.50 },
    { id: "SP-003", name: "Enclume isolant AMP", partNo: "AMP-EI-330", qty: 8, min: 3, max: 20, location: "MAG-B2-R2", supplier: "TE Connectivity", type: "Enclume", group: "Crimping", price: 156.00 },
    { id: "SP-004", name: "Joint torique Ø12", partNo: "JT-OR-012", qty: 150, min: 50, max: 500, location: "MAG-C1-R1", supplier: "Parker", type: "Joint", group: "Kit Joint", price: 0.85 },
    { id: "SP-005", name: "Courroie Schleuniger", partNo: "SCH-BLT-93", qty: 2, min: 3, max: 10, location: "MAG-A2-R4", supplier: "Schleuniger", type: "Courroie", group: "Coupe", price: 78.30 },
    { id: "SP-006", name: "Ressort applicateur", partNo: "RST-APP-05", qty: 0, min: 5, max: 25, location: "MAG-B1-R3", supplier: "TE Connectivity", type: "Ressort", group: "Crimping", price: 12.40 },
    { id: "SP-007", name: "Capteur optique Kx", partNo: "KMX-OPT-22", qty: 4, min: 2, max: 8, location: "MAG-A1-R5", supplier: "Komax AG", type: "Capteur", group: "Coupe", price: 520.00 },
    { id: "SP-008", name: "Seal plug connector", partNo: "SPC-44-BLK", qty: 500, min: 100, max: 2000, location: "MAG-C2-R1", supplier: "Yazaki", type: "Seal", group: "Kit Joint", price: 0.15 },
  ],
  crimpingTools: [
    { id: "CT-001", invNo: "INV-2024-001", terminal: "TE-MQS-0.5", connexion: "MQS 0.5mm²", group: "A", poinconCond: "PC-A1", poinconIsol: "PI-A1", enclumeCond: "EC-A1", supplier: "TE Connectivity", project: "BMW F45", status: "Intégré sur CAO" },
    { id: "CT-002", invNo: "INV-2024-002", terminal: "TE-MLK-1.0", connexion: "MLK 1.0mm²", group: "B", poinconCond: "PC-B2", poinconIsol: "PI-B2", enclumeCond: "EC-B2", supplier: "TE Connectivity", project: "PSA W12", status: "Nouveau" },
    { id: "CT-003", invNo: "INV-2024-003", terminal: "YZK-SWS-0.35", connexion: "SWS 0.35mm²", group: "A", poinconCond: "PC-A3", poinconIsol: "PI-A3", enclumeCond: "EC-A3", supplier: "Yazaki", project: "VW MQB", status: "Intégré sur CAO" },
  ],
  preventiveTasks: [
    { id: "PT-001", machineId: "M-001", task: "Lubrification lames", freq: "Hebdo", nextDate: "2026-04-07", assignee: "Adil", status: "planifié", signed: false },
    { id: "PT-002", machineId: "M-002", task: "Calibration capteurs", freq: "Mensuel", nextDate: "2026-04-05", assignee: "Mouaden", status: "en_retard", signed: false },
    { id: "PT-003", machineId: "M-004", task: "Remplacement courroie", freq: "Trimestriel", nextDate: "2026-04-02", assignee: "Adil", status: "urgent", signed: false },
    { id: "PT-004", machineId: "M-006", task: "Vérification couple sertissage", freq: "Hebdo", nextDate: "2026-04-08", assignee: "Nahil", status: "planifié", signed: false },
    { id: "PT-005", machineId: "M-008", task: "Nettoyage station joints", freq: "Quotidien", nextDate: "2026-04-03", assignee: "Oussama", status: "planifié", signed: false },
  ],
  suppliers: [
    { id: "S-001", name: "Komax AG", color: "#00d4ff", partsCount: 3, contact: "komax@supply.ch" },
    { id: "S-002", name: "TE Connectivity", color: "#ff6b35", partsCount: 4, contact: "te@connect.com" },
    { id: "S-003", name: "Schleuniger", color: "#7cff54", partsCount: 2, contact: "info@schleuniger.ch" },
    { id: "S-004", name: "Parker", color: "#ffd700", partsCount: 1, contact: "parker@seal.com" },
    { id: "S-005", name: "Yazaki", color: "#ff54a0", partsCount: 2, contact: "yazaki@jp.com" },
  ],
  standards: [
    { id: "STD-001", title: "Standard Sertissage LEONI V3.2", type: "PDF", category: "Crimping", date: "2025-11-15" },
    { id: "STD-002", title: "Procédure Maintenance Préventive", type: "PDF", category: "Maintenance", date: "2025-09-01" },
    { id: "STD-003", title: "Inspection Visuelle Joints", type: "Image", category: "Kit Joint", date: "2026-01-20" },
    { id: "STD-004", title: "Calibration Outils Coupe", type: "PDF", category: "Coupe", date: "2025-12-10" },
  ],
};

// ── ICONS (SVG inline) ──
const Icon = ({ name, size = 18, color = "currentColor" }) => {
  const icons = {
    dashboard: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    machine: <><rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 6V4a2 2 0 012-2h8a2 2 0 012 2v2"/><circle cx="12" cy="12" r="2"/></>,
    warehouse: <><path d="M3 21V8l9-5 9 5v13"/><path d="M9 21V12h6v9"/></>,
    joint: <><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/><circle cx="12" cy="12" r="1"/></>,
    crimping: <><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></>,
    qrcode: <><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="3" height="3"/><rect x="19" y="14" width="2" height="2"/><rect x="14" y="19" width="2" height="2"/><rect x="19" y="19" width="2" height="2"/></>,
    calendar: <><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></>,
    library: <><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/></>,
    admin: <><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4z"/><path d="M6 20v-2c0-2.21 3.58-4 6-4s6 1.79 6 4v2"/></>,
    alert: <><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></>,
    check: <><polyline points="20 6 9 17 4 12"/></>,
    lock: <><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></>,
    logout: <><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></>,
    search: <><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></>,
    settings: <><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></>,
    plus: <><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></>,
    chevronRight: <><polyline points="9 18 15 12 9 6"/></>,
    refresh: <><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></>,
    upload: <><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></>,
    bell: <><path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/></>,
    signature: <><path d="M2 17c1 0 2-1 3-3s2-4 3-4 2 6 3 6 2-8 3-8 2 10 3 10 2-5 3-5"/></>,
    star: <><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></>,
  };
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {icons[name]}
    </svg>
  );
};

// ── HEALTH GAUGE COMPONENT (3D-like Sphere) ──
const HealthGauge = ({ value, label, size = 160 }) => {
  const circumference = 2 * Math.PI * 54;
  const progress = ((100 - value) / 100) * circumference;
  const color = value >= 80 ? "#00ff88" : value >= 50 ? "#ffaa00" : "#ff3355";
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
      <div style={{ position: "relative", width: size, height: size }}>
        <svg width={size} height={size} viewBox="0 0 120 120">
          <defs>
            <radialGradient id={`gauge-bg-${label}`} cx="40%" cy="35%" r="60%">
              <stop offset="0%" stopColor="#1a2340" />
              <stop offset="100%" stopColor="#0a0f1e" />
            </radialGradient>
            <filter id={`glow-${label}`}>
              <feGaussianBlur stdDeviation="3" result="blur" />
              <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
            </filter>
          </defs>
          <circle cx="60" cy="60" r="56" fill={`url(#gauge-bg-${label})`} stroke="#1e2a4a" strokeWidth="2" />
          <circle cx="60" cy="60" r="54" fill="none" stroke="#111a30" strokeWidth="6" />
          <circle cx="60" cy="60" r="54" fill="none" stroke={color} strokeWidth="6" strokeDasharray={circumference} strokeDashoffset={progress} strokeLinecap="round" transform="rotate(-90 60 60)" filter={`url(#glow-${label})`} style={{ transition: "stroke-dashoffset 1.5s ease" }} />
          <circle cx="42" cy="38" r="12" fill="rgba(255,255,255,0.04)" />
          <text x="60" y="55" textAnchor="middle" fill={color} fontSize="26" fontWeight="800" fontFamily="'JetBrains Mono', monospace">{value}</text>
          <text x="60" y="70" textAnchor="middle" fill="#5a6a8a" fontSize="9" fontWeight="500" letterSpacing="1">SCORE</text>
        </svg>
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%", boxShadow: `0 0 30px ${color}22, inset 0 0 20px ${color}08`, pointerEvents: "none" }} />
      </div>
      <span style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600 }}>{label}</span>
    </div>
  );
};

// ── KPI CARD ──
const KPICard = ({ icon, label, value, unit, trend, color = "#00d4ff" }) => (
  <div style={{
    background: "linear-gradient(135deg, #0d1528 0%, #111d35 100%)",
    border: "1px solid #1a2845",
    borderRadius: 12,
    padding: "18px 20px",
    flex: 1,
    minWidth: 160,
    position: "relative",
    overflow: "hidden",
  }}>
    <div style={{ position: "absolute", top: -20, right: -20, width: 80, height: 80, borderRadius: "50%", background: `${color}06` }} />
    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
      <div style={{ width: 32, height: 32, borderRadius: 8, background: `${color}15`, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <span style={{ fontSize: 16 }}>{icon}</span>
      </div>
      <span style={{ color: "#6b7fa3", fontSize: 11, letterSpacing: 1, textTransform: "uppercase", fontWeight: 600 }}>{label}</span>
    </div>
    <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
      <span style={{ color, fontSize: 28, fontWeight: 800, fontFamily: "'JetBrains Mono', monospace" }}>{value}</span>
      {unit && <span style={{ color: "#4a5a7a", fontSize: 12 }}>{unit}</span>}
    </div>
    {trend && <span style={{ color: trend > 0 ? "#00ff88" : "#ff3355", fontSize: 11, fontWeight: 600 }}>{trend > 0 ? "↑" : "↓"} {Math.abs(trend)}%</span>}
  </div>
);

// ── STATUS BADGE ──
const StatusBadge = ({ status }) => {
  const config = {
    running: { bg: "#00ff8815", color: "#00ff88", border: "#00ff8830", label: "En marche" },
    warning: { bg: "#ffaa0015", color: "#ffaa00", border: "#ffaa0030", label: "Attention" },
    stopped: { bg: "#ff335515", color: "#ff3355", border: "#ff335530", label: "Arrêt" },
    planifié: { bg: "#00d4ff15", color: "#00d4ff", border: "#00d4ff30", label: "Planifié" },
    en_retard: { bg: "#ffaa0015", color: "#ffaa00", border: "#ffaa0030", label: "En retard" },
    urgent: { bg: "#ff335515", color: "#ff3355", border: "#ff335530", label: "Urgent" },
  }[status] || { bg: "#ffffff10", color: "#888", border: "#ffffff20", label: status };
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", gap: 5,
      padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600,
      background: config.bg, color: config.color, border: `1px solid ${config.border}`,
    }}>
      <span style={{ width: 6, height: 6, borderRadius: "50%", background: config.color, boxShadow: `0 0 6px ${config.color}` }} />
      {config.label}
    </span>
  );
};

// ── MINI BAR CHART ──
const MiniBar = ({ data, height = 120 }) => {
  const max = Math.max(...data.map(d => d.value), 1);
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 6, height, padding: "0 4px" }}>
      {data.map((d, i) => (
        <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
          <span style={{ color: "#6b7fa3", fontSize: 9, fontWeight: 600 }}>{d.value}</span>
          <div style={{
            width: "100%", maxWidth: 28,
            height: `${(d.value / max) * 100}%`,
            minHeight: 4,
            background: `linear-gradient(to top, ${d.color || "#00d4ff"}, ${d.color || "#00d4ff"}88)`,
            borderRadius: "4px 4px 0 0",
            transition: "height 0.8s ease",
            boxShadow: `0 0 8px ${d.color || "#00d4ff"}33`,
          }} />
          <span style={{ color: "#4a5a7a", fontSize: 9, fontWeight: 500, whiteSpace: "nowrap" }}>{d.label}</span>
        </div>
      ))}
    </div>
  );
};

// ── QR CODE GENERATOR (Simple matrix) ──
const QRCodeDisplay = ({ data, size = 120 }) => {
  const matrix = useMemo(() => {
    const s = 21;
    const grid = Array.from({ length: s }, () => Array(s).fill(false));
    // Finder patterns
    for (let i = 0; i < 7; i++) for (let j = 0; j < 7; j++) {
      const fill = i === 0 || i === 6 || j === 0 || j === 6 || (i >= 2 && i <= 4 && j >= 2 && j <= 4);
      grid[i][j] = fill;
      grid[i][s - 1 - j] = fill;
      grid[s - 1 - i][j] = fill;
    }
    // Data encoding simulation
    let hash = 0;
    for (let c = 0; c < data.length; c++) hash = ((hash << 5) - hash + data.charCodeAt(c)) | 0;
    for (let i = 8; i < s - 8; i++) for (let j = 8; j < s; j++) {
      const seed = (hash * (i + 1) * (j + 1)) & 0xffff;
      grid[i][j] = seed % 3 !== 0;
    }
    for (let i = 0; i < 8; i++) for (let j = 8; j < s - 8; j++) {
      const seed = (hash * (i + 1) * (j + 31)) & 0xffff;
      grid[i][j] = i > 0 && seed % 4 !== 0;
    }
    return grid;
  }, [data]);

  const cellSize = size / 21;
  return (
    <div style={{ background: "#fff", padding: 8, borderRadius: 6, display: "inline-block" }}>
      <svg width={size} height={size}>
        {matrix.map((row, i) => row.map((cell, j) => cell ? (
          <rect key={`${i}-${j}`} x={j * cellSize} y={i * cellSize} width={cellSize + 0.5} height={cellSize + 0.5} fill="#0a0f1e" />
        ) : null))}
      </svg>
    </div>
  );
};

// ── CALENDAR GRID ──
const CalendarGrid = ({ tasks, month = 3, year = 2026 }) => {
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstDay = new Date(year, month, 1).getDay();
  const dayNames = ["Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"];
  const cells = [];
  for (let i = 0; i < (firstDay === 0 ? 6 : firstDay - 1); i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const getTasksForDay = (day) => {
    if (!day) return [];
    const dateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    return tasks.filter(t => t.nextDate === dateStr);
  };

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 2, marginBottom: 4 }}>
        {dayNames.map(d => (
          <div key={d} style={{ padding: "6px 4px", textAlign: "center", color: "#4a5a7a", fontSize: 10, fontWeight: 700, letterSpacing: 1 }}>{d}</div>
        ))}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 2 }}>
        {cells.map((day, i) => {
          const dayTasks = getTasksForDay(day);
          const hasUrgent = dayTasks.some(t => t.status === "urgent");
          const hasLate = dayTasks.some(t => t.status === "en_retard");
          const isToday = day === 2;
          return (
            <div key={i} style={{
              padding: "6px 4px", minHeight: 44, borderRadius: 6,
              background: !day ? "transparent" : isToday ? "#00d4ff12" : "#0a101e",
              border: isToday ? "1px solid #00d4ff40" : "1px solid #111a2e",
              position: "relative",
            }}>
              {day && (
                <>
                  <span style={{ fontSize: 11, color: isToday ? "#00d4ff" : "#6b7fa3", fontWeight: isToday ? 700 : 400 }}>{day}</span>
                  {dayTasks.length > 0 && (
                    <div style={{ display: "flex", gap: 2, marginTop: 3, flexWrap: "wrap" }}>
                      {dayTasks.map((t, j) => (
                        <div key={j} style={{
                          width: 6, height: 6, borderRadius: "50%",
                          background: t.status === "urgent" ? "#ff3355" : t.status === "en_retard" ? "#ffaa00" : "#00d4ff",
                          boxShadow: `0 0 4px ${t.status === "urgent" ? "#ff3355" : t.status === "en_retard" ? "#ffaa00" : "#00d4ff"}`,
                        }} title={t.task} />
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════
// MAIN APPLICATION
// ═══════════════════════════════════════════════════════════════
export default function MaintenanceERP() {
  const [db, setDB] = useState(INITIAL_DB);
  const [currentUser, setCurrentUser] = useState(null);
  const [activeModule, setActiveModule] = useState("dashboard");
  const [loginForm, setLoginForm] = useState({ username: "", password: "" });
  const [loginError, setLoginError] = useState("");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedMachine, setSelectedMachine] = useState(null);
  const [qrInput, setQrInput] = useState("LEONI-M-001");
  const [notifications, setNotifications] = useState([]);
  const [showNotifications, setShowNotifications] = useState(false);
  const [signedTasks, setSignedTasks] = useState({});
  const [calendarMonth, setCalendarMonth] = useState(3);

  // Compute alerts
  useEffect(() => {
    const alerts = [];
    db.spareParts.filter(p => p.qty <= p.min).forEach(p => {
      alerts.push({
        id: p.id,
        type: p.qty === 0 ? "critical" : "warning",
        message: p.qty === 0 ? `RUPTURE: ${p.name}` : `Stock bas: ${p.name} (${p.qty}/${p.min})`,
        location: p.location,
        time: "Maintenant",
      });
    });
    db.preventiveTasks.filter(t => t.status === "urgent" || t.status === "en_retard").forEach(t => {
      const machine = db.machines.find(m => m.id === t.machineId);
      alerts.push({
        id: t.id,
        type: t.status === "urgent" ? "critical" : "warning",
        message: `${t.status === "urgent" ? "URGENT" : "RETARD"}: ${t.task} — ${machine?.name}`,
        location: machine?.zone,
        time: t.nextDate,
      });
    });
    setNotifications(alerts);
  }, [db]);

  // RBAC filter
  const getFilteredMachines = useCallback(() => {
    if (!currentUser) return [];
    if (currentUser.role === "super_admin") return db.machines;
    if (currentUser.section === "machine") return db.machines.filter(m => ["Coupe", "Préparation"].includes(m.zone));
    if (currentUser.section === "crimping") return db.machines.filter(m => m.zone === "Crimping");
    if (currentUser.section === "magasin") return db.machines;
    return db.machines;
  }, [currentUser, db.machines]);

  const getFilteredParts = useCallback(() => {
    if (!currentUser) return [];
    if (currentUser.role === "super_admin") return db.spareParts;
    if (currentUser.section === "crimping") return db.spareParts.filter(p => p.group === "Crimping");
    if (currentUser.section === "machine") return db.spareParts.filter(p => ["Coupe", "Préparation"].includes(p.group));
    return db.spareParts;
  }, [currentUser, db.spareParts]);

  // Module access control
  const modules = useMemo(() => {
    const all = [
      { id: "dashboard", label: "Dashboard", icon: "dashboard", sections: ["all"] },
      { id: "machines", label: "Machines", icon: "machine", sections: ["all", "machine"] },
      { id: "magasin", label: "Magasin", icon: "warehouse", sections: ["all", "magasin"] },
      { id: "kitjoint", label: "Kit Joint", icon: "joint", sections: ["all", "magasin", "kitjoint"] },
      { id: "crimping", label: "Crimping", icon: "crimping", sections: ["all", "crimping"] },
      { id: "stock", label: "Pièces & Stock", icon: "settings", sections: ["all", "magasin", "machine", "crimping"] },
      { id: "preventif", label: "Planning Préventif", icon: "calendar", sections: ["all", "machine", "crimping"] },
      { id: "qrcode", label: "QR Code", icon: "qrcode", sections: ["all", "machine", "crimping", "magasin"] },
      { id: "standards", label: "Standard Library", icon: "library", sections: ["all"] },
      { id: "admins", label: "Administration", icon: "admin", sections: ["all"] },
    ];
    if (!currentUser) return [];
    if (currentUser.role === "super_admin") return all;
    return all.filter(m => m.sections.includes(currentUser.section));
  }, [currentUser]);

  // Login handler
  const handleLogin = () => {
    const user = db.users.find(u => u.username === loginForm.username && u.password === loginForm.password);
    if (user) {
      setCurrentUser(user);
      setLoginError("");
    } else {
      setLoginError("Identifiants incorrects");
    }
  };

  // Global health score
  const globalHealth = useMemo(() => {
    const machines = db.machines;
    if (machines.length === 0) return 0;
    return Math.round(machines.reduce((s, m) => s + m.health, 0) / machines.length);
  }, [db.machines]);

  const avgMTTR = useMemo(() => {
    const machines = db.machines;
    if (machines.length === 0) return 0;
    return (machines.reduce((s, m) => s + m.mttr, 0) / machines.length).toFixed(1);
  }, [db.machines]);

  const preventiveRate = useMemo(() => {
    const total = db.preventiveTasks.length;
    const done = db.preventiveTasks.filter(t => t.status === "planifié").length;
    return total > 0 ? Math.round((done / total) * 100) : 0;
  }, [db.preventiveTasks]);

  // ── LOGIN SCREEN ──
  if (!currentUser) {
    return (
      <div style={{
        minHeight: "100vh",
        background: "radial-gradient(ellipse at 30% 20%, #0a1628 0%, #060c18 50%, #030812 100%)",
        display: "flex", alignItems: "center", justifyContent: "center",
        fontFamily: "'Segoe UI', -apple-system, sans-serif",
        overflow: "hidden", position: "relative",
      }}>
        {/* Animated grid background */}
        <div style={{
          position: "absolute", inset: 0, opacity: 0.04,
          backgroundImage: "linear-gradient(#00d4ff 1px, transparent 1px), linear-gradient(90deg, #00d4ff 1px, transparent 1px)",
          backgroundSize: "60px 60px",
        }} />
        {/* Glowing orbs */}
        <div style={{ position: "absolute", top: "15%", left: "20%", width: 300, height: 300, borderRadius: "50%", background: "radial-gradient(circle, #00d4ff08, transparent)", filter: "blur(60px)" }} />
        <div style={{ position: "absolute", bottom: "20%", right: "15%", width: 250, height: 250, borderRadius: "50%", background: "radial-gradient(circle, #ff335508, transparent)", filter: "blur(50px)" }} />

        <div style={{
          width: 400, padding: 40, borderRadius: 20,
          background: "linear-gradient(135deg, #0d1528cc, #111d35cc)",
          border: "1px solid #1a2845",
          backdropFilter: "blur(20px)",
          boxShadow: "0 25px 80px rgba(0,0,0,0.5), 0 0 40px #00d4ff08",
          position: "relative", zIndex: 1,
        }}>
          {/* Logo */}
          <div style={{ textAlign: "center", marginBottom: 32 }}>
            <div style={{
              width: 64, height: 64, margin: "0 auto 16px", borderRadius: 16,
              background: "linear-gradient(135deg, #00d4ff, #0088ff)",
              display: "flex", alignItems: "center", justifyContent: "center",
              boxShadow: "0 8px 30px #00d4ff33",
            }}>
              <Icon name="settings" size={32} color="#fff" />
            </div>
            <h1 style={{ color: "#e8edf5", fontSize: 18, fontWeight: 700, margin: "0 0 4px", letterSpacing: 0.5 }}>MAINTENANCE MANAGEMENT</h1>
            <p style={{ color: "#00d4ff", fontSize: 11, fontWeight: 600, letterSpacing: 3, margin: 0 }}>LEONI WIRING SYSTEM</p>
          </div>

          {loginError && (
            <div style={{ background: "#ff335515", border: "1px solid #ff335530", borderRadius: 8, padding: "8px 12px", marginBottom: 16, color: "#ff5577", fontSize: 12, textAlign: "center" }}>
              ⚠ {loginError}
            </div>
          )}

          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div>
              <label style={{ display: "block", color: "#5a6a8a", fontSize: 11, fontWeight: 600, marginBottom: 6, letterSpacing: 1, textTransform: "uppercase" }}>Nom d'utilisateur</label>
              <input
                type="text" value={loginForm.username}
                onChange={e => setLoginForm(p => ({ ...p, username: e.target.value }))}
                onKeyDown={e => e.key === "Enter" && handleLogin()}
                style={{
                  width: "100%", padding: "12px 14px", borderRadius: 10,
                  background: "#080e1e", border: "1px solid #1a2845", color: "#e8edf5",
                  fontSize: 14, outline: "none", boxSizing: "border-box",
                  transition: "border-color 0.2s",
                }}
                onFocus={e => e.target.style.borderColor = "#00d4ff50"}
                onBlur={e => e.target.style.borderColor = "#1a2845"}
                placeholder="admin"
              />
            </div>
            <div>
              <label style={{ display: "block", color: "#5a6a8a", fontSize: 11, fontWeight: 600, marginBottom: 6, letterSpacing: 1, textTransform: "uppercase" }}>Mot de passe</label>
              <input
                type="password" value={loginForm.password}
                onChange={e => setLoginForm(p => ({ ...p, password: e.target.value }))}
                onKeyDown={e => e.key === "Enter" && handleLogin()}
                style={{
                  width: "100%", padding: "12px 14px", borderRadius: 10,
                  background: "#080e1e", border: "1px solid #1a2845", color: "#e8edf5",
                  fontSize: 14, outline: "none", boxSizing: "border-box",
                }}
                onFocus={e => e.target.style.borderColor = "#00d4ff50"}
                onBlur={e => e.target.style.borderColor = "#1a2845"}
                placeholder="••••••••"
              />
            </div>
            <button
              onClick={handleLogin}
              style={{
                marginTop: 8, padding: "13px 0", borderRadius: 10, border: "none",
                background: "linear-gradient(135deg, #00d4ff, #0088ff)",
                color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer",
                letterSpacing: 0.5,
                boxShadow: "0 4px 20px #00d4ff33",
                transition: "transform 0.1s, box-shadow 0.2s",
              }}
              onMouseDown={e => e.target.style.transform = "scale(0.98)"}
              onMouseUp={e => e.target.style.transform = "scale(1)"}
            >
              Connexion Sécurisée
            </button>
          </div>

          <div style={{ marginTop: 24, padding: "12px 14px", borderRadius: 10, background: "#00d4ff08", border: "1px solid #00d4ff15" }}>
            <p style={{ color: "#4a6a8a", fontSize: 10, margin: 0, lineHeight: 1.6, textAlign: "center" }}>
              🔐 Accès sécurisé · RBAC activé · Chaque admin accède uniquement à sa section
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ── MAIN LAYOUT ──
  return (
    <div style={{
      minHeight: "100vh",
      background: "#060c18",
      display: "flex",
      fontFamily: "'Segoe UI', -apple-system, sans-serif",
      color: "#c8d4e8",
      fontSize: 13,
    }}>
      {/* ── SIDEBAR ── */}
      <aside style={{
        width: sidebarCollapsed ? 64 : 230,
        background: "linear-gradient(180deg, #0a1225 0%, #080e1c 100%)",
        borderRight: "1px solid #141e35",
        display: "flex", flexDirection: "column",
        transition: "width 0.3s ease",
        flexShrink: 0,
        overflow: "hidden",
      }}>
        {/* Brand */}
        <div style={{ padding: sidebarCollapsed ? "16px 8px" : "20px 18px", borderBottom: "1px solid #141e35" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }} onClick={() => setSidebarCollapsed(!sidebarCollapsed)}>
            <div style={{
              width: 36, height: 36, borderRadius: 10, flexShrink: 0,
              background: "linear-gradient(135deg, #00d4ff, #0088ff)",
              display: "flex", alignItems: "center", justifyContent: "center",
            }}>
              <Icon name="settings" size={20} color="#fff" />
            </div>
            {!sidebarCollapsed && (
              <div>
                <div style={{ color: "#e8edf5", fontSize: 13, fontWeight: 700 }}>LEONI MMS</div>
                <div style={{ color: "#00d4ff", fontSize: 9, letterSpacing: 2, fontWeight: 600 }}>MAINTENANCE PRO</div>
              </div>
            )}
          </div>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: "12px 8px", display: "flex", flexDirection: "column", gap: 2 }}>
          {modules.map(mod => (
            <button
              key={mod.id}
              onClick={() => setActiveModule(mod.id)}
              style={{
                display: "flex", alignItems: "center", gap: 10,
                padding: sidebarCollapsed ? "10px 14px" : "10px 14px",
                borderRadius: 8, border: "none", cursor: "pointer",
                background: activeModule === mod.id ? "#00d4ff12" : "transparent",
                color: activeModule === mod.id ? "#00d4ff" : "#5a6a8a",
                fontSize: 12.5, fontWeight: activeModule === mod.id ? 600 : 400,
                textAlign: "left", width: "100%",
                transition: "all 0.15s",
                borderLeft: activeModule === mod.id ? "2px solid #00d4ff" : "2px solid transparent",
              }}
            >
              <Icon name={mod.icon} size={18} color={activeModule === mod.id ? "#00d4ff" : "#4a5a7a"} />
              {!sidebarCollapsed && <span>{mod.label}</span>}
              {!sidebarCollapsed && mod.id === "stock" && db.spareParts.filter(p => p.qty <= p.min).length > 0 && (
                <span style={{ marginLeft: "auto", background: "#ff3355", color: "#fff", fontSize: 9, fontWeight: 700, padding: "1px 6px", borderRadius: 10 }}>
                  {db.spareParts.filter(p => p.qty <= p.min).length}
                </span>
              )}
            </button>
          ))}
        </nav>

        {/* User */}
        <div style={{ padding: "14px 12px", borderTop: "1px solid #141e35" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{
              width: 32, height: 32, borderRadius: 8,
              background: currentUser.role === "super_admin" ? "linear-gradient(135deg, #ffd700, #ff8c00)" : "linear-gradient(135deg, #00d4ff, #0066cc)",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 13, fontWeight: 700, color: "#fff",
            }}>
              {currentUser.displayName[0]}
            </div>
            {!sidebarCollapsed && (
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ color: "#c8d4e8", fontSize: 12, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {currentUser.displayName}
                </div>
                <div style={{ color: currentUser.role === "super_admin" ? "#ffd700" : "#00d4ff", fontSize: 10, fontWeight: 600 }}>
                  {currentUser.role === "super_admin" ? "Super Admin" : `Admin · ${currentUser.section}`}
                </div>
              </div>
            )}
            {!sidebarCollapsed && (
              <button onClick={() => { setCurrentUser(null); setLoginForm({ username: "", password: "" }); }} style={{ background: "none", border: "none", cursor: "pointer", padding: 4 }}>
                <Icon name="logout" size={16} color="#5a6a8a" />
              </button>
            )}
          </div>
        </div>
      </aside>

      {/* ── MAIN CONTENT ── */}
      <main style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        {/* Top Bar */}
        <header style={{
          height: 56, padding: "0 24px",
          background: "linear-gradient(90deg, #0a1225, #0d1528)",
          borderBottom: "1px solid #141e35",
          display: "flex", alignItems: "center", gap: 16,
        }}>
          <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ position: "relative" }}>
              <Icon name="search" size={16} color="#3a4a6a" />
            </div>
            <input
              type="text" placeholder="Rechercher machines, pièces, outils..."
              value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
              style={{
                background: "#080e1e", border: "1px solid #1a2845", borderRadius: 8,
                padding: "7px 12px", color: "#c8d4e8", fontSize: 12, width: 280,
                outline: "none",
              }}
            />
          </div>

          {/* Notifications Bell */}
          <div style={{ position: "relative" }}>
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              style={{ background: "none", border: "none", cursor: "pointer", position: "relative", padding: 6 }}
            >
              <Icon name="bell" size={20} color="#5a6a8a" />
              {notifications.length > 0 && (
                <span style={{
                  position: "absolute", top: 2, right: 2, width: 16, height: 16, borderRadius: "50%",
                  background: "#ff3355", color: "#fff", fontSize: 9, fontWeight: 700,
                  display: "flex", alignItems: "center", justifyContent: "center",
                }}>{notifications.length}</span>
              )}
            </button>

            {showNotifications && (
              <div style={{
                position: "absolute", top: "100%", right: 0, width: 340,
                background: "#0d1528", border: "1px solid #1a2845", borderRadius: 12,
                boxShadow: "0 10px 40px rgba(0,0,0,0.5)", zIndex: 100,
                maxHeight: 400, overflowY: "auto",
              }}>
                <div style={{ padding: "14px 16px", borderBottom: "1px solid #141e35" }}>
                  <span style={{ color: "#e8edf5", fontWeight: 700, fontSize: 13 }}>Alertes Prédictives</span>
                </div>
                {notifications.map((n, i) => (
                  <div key={i} style={{
                    padding: "10px 16px", borderBottom: "1px solid #0a1020",
                    display: "flex", gap: 10, alignItems: "flex-start",
                  }}>
                    <span style={{
                      width: 8, height: 8, borderRadius: "50%", marginTop: 4, flexShrink: 0,
                      background: n.type === "critical" ? "#ff3355" : "#ffaa00",
                      boxShadow: `0 0 8px ${n.type === "critical" ? "#ff3355" : "#ffaa00"}`,
                    }} />
                    <div>
                      <div style={{ color: "#c8d4e8", fontSize: 12, fontWeight: 500 }}>{n.message}</div>
                      <div style={{ color: "#4a5a7a", fontSize: 10, marginTop: 2 }}>📍 {n.location} · {n.time}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={{ color: "#3a4a6a", fontSize: 11 }}>
            {new Date().toLocaleDateString("fr-FR", { weekday: "long", year: "numeric", month: "long", day: "numeric" })}
          </div>
        </header>

        {/* Content Area */}
        <div style={{ flex: 1, overflow: "auto", padding: 24 }}>
          {/* ═══ DASHBOARD ═══ */}
          {activeModule === "dashboard" && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 24 }}>
                <div>
                  <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Centre de Contrôle</h2>
                  <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Vue d'ensemble temps réel · LEONI Wiring System</p>
                </div>
                <button style={{
                  display: "flex", alignItems: "center", gap: 6,
                  padding: "8px 16px", borderRadius: 8, border: "1px solid #1a2845",
                  background: "#0d1528", color: "#00d4ff", fontSize: 12, cursor: "pointer",
                }}>
                  <Icon name="refresh" size={14} color="#00d4ff" /> Actualiser
                </button>
              </div>

              {/* Health Score Row */}
              <div style={{ display: "flex", gap: 20, marginBottom: 24, flexWrap: "wrap" }}>
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 16, padding: 24,
                  display: "flex", alignItems: "center", gap: 32, flex: "1 1 400px",
                }}>
                  <HealthGauge value={globalHealth} label="Santé Usine" size={150} />
                  <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 12 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ color: "#6b7fa3", fontSize: 11 }}>Machines en marche</span>
                      <span style={{ color: "#00ff88", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>
                        {db.machines.filter(m => m.status === "running").length}/{db.machines.length}
                      </span>
                    </div>
                    <div style={{ height: 1, background: "#141e35" }} />
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ color: "#6b7fa3", fontSize: 11 }}>Alertes actives</span>
                      <span style={{ color: "#ff3355", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{notifications.length}</span>
                    </div>
                    <div style={{ height: 1, background: "#141e35" }} />
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ color: "#6b7fa3", fontSize: 11 }}>Taux préventif</span>
                      <span style={{ color: "#00d4ff", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{preventiveRate}%</span>
                    </div>
                  </div>
                </div>

                {/* Zone Health */}
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 16, padding: 24,
                  flex: "1 1 300px",
                }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 16px" }}>Santé par Zone</h3>
                  <div style={{ display: "flex", gap: 16, justifyContent: "center" }}>
                    {["Coupe", "Préparation", "Crimping", "Kit Joint"].map(zone => {
                      const zoneMachines = db.machines.filter(m => m.zone === zone);
                      const avg = zoneMachines.length > 0 ? Math.round(zoneMachines.reduce((s, m) => s + m.health, 0) / zoneMachines.length) : 0;
                      return <HealthGauge key={zone} value={avg} label={zone} size={90} />;
                    })}
                  </div>
                </div>
              </div>

              {/* KPI Row */}
              <div style={{ display: "flex", gap: 12, marginBottom: 24, flexWrap: "wrap" }}>
                <KPICard icon="🔧" label="Machines" value={db.machines.length} trend={5} color="#00d4ff" />
                <KPICard icon="⏱" label="MTTR Moyen" value={avgMTTR} unit="h" trend={-12} color="#00ff88" />
                <KPICard icon="📦" label="Pièces en stock" value={db.spareParts.length} color="#7c54ff" />
                <KPICard icon="⚠" label="Alertes Stock" value={db.spareParts.filter(p => p.qty <= p.min).length} color="#ff3355" />
                <KPICard icon="🏢" label="Fournisseurs" value={db.suppliers.length} trend={2} color="#ffd700" />
              </div>

              {/* Charts Row */}
              <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 16, padding: 24,
                  flex: "1 1 300px",
                }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 16px" }}>MTTR par Machine (heures)</h3>
                  <MiniBar data={db.machines.map(m => ({
                    label: m.id,
                    value: m.mttr,
                    color: m.mttr > 3 ? "#ff3355" : m.mttr > 2 ? "#ffaa00" : "#00ff88",
                  }))} height={100} />
                </div>

                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 16, padding: 24,
                  flex: "1 1 300px",
                }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 16px" }}>Niveau de Stock par Pièce</h3>
                  <MiniBar data={db.spareParts.slice(0, 8).map(p => ({
                    label: p.id,
                    value: p.qty,
                    color: p.qty === 0 ? "#ff3355" : p.qty <= p.min ? "#ffaa00" : "#00d4ff",
                  }))} height={100} />
                </div>
              </div>
            </div>
          )}

          {/* ═══ MACHINES ═══ */}
          {activeModule === "machines" && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
                <div>
                  <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Gestion des Machines</h2>
                  <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Coupe · Préparation · Pièces de Rechange</p>
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid #1a2845", background: "#0d1528", color: "#00d4ff", fontSize: 11, cursor: "pointer", display: "flex", alignItems: "center", gap: 5 }}>
                    <Icon name="upload" size={14} color="#00d4ff" /> Import Excel
                  </button>
                  <button style={{ padding: "8px 14px", borderRadius: 8, border: "none", background: "linear-gradient(135deg, #00d4ff, #0088ff)", color: "#fff", fontSize: 11, cursor: "pointer", display: "flex", alignItems: "center", gap: 5, fontWeight: 600 }}>
                    <Icon name="plus" size={14} color="#fff" /> Nouvelle Machine
                  </button>
                </div>
              </div>

              {/* Machine cards */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 14 }}>
                {getFilteredMachines().filter(m => !searchQuery || m.name.toLowerCase().includes(searchQuery.toLowerCase()) || m.id.toLowerCase().includes(searchQuery.toLowerCase())).map(machine => (
                  <div
                    key={machine.id}
                    onClick={() => setSelectedMachine(selectedMachine?.id === machine.id ? null : machine)}
                    style={{
                      background: "linear-gradient(135deg, #0d1528, #111d35)",
                      border: selectedMachine?.id === machine.id ? "1px solid #00d4ff40" : "1px solid #1a2845",
                      borderRadius: 14, padding: 18, cursor: "pointer",
                      transition: "all 0.2s",
                      boxShadow: selectedMachine?.id === machine.id ? "0 0 20px #00d4ff10" : "none",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
                      <div>
                        <div style={{ color: "#e8edf5", fontSize: 14, fontWeight: 600 }}>{machine.name}</div>
                        <div style={{ color: "#4a5a7a", fontSize: 11, marginTop: 2 }}>{machine.id} · {machine.zone}</div>
                      </div>
                      <StatusBadge status={machine.status} />
                    </div>

                    {/* Mini health bar */}
                    <div style={{ marginBottom: 10 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                        <span style={{ color: "#6b7fa3", fontSize: 10 }}>Santé</span>
                        <span style={{ color: machine.health >= 80 ? "#00ff88" : machine.health >= 50 ? "#ffaa00" : "#ff3355", fontSize: 11, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{machine.health}%</span>
                      </div>
                      <div style={{ height: 4, background: "#0a101e", borderRadius: 2 }}>
                        <div style={{
                          height: "100%", borderRadius: 2,
                          width: `${machine.health}%`,
                          background: machine.health >= 80 ? "linear-gradient(90deg, #00ff88, #00cc66)" : machine.health >= 50 ? "linear-gradient(90deg, #ffaa00, #ff8800)" : "linear-gradient(90deg, #ff3355, #cc0033)",
                          transition: "width 0.8s ease",
                          boxShadow: `0 0 6px ${machine.health >= 80 ? "#00ff8844" : machine.health >= 50 ? "#ffaa0044" : "#ff335544"}`,
                        }} />
                      </div>
                    </div>

                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#4a5a7a" }}>
                      <span>MTTR: <span style={{ color: "#c8d4e8", fontWeight: 600 }}>{machine.mttr}h</span></span>
                      <span>Projet: <span style={{ color: "#00d4ff", fontWeight: 600 }}>{machine.project}</span></span>
                    </div>

                    {selectedMachine?.id === machine.id && (
                      <div style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid #141e35" }}>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: 11 }}>
                          <div><span style={{ color: "#4a5a7a" }}>Dernière maint:</span> <span style={{ color: "#c8d4e8" }}>{machine.lastMaint}</span></div>
                          <div><span style={{ color: "#4a5a7a" }}>Prochaine:</span> <span style={{ color: "#ffaa00" }}>{machine.nextMaint}</span></div>
                        </div>
                        <div style={{ display: "flex", gap: 6, marginTop: 10 }}>
                          <button style={{ flex: 1, padding: "6px 0", borderRadius: 6, border: "1px solid #00d4ff30", background: "#00d4ff10", color: "#00d4ff", fontSize: 10, cursor: "pointer", fontWeight: 600 }}>QR Code</button>
                          <button style={{ flex: 1, padding: "6px 0", borderRadius: 6, border: "1px solid #00ff8830", background: "#00ff8810", color: "#00ff88", fontSize: 10, cursor: "pointer", fontWeight: 600 }}>Historique</button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ═══ MAGASIN ═══ */}
          {activeModule === "magasin" && (
            <div>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Magasin</h2>
                <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Gestion globale du flux entrant · Admin: Oussama</p>
              </div>

              <div style={{ display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" }}>
                <KPICard icon="📦" label="Total Pièces" value={db.spareParts.reduce((s, p) => s + p.qty, 0)} color="#00d4ff" />
                <KPICard icon="📍" label="Emplacements" value={[...new Set(db.spareParts.map(p => p.location))].length} color="#7c54ff" />
                <KPICard icon="✅" label="Disponible" value={db.spareParts.filter(p => p.qty > p.min).length} color="#00ff88" />
                <KPICard icon="🔴" label="Rupture" value={db.spareParts.filter(p => p.qty === 0).length} color="#ff3355" />
              </div>

              {/* Supplier distribution */}
              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20, marginBottom: 20,
              }}>
                <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 14px" }}>Répartition par Fournisseur</h3>
                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                  {db.suppliers.map(s => (
                    <div key={s.id} style={{
                      padding: "10px 16px", borderRadius: 10,
                      background: `${s.color}08`, border: `1px solid ${s.color}25`,
                      display: "flex", alignItems: "center", gap: 8,
                    }}>
                      <span style={{ width: 10, height: 10, borderRadius: "50%", background: s.color, boxShadow: `0 0 6px ${s.color}` }} />
                      <span style={{ color: "#c8d4e8", fontSize: 12, fontWeight: 500 }}>{s.name}</span>
                      <span style={{ color: s.color, fontSize: 11, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{s.partsCount}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Recent entries table */}
              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20,
              }}>
                <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 14px" }}>Flux Entrant Récent</h3>
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr>
                        {["Réf.", "Désignation", "Qté", "Emplacement", "Fournisseur", "Status"].map(h => (
                          <th key={h} style={{ padding: "8px 12px", textAlign: "left", color: "#4a5a7a", fontSize: 10, fontWeight: 700, letterSpacing: 1, textTransform: "uppercase", borderBottom: "1px solid #141e35" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {db.spareParts.map(p => (
                        <tr key={p.id} style={{ borderBottom: "1px solid #0a101e" }}>
                          <td style={{ padding: "8px 12px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#00d4ff" }}>{p.partNo}</td>
                          <td style={{ padding: "8px 12px", color: "#c8d4e8", fontSize: 12 }}>{p.name}</td>
                          <td style={{ padding: "8px 12px" }}>
                            <span style={{ color: p.qty === 0 ? "#ff3355" : p.qty <= p.min ? "#ffaa00" : "#00ff88", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{p.qty}</span>
                          </td>
                          <td style={{ padding: "8px 12px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#7c54ff" }}>{p.location}</td>
                          <td style={{ padding: "8px 12px", color: "#c8d4e8", fontSize: 12 }}>{p.supplier}</td>
                          <td style={{ padding: "8px 12px" }}>
                            <StatusBadge status={p.qty === 0 ? "stopped" : p.qty <= p.min ? "warning" : "running"} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ═══ KIT JOINT ═══ */}
          {activeModule === "kitjoint" && (
            <div>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Kit Joint</h2>
                <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Gestion des joints et seals · Section dédiée</p>
              </div>

              <div style={{ display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" }}>
                <KPICard icon="⭕" label="Réf. Joints" value={db.spareParts.filter(p => p.group === "Kit Joint").length} color="#ff54a0" />
                <KPICard icon="📦" label="Stock Total" value={db.spareParts.filter(p => p.group === "Kit Joint").reduce((s, p) => s + p.qty, 0)} unit="pcs" color="#00d4ff" />
                <KPICard icon="🔧" label="Machines Associées" value={db.machines.filter(m => m.zone === "Kit Joint").length} color="#7c54ff" />
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 14 }}>
                {db.spareParts.filter(p => p.group === "Kit Joint").map(p => (
                  <div key={p.id} style={{
                    background: "linear-gradient(135deg, #0d1528, #111d35)",
                    border: "1px solid #1a2845", borderRadius: 14, padding: 18,
                  }}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
                      <span style={{ color: "#e8edf5", fontWeight: 600, fontSize: 13 }}>{p.name}</span>
                      <StatusBadge status={p.qty === 0 ? "stopped" : p.qty <= p.min ? "warning" : "running"} />
                    </div>
                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: 11 }}>
                      <div><span style={{ color: "#4a5a7a" }}>N° Pièce:</span> <span style={{ color: "#00d4ff", fontFamily: "'JetBrains Mono', monospace" }}>{p.partNo}</span></div>
                      <div><span style={{ color: "#4a5a7a" }}>Quantité:</span> <span style={{ color: p.qty <= p.min ? "#ff3355" : "#00ff88", fontWeight: 700 }}>{p.qty}</span></div>
                      <div><span style={{ color: "#4a5a7a" }}>Min/Max:</span> <span style={{ color: "#c8d4e8" }}>{p.min}/{p.max}</span></div>
                      <div><span style={{ color: "#4a5a7a" }}>Emplacement:</span> <span style={{ color: "#7c54ff" }}>{p.location}</span></div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ═══ CRIMPING ═══ */}
          {activeModule === "crimping" && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
                <div>
                  <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Crimping Pro</h2>
                  <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Gestion des outils de sertissage · Dashboard dédié</p>
                </div>
                <button style={{ padding: "8px 14px", borderRadius: 8, border: "none", background: "linear-gradient(135deg, #00d4ff, #0088ff)", color: "#fff", fontSize: 11, cursor: "pointer", fontWeight: 600 }}>
                  + Nouvel Outil
                </button>
              </div>

              <div style={{ display: "flex", gap: 12, marginBottom: 20, flexWrap: "wrap" }}>
                <KPICard icon="🔧" label="Total Outils" value={db.crimpingTools.length} color="#00d4ff" />
                <KPICard icon="🔌" label="Connexions" value={[...new Set(db.crimpingTools.map(t => t.connexion))].length} color="#ff6b35" />
                <KPICard icon="🏢" label="Fournisseurs" value={[...new Set(db.crimpingTools.map(t => t.supplier))].length} color="#7cff54" />
                <KPICard icon="📈" label="Taux CAO" value={Math.round((db.crimpingTools.filter(t => t.status === "Intégré sur CAO").length / db.crimpingTools.length) * 100)} unit="%" color="#ffd700" />
              </div>

              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20,
              }}>
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr>
                        {["N° Inventaire", "Terminal", "Connexion", "Groupe", "Poinçon Cond.", "Poinçon Isol.", "Enclume", "Fournisseur", "Projet", "État"].map(h => (
                          <th key={h} style={{ padding: "8px 10px", textAlign: "left", color: "#4a5a7a", fontSize: 10, fontWeight: 700, letterSpacing: 0.5, textTransform: "uppercase", borderBottom: "1px solid #141e35", whiteSpace: "nowrap" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {db.crimpingTools.map(t => (
                        <tr key={t.id} style={{ borderBottom: "1px solid #0a101e" }}>
                          <td style={{ padding: "8px 10px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#00d4ff" }}>{t.invNo}</td>
                          <td style={{ padding: "8px 10px", color: "#c8d4e8", fontSize: 12 }}>{t.terminal}</td>
                          <td style={{ padding: "8px 10px", color: "#c8d4e8", fontSize: 12 }}>{t.connexion}</td>
                          <td style={{ padding: "8px 10px" }}><span style={{ background: "#7c54ff20", color: "#7c54ff", padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 600 }}>{t.group}</span></td>
                          <td style={{ padding: "8px 10px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#c8d4e8" }}>{t.poinconCond}</td>
                          <td style={{ padding: "8px 10px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#c8d4e8" }}>{t.poinconIsol}</td>
                          <td style={{ padding: "8px 10px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#c8d4e8" }}>{t.enclumeCond}</td>
                          <td style={{ padding: "8px 10px", color: "#ff6b35", fontSize: 12 }}>{t.supplier}</td>
                          <td style={{ padding: "8px 10px", color: "#00d4ff", fontSize: 12, fontWeight: 500 }}>{t.project}</td>
                          <td style={{ padding: "8px 10px" }}>
                            <span style={{
                              padding: "3px 8px", borderRadius: 4, fontSize: 10, fontWeight: 600,
                              background: t.status === "Intégré sur CAO" ? "#00ff8815" : t.status === "Nouveau" ? "#00d4ff15" : "#ffaa0015",
                              color: t.status === "Intégré sur CAO" ? "#00ff88" : t.status === "Nouveau" ? "#00d4ff" : "#ffaa00",
                            }}>{t.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ═══ STOCK ═══ */}
          {activeModule === "stock" && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
                <div>
                  <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Pièces de Rechange & Stock</h2>
                  <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Gestion centralisée · Alertes seuil automatiques</p>
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid #1a2845", background: "#0d1528", color: "#00d4ff", fontSize: 11, cursor: "pointer" }}>📂 Import Excel</button>
                </div>
              </div>

              {/* Alert banner */}
              {db.spareParts.filter(p => p.qty <= p.min).length > 0 && (
                <div style={{
                  background: "#ff335510", border: "1px solid #ff335525", borderRadius: 12,
                  padding: "12px 18px", marginBottom: 18, display: "flex", alignItems: "center", gap: 12,
                }}>
                  <span style={{ fontSize: 20 }}>⚠</span>
                  <div>
                    <div style={{ color: "#ff5577", fontWeight: 600, fontSize: 12 }}>Alertes Stock Critiques</div>
                    <div style={{ color: "#ff557788", fontSize: 11, marginTop: 2 }}>
                      {db.spareParts.filter(p => p.qty <= p.min).map(p => `${p.name} (📍${p.location})`).join(" · ")}
                    </div>
                  </div>
                </div>
              )}

              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20,
              }}>
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr>
                        {["Réf.", "Nom", "Qté", "Min", "Max", "Emplacement", "Fournisseur", "Type", "Prix", "Status"].map(h => (
                          <th key={h} style={{ padding: "8px 10px", textAlign: "left", color: "#4a5a7a", fontSize: 10, fontWeight: 700, letterSpacing: 0.5, textTransform: "uppercase", borderBottom: "1px solid #141e35", whiteSpace: "nowrap" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {getFilteredParts().map(p => (
                        <tr key={p.id} style={{ borderBottom: "1px solid #0a101e", background: p.qty === 0 ? "#ff335506" : p.qty <= p.min ? "#ffaa0006" : "transparent" }}>
                          <td style={{ padding: "8px 10px", fontFamily: "'JetBrains Mono', monospace", fontSize: 11, color: "#00d4ff" }}>{p.partNo}</td>
                          <td style={{ padding: "8px 10px", color: "#c8d4e8", fontSize: 12 }}>{p.name}</td>
                          <td style={{ padding: "8px 10px" }}>
                            <span style={{ color: p.qty === 0 ? "#ff3355" : p.qty <= p.min ? "#ffaa00" : "#00ff88", fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{p.qty}</span>
                          </td>
                          <td style={{ padding: "8px 10px", color: "#6b7fa3", fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{p.min}</td>
                          <td style={{ padding: "8px 10px", color: "#6b7fa3", fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{p.max}</td>
                          <td style={{ padding: "8px 10px" }}>
                            <span style={{ background: "#7c54ff15", color: "#7c54ff", padding: "2px 8px", borderRadius: 4, fontSize: 10, fontWeight: 600, fontFamily: "'JetBrains Mono', monospace" }}>{p.location}</span>
                          </td>
                          <td style={{ padding: "8px 10px", color: "#c8d4e8", fontSize: 12 }}>{p.supplier}</td>
                          <td style={{ padding: "8px 10px", color: "#6b7fa3", fontSize: 11 }}>{p.type}</td>
                          <td style={{ padding: "8px 10px", color: "#ffd700", fontFamily: "'JetBrains Mono', monospace", fontSize: 11 }}>{p.price.toFixed(2)}€</td>
                          <td style={{ padding: "8px 10px" }}><StatusBadge status={p.qty === 0 ? "stopped" : p.qty <= p.min ? "warning" : "running"} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* ═══ PLANNING PREVENTIF ═══ */}
          {activeModule === "preventif" && (
            <div>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Planning Préventif</h2>
                <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Calendrier interactif · Signature numérique</p>
              </div>

              <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
                {/* Calendar */}
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 14, padding: 20,
                  flex: "1 1 320px",
                }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                    <button onClick={() => setCalendarMonth(p => Math.max(0, p - 1))} style={{ background: "none", border: "none", color: "#00d4ff", cursor: "pointer", fontSize: 16 }}>◀</button>
                    <span style={{ color: "#e8edf5", fontWeight: 600, fontSize: 14 }}>
                      {new Date(2026, calendarMonth).toLocaleDateString("fr-FR", { month: "long", year: "numeric" })}
                    </span>
                    <button onClick={() => setCalendarMonth(p => Math.min(11, p + 1))} style={{ background: "none", border: "none", color: "#00d4ff", cursor: "pointer", fontSize: 16 }}>▶</button>
                  </div>
                  <CalendarGrid tasks={db.preventiveTasks} month={calendarMonth} />
                  <div style={{ display: "flex", gap: 12, marginTop: 12, justifyContent: "center" }}>
                    {[{ color: "#00d4ff", label: "Planifié" }, { color: "#ffaa00", label: "En retard" }, { color: "#ff3355", label: "Urgent" }].map(l => (
                      <div key={l.label} style={{ display: "flex", alignItems: "center", gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: "50%", background: l.color }} />
                        <span style={{ color: "#6b7fa3", fontSize: 10 }}>{l.label}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Task list */}
                <div style={{ flex: "1 1 340px", display: "flex", flexDirection: "column", gap: 10 }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: 0 }}>Tâches à venir</h3>
                  {db.preventiveTasks.map(task => {
                    const machine = db.machines.find(m => m.id === task.machineId);
                    return (
                      <div key={task.id} style={{
                        background: "linear-gradient(135deg, #0d1528, #111d35)",
                        border: `1px solid ${task.status === "urgent" ? "#ff335530" : "#1a2845"}`,
                        borderRadius: 12, padding: 14,
                      }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
                          <div>
                            <div style={{ color: "#e8edf5", fontSize: 13, fontWeight: 600 }}>{task.task}</div>
                            <div style={{ color: "#4a5a7a", fontSize: 11, marginTop: 2 }}>{machine?.name} · {task.freq}</div>
                          </div>
                          <StatusBadge status={task.status} />
                        </div>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                          <div style={{ fontSize: 11 }}>
                            <span style={{ color: "#4a5a7a" }}>Date: </span>
                            <span style={{ color: "#00d4ff", fontFamily: "'JetBrains Mono', monospace" }}>{task.nextDate}</span>
                            <span style={{ color: "#4a5a7a", marginLeft: 10 }}>Assigné: </span>
                            <span style={{ color: "#c8d4e8" }}>{task.assignee}</span>
                          </div>
                          <button
                            onClick={() => setSignedTasks(p => ({ ...p, [task.id]: !p[task.id] }))}
                            style={{
                              padding: "5px 12px", borderRadius: 6, border: "none", cursor: "pointer", fontSize: 10, fontWeight: 600,
                              background: signedTasks[task.id] ? "#00ff8820" : "#1a2845",
                              color: signedTasks[task.id] ? "#00ff88" : "#6b7fa3",
                              display: "flex", alignItems: "center", gap: 4,
                            }}
                          >
                            {signedTasks[task.id] ? "✓ Signé" : "✍ Signer"}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          )}

          {/* ═══ QR CODE ═══ */}
          {activeModule === "qrcode" && (
            <div>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Générateur QR Code</h2>
                <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Pour chaque pièce et machine · Scan rapide</p>
              </div>

              <div style={{ display: "flex", gap: 20, flexWrap: "wrap" }}>
                {/* Generator */}
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 14, padding: 24,
                  flex: "1 1 280px", display: "flex", flexDirection: "column", alignItems: "center", gap: 16,
                }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: 0 }}>Générer un QR Code</h3>
                  <input
                    type="text" value={qrInput} onChange={e => setQrInput(e.target.value)}
                    style={{
                      width: "100%", padding: "10px 14px", borderRadius: 8,
                      background: "#080e1e", border: "1px solid #1a2845", color: "#e8edf5",
                      fontSize: 13, outline: "none", textAlign: "center",
                      boxSizing: "border-box",
                    }}
                    placeholder="ID machine ou pièce..."
                  />
                  <QRCodeDisplay data={qrInput} size={160} />
                  <div style={{ color: "#00d4ff", fontFamily: "'JetBrains Mono', monospace", fontSize: 12, fontWeight: 600 }}>{qrInput}</div>
                  <button style={{
                    padding: "8px 20px", borderRadius: 8, border: "none",
                    background: "linear-gradient(135deg, #00d4ff, #0088ff)", color: "#fff",
                    fontSize: 11, fontWeight: 600, cursor: "pointer",
                  }}>🖨 Imprimer</button>
                </div>

                {/* Quick QR for all machines */}
                <div style={{
                  background: "linear-gradient(135deg, #0d1528, #111d35)",
                  border: "1px solid #1a2845", borderRadius: 14, padding: 24,
                  flex: "2 1 400px",
                }}>
                  <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 16px" }}>QR Codes Machines</h3>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(120px, 1fr))", gap: 12 }}>
                    {db.machines.map(m => (
                      <div key={m.id} onClick={() => setQrInput(m.id)} style={{
                        background: "#080e1e", borderRadius: 10, padding: 12,
                        display: "flex", flexDirection: "column", alignItems: "center", gap: 6,
                        cursor: "pointer", border: qrInput === m.id ? "1px solid #00d4ff40" : "1px solid #111a2e",
                        transition: "all 0.2s",
                      }}>
                        <QRCodeDisplay data={m.id} size={64} />
                        <span style={{ color: "#00d4ff", fontSize: 10, fontWeight: 600, fontFamily: "'JetBrains Mono', monospace" }}>{m.id}</span>
                        <span style={{ color: "#6b7fa3", fontSize: 9, textAlign: "center" }}>{m.name.split(" ")[0]}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ═══ STANDARD LIBRARY ═══ */}
          {activeModule === "standards" && (
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
                <div>
                  <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Standard Library</h2>
                  <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Documents PDF/Images des standards LEONI</p>
                </div>
                <button style={{ padding: "8px 14px", borderRadius: 8, border: "none", background: "linear-gradient(135deg, #00d4ff, #0088ff)", color: "#fff", fontSize: 11, cursor: "pointer", fontWeight: 600 }}>
                  + Ajouter Document
                </button>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 14 }}>
                {db.standards.map(doc => (
                  <div key={doc.id} style={{
                    background: "linear-gradient(135deg, #0d1528, #111d35)",
                    border: "1px solid #1a2845", borderRadius: 14, padding: 18,
                  }}>
                    <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
                      <div style={{
                        width: 44, height: 44, borderRadius: 10,
                        background: doc.type === "PDF" ? "#ff335518" : "#7c54ff18",
                        display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
                      }}>
                        <span style={{ fontSize: 20 }}>{doc.type === "PDF" ? "📄" : "🖼"}</span>
                      </div>
                      <div>
                        <div style={{ color: "#e8edf5", fontSize: 13, fontWeight: 600 }}>{doc.title}</div>
                        <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
                          <span style={{ background: "#00d4ff15", color: "#00d4ff", padding: "2px 8px", borderRadius: 4, fontSize: 10, fontWeight: 600 }}>{doc.category}</span>
                          <span style={{ color: "#4a5a7a", fontSize: 10 }}>{doc.date}</span>
                        </div>
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 6, marginTop: 12 }}>
                      <button style={{ flex: 1, padding: "6px 0", borderRadius: 6, border: "1px solid #00d4ff30", background: "#00d4ff10", color: "#00d4ff", fontSize: 10, cursor: "pointer", fontWeight: 600 }}>Voir</button>
                      <button style={{ flex: 1, padding: "6px 0", borderRadius: 6, border: "1px solid #1a2845", background: "#0a101e", color: "#6b7fa3", fontSize: 10, cursor: "pointer" }}>Télécharger</button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ═══ ADMINISTRATION ═══ */}
          {activeModule === "admins" && (
            <div>
              <div style={{ marginBottom: 20 }}>
                <h2 style={{ color: "#e8edf5", fontSize: 20, fontWeight: 700, margin: 0 }}>Administration</h2>
                <p style={{ color: "#4a5a7a", fontSize: 12, margin: "4px 0 0" }}>Gestion des utilisateurs · RBAC</p>
              </div>

              {/* Schema Card */}
              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20, marginBottom: 20,
              }}>
                <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 14px" }}>Schéma RBAC — Contrôle d'Accès</h3>
                <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
                  {[
                    { role: "Super Admin", user: "Bilal", sections: "Toutes les sections", color: "#ffd700" },
                    { role: "Admin Machine", user: "Adil, Mouaden", sections: "Coupe, Préparation", color: "#00d4ff" },
                    { role: "Admin Magasin", user: "Oussama", sections: "Magasin, Kit Joint", color: "#7cff54" },
                    { role: "Admin Crimping", user: "Nahil", sections: "Crimping", color: "#ff6b35" },
                  ].map(r => (
                    <div key={r.role} style={{
                      flex: "1 1 200px", padding: 14, borderRadius: 10,
                      background: `${r.color}06`, border: `1px solid ${r.color}20`,
                    }}>
                      <div style={{ color: r.color, fontSize: 12, fontWeight: 700, marginBottom: 6 }}>{r.role}</div>
                      <div style={{ color: "#c8d4e8", fontSize: 12 }}>{r.user}</div>
                      <div style={{ color: "#4a5a7a", fontSize: 10, marginTop: 4 }}>Accès: {r.sections}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Users Table */}
              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20,
              }}>
                <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 14px" }}>Utilisateurs Actifs</h3>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr>
                      {["Utilisateur", "Rôle", "Section", "Status"].map(h => (
                        <th key={h} style={{ padding: "8px 12px", textAlign: "left", color: "#4a5a7a", fontSize: 10, fontWeight: 700, letterSpacing: 1, textTransform: "uppercase", borderBottom: "1px solid #141e35" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {db.users.map(u => (
                      <tr key={u.id} style={{ borderBottom: "1px solid #0a101e" }}>
                        <td style={{ padding: "10px 12px" }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                            <div style={{
                              width: 30, height: 30, borderRadius: 8,
                              background: u.role === "super_admin" ? "linear-gradient(135deg, #ffd700, #ff8c00)" : "linear-gradient(135deg, #00d4ff, #0066cc)",
                              display: "flex", alignItems: "center", justifyContent: "center",
                              color: "#fff", fontSize: 12, fontWeight: 700,
                            }}>{u.displayName[0]}</div>
                            <span style={{ color: "#e8edf5", fontWeight: 500 }}>{u.displayName}</span>
                          </div>
                        </td>
                        <td style={{ padding: "10px 12px" }}>
                          <span style={{
                            padding: "3px 10px", borderRadius: 4, fontSize: 10, fontWeight: 600,
                            background: u.role === "super_admin" ? "#ffd70018" : "#00d4ff15",
                            color: u.role === "super_admin" ? "#ffd700" : "#00d4ff",
                          }}>{u.role === "super_admin" ? "Super Admin" : "Admin"}</span>
                        </td>
                        <td style={{ padding: "10px 12px", color: "#c8d4e8", fontSize: 12 }}>{u.section === "all" ? "Toutes" : u.section}</td>
                        <td style={{ padding: "10px 12px" }}><StatusBadge status="running" /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* DB Schema */}
              <div style={{
                background: "linear-gradient(135deg, #0d1528, #111d35)",
                border: "1px solid #1a2845", borderRadius: 14, padding: 20, marginTop: 20,
              }}>
                <h3 style={{ color: "#8899bb", fontSize: 11, letterSpacing: 1.5, textTransform: "uppercase", fontWeight: 600, margin: "0 0 14px" }}>Schéma Base de Données Relationnelle</h3>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10 }}>
                  {[
                    { table: "Users", fields: ["id PK", "username", "password_hash", "role FK", "section FK", "created_at"] },
                    { table: "Machines", fields: ["id PK", "name", "zone FK", "status", "health_score", "mttr", "project FK"] },
                    { table: "SpareParts", fields: ["id PK", "part_no", "name", "qty", "min/max", "location", "supplier FK"] },
                    { table: "CrimpingTools", fields: ["id PK", "inv_no", "terminal", "connexion", "poincons", "supplier FK"] },
                    { table: "PreventiveTasks", fields: ["id PK", "machine FK", "task", "freq", "next_date", "assignee FK"] },
                    { table: "Standards", fields: ["id PK", "title", "type", "category", "file_path", "uploaded_at"] },
                  ].map(t => (
                    <div key={t.table} style={{ background: "#080e1e", borderRadius: 8, overflow: "hidden", border: "1px solid #111a2e" }}>
                      <div style={{ padding: "8px 12px", background: "#00d4ff10", borderBottom: "1px solid #111a2e" }}>
                        <span style={{ color: "#00d4ff", fontSize: 12, fontWeight: 700, fontFamily: "'JetBrains Mono', monospace" }}>{t.table}</span>
                      </div>
                      <div style={{ padding: "8px 12px" }}>
                        {t.fields.map(f => (
                          <div key={f} style={{ color: "#6b7fa3", fontSize: 10, fontFamily: "'JetBrains Mono', monospace", padding: "2px 0", borderBottom: "1px solid #0a101e" }}>
                            {f.includes("PK") ? <span style={{ color: "#ffd700" }}>{f}</span> : f.includes("FK") ? <span style={{ color: "#ff54a0" }}>{f}</span> : f}
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

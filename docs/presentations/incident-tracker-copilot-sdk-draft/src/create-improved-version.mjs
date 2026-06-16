import { createRequire } from "node:module";
import { execFile } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";
import fs from "node:fs/promises";
import path from "node:path";
import { promisify } from "node:util";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const deckRoot = path.resolve(__dirname, "..");
const outputDir = path.join(deckRoot, "output");
const reviewDir = path.join(deckRoot, "review-improved");
const sourcePptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-architecture.pptx");
const improvedPptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-improved.pptx");
const execFileAsync = promisify(execFile);

const runtimeNodeHome =
  process.env.CODEX_PRIMARY_RUNTIME_NODE_HOME ??
  "C:/Users/mknie/.cache/codex-runtimes/codex-primary-runtime/dependencies/node";
const runtimeRequire = createRequire(path.join(runtimeNodeHome, "package.json"));
const artifactToolPath = runtimeRequire.resolve("@oai/artifact-tool");
const artifactTool = await import(pathToFileURL(artifactToolPath).href);

const {
  PresentationFile,
  row,
  column,
  grid,
  layers,
  panel,
  text,
  shape,
  rule,
  fill,
  hug,
  fixed,
  wrap,
  fr,
} = artifactTool;

const W = 1920;
const H = 1080;

const C = {
  bg: "#F5F5F7",
  ink: "#1D1D1F",
  muted: "#6E6E73",
  line: "#D2D2D7",
  softLine: "#E5E5EA",
  paper: "#FFFFFF",
  dark: "#1D1D1F",
  darkText: "#F5F5F7",
  darkMuted: "#A1A1A6",
  darkPanel: "#2C2C2E",
  red: "#FF0A0A",
  blue: "#0071E3",
  green: "#34C759",
  violet: "#AF52DE",
  orange: "#FF9F0A",
};

const font = {
  display: "Aptos Display",
  body: "Aptos",
  mono: "Cascadia Mono",
};

function textStyle(size, color = C.ink, extra = {}) {
  return {
    fontFamily: extra.mono ? font.mono : extra.display ? font.display : font.body,
    fontSize: size,
    color,
    bold: extra.bold ?? false,
    alignment: extra.alignment ?? "left",
  };
}

function t(value, size, color = C.ink, extra = {}) {
  return text(value, {
    width: extra.width ?? fill,
    height: extra.height ?? hug,
    style: textStyle(size, color, extra),
  });
}

function topRail(section, dark = false) {
  return row({ width: fill, height: hug, gap: 24, align: "center" }, [
    t("INCIDENT TRACKER", 18, dark ? C.darkMuted : C.muted, { width: fixed(190), bold: true }),
    rule({ width: fill, stroke: dark ? "#424245" : C.line, weight: 1 }),
    t(section.toUpperCase(), 18, dark ? C.darkMuted : C.muted, {
      width: fixed(320),
      bold: true,
      alignment: "right",
    }),
  ]);
}

function footer(slideNo, dark = false) {
  return row({ width: fill, height: hug, align: "center", gap: 24 }, [
    t("Team Delivery Workspace · AI tooling philosophy", 18, dark ? C.darkMuted : C.muted),
    t(String(slideNo).padStart(2, "0"), 18, dark ? C.darkMuted : C.muted, {
      width: fixed(44),
      bold: true,
      alignment: "right",
    }),
  ]);
}

function shell(slide, section, slideNo, body, dark = false) {
  slide.compose(
    layers({ width: fill, height: fill }, [
      shape({ width: fill, height: fill, fill: dark ? C.dark : C.bg }),
      column({ width: fill, height: fill, padding: { x: 96, y: 70 }, gap: 24 }, [
        topRail(section, dark),
        body,
        footer(slideNo, dark),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
}

function accentBar(color, height = 58) {
  return shape({ width: fixed(8), height: fixed(height), fill: color, borderRadius: 4 });
}

function numberedDot(no, color = C.blue, size = 56) {
  return panel(
    {
      width: fixed(size),
      height: fixed(size),
      fill: color,
      borderRadius: size / 2,
      align: "center",
      justify: "center",
    },
    [t(String(no), 20, "#FFFFFF", { width: fixed(size), alignment: "center", bold: true })],
  );
}

function claimLine(main, detail, color = C.blue, dark = false) {
  return row({ width: fill, height: hug, gap: 18, align: "start" }, [
    accentBar(color, 46),
    column({ width: fill, height: hug, gap: 5 }, [
      t(main, 27, dark ? C.darkText : C.ink, { bold: true }),
      t(detail, 20, dark ? C.darkMuted : C.muted),
    ]),
  ]);
}

function stepItem(no, title, detail, color = C.blue) {
  return column({ width: fill, height: hug, gap: 20, align: "start" }, [
    t(String(no).padStart(2, "0"), 22, color, { width: fixed(80), bold: true }),
    column({ width: fill, height: hug, gap: 10 }, [
      t(title, 34, C.ink, { bold: true }),
      rule({ width: fixed(120), stroke: color, weight: 5 }),
      t(detail, 24, C.muted, { width: wrap(430) }),
    ]),
  ]);
}

function sourceLine(name, detail, color) {
  return row({ width: fill, height: fixed(72), gap: 16, align: "center" }, [
    accentBar(color, 42),
    column({ width: fill, height: hug, gap: 4 }, [
      t(name, 25, C.ink, { bold: true }),
      t(detail, 19, C.muted),
    ]),
  ]);
}

function addManualAnalysisSlide(slide) {
  shell(
    slide,
    "manualna analiza",
    3,
    column({ width: fill, height: fill, gap: 58 }, [
      column({ width: fill, height: hug, gap: 18 }, [
        t("Jak rozwiązałbym ten incydent bez AI?", 68, C.ink, { bold: true, display: true }),
        t(
          "Mam correlationId. Chcę ustalić: co się stało, gdzie, dlaczego i komu przekazać naprawę.",
          31,
          C.muted,
          { width: wrap(1320) },
        ),
      ]),
      grid({ width: fill, height: hug, columns: [fr(1), fr(1), fr(1)], columnGap: 96 }, [
        stepItem(1, "Zbieram fakty", "logi, trace, metryki, kod i dane, które opisują symptom", C.blue),
        stepItem(2, "Łączę znaczenia", "system, proces, bounded context, zespół i handoff", C.green),
        stepItem(3, "Formułuję rezultat", "diagnoza, ograniczenia widoczności i materiał dla zespołu", C.red),
      ]),
      row({ width: fill, height: hug, gap: 18, align: "center" }, [
        rule({ width: fill, stroke: C.line, weight: 1 }),
        t("To jest plan pracy. Dopiero później sprawdzam, co da się zautomatyzować.", 25, C.ink, {
          width: fixed(880),
          bold: true,
          alignment: "center",
        }),
        rule({ width: fill, stroke: C.line, weight: 1 }),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "Ten slajd ma oczyścić narrację po wstępie. Zamiast pokazywać całą notatkę z analizy, pokazujemy sposób myślenia członka zespołu: mam correlationId, zbieram fakty, łączę je ze znaczeniem systemowym i dopiero na końcu formułuję rezultat, który może przejąć inny człowiek.",
  );
}

function addCorrelationSlide(slide) {
  const sourceItems = [
    ["Elasticsearch", "daty, komponenty, endpointy, stacktrace", C.blue],
    ["Dynatrace", "stan runtime, problemy i degradacje", C.green],
    ["GitLab", "implementacja, konfiguracja i repozytoria", C.violet],
    ["Database", "dane i model domenowy", C.orange],
    ["Operational context", "system, proces, zespół i handoff", C.red],
  ];

  shell(
    slide,
    "deterministyczna korelacja faktów",
    4,
    column({ width: fill, height: fill, gap: 46 }, [
      t("Najpierw korelujemy fakty deterministycznie.", 68, C.ink, { bold: true, display: true, width: wrap(1260) }),
      grid({ width: fill, height: hug, columns: [fr(0.72), fr(1.28), fr(0.82)], columnGap: 56, alignItems: "center" }, [
        column({ width: fill, height: hug, gap: 20 }, [
          t("wejście", 20, C.muted, { bold: true }),
          t("correlationId", 46, C.ink, { bold: true, mono: true }),
          rule({ width: fixed(260), stroke: C.blue, weight: 6 }),
          t("jeden techniczny ślad, z którego startuje cała analiza", 24, C.muted, { width: wrap(360) }),
        ]),
        column({ width: fill, height: hug, gap: 12 }, sourceItems.map(([name, detail, color]) => sourceLine(name, detail, color))),
        column({ width: fill, height: hug, gap: 20 }, [
          t("wyjście", 20, C.muted, { bold: true }),
          t("evidence package", 46, C.ink, { bold: true }),
          rule({ width: fixed(310), stroke: C.green, weight: 6 }),
          t("uporządkowany kontekst, który AI może interpretować zamiast zgadywać korelację od zera", 24, C.muted, {
            width: wrap(430),
          }),
        ]),
      ]),
      row({ width: fill, height: hug, gap: 18, align: "center" }, [
        rule({ width: fill, stroke: C.line, weight: 1 }),
        t("AI interpretuje kontekst. Nie zgaduje brakujących połączeń od zera.", 27, C.ink, {
          width: fixed(820),
          bold: true,
          alignment: "center",
        }),
        rule({ width: fill, stroke: C.line, weight: 1 }),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "Ten slajd zastępuje ciężki diagram blokowy. Ważne jest rozróżnienie: correlationId uruchamia deterministyczną korelację faktów, a AI dostaje już evidence package. To nie jest strategia 'wrzuć wszystko do modelu'.",
  );
}

function addOperationalContextSlide(slide) {
  shell(
    slide,
    "operational context",
    5,
    grid({ width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 90, alignItems: "center" }, [
      column({ width: fill, height: hug, gap: 28 }, [
        t("Operational context", 62, C.red, { bold: true, display: true }),
        t("zastępuje wiedzę z głowy.", 62, C.ink, { bold: true, display: true }),
        t("Pipeline miał fakty. Brakowało mapy znaczeń.", 31, C.muted, { width: wrap(700) }),
        rule({ width: fixed(180), stroke: C.blue, weight: 6 }),
        t(
          "To narzędzie powstało dopiero po manualnej analizie, gdy stało się widoczne, że korelacja technicznych faktów nie wystarcza bez wiedzy projektowej.",
          27,
          C.ink,
          { width: wrap(720), bold: true },
        ),
      ]),
      column({ width: fill, height: hug, gap: 16 }, [
        claimLine("System", "kanoniczny punkt odniesienia dla runtime, repozytoriów i zakresu analizy", C.blue),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Proces i bounded context", "jak techniczny ślad połączyć z obszarem funkcjonalnym", C.green),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Repozytorium", "gdzie agent powinien szukać kodu dla danego systemu", C.violet),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Zespół i handoff", "kto może przejąć naprawę albo dalszą analizę", C.orange),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Terminy", "język projektu potrzebny do poprawnej interpretacji faktów", C.red),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "Operational context nie był wejściem do manualnej analizy. Był wiedzą w głowie po kilku latach pracy z projektem. Automatyzacja ujawniła lukę: narzędzia mogły zebrać fakty, ale agent nie miał mapy systemu, procesu, zespołu i handoffu. Dlatego operational context stał się osobnym toolem.",
  );
}

function addPromptSlide(slide) {
  shell(
    slide,
    "prompt",
    7,
    column({ width: fill, height: fill, gap: 116 }, [
      column({ width: fill, height: hug, gap: 18 }, [
        t("Prompt to nie triki semantyczne.", 66, C.ink, { bold: true, display: true }),
        t("To polecenie specyficzne dla problemu.", 66, C.red, { bold: true, display: true }),
      ]),
      grid({ width: fill, height: hug, columns: [fr(1), fr(1), fr(1)], columnGap: 110 }, [
        stepItem(1, "Fakty", "wykryte środowisko\ndaty i komponenty\nstan runtime", C.blue),
        stepItem(2, "Powiązania", "bounded context\nsystem i repozytorium\nzespół i handoff", C.blue),
        stepItem(3, "Interpretacja", "kategoryzacja błędu\nwykryte luki\ndobór tools\nforma odpowiedzi", C.blue),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "W tym miejscu warto powiedzieć wprost: prompt nie jest zaklęciem. Jest kontraktem pracy dla problemu, który już rozumiemy. Dlatego opisuje fakty, powiązania i oczekiwaną interpretację.",
  );
}

function traceColumn(title, detail, color) {
  return column({ width: fill, height: hug, gap: 16 }, [
    accentBar(color, 64),
    t(title, 36, C.ink, { bold: true }),
    t(detail, 25, C.muted, { width: wrap(420) }),
  ]);
}

function addTraceSlide(slide) {
  shell(
    slide,
    "copilot trace",
    11,
    grid({ width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 86, alignItems: "center" }, [
      column({ width: fill, height: hug, gap: 28 }, [
        t("Po pętli zostaje ślad.", 63, C.ink, { bold: true, display: true }),
        t("Nie tylko diagnoza.", 63, C.red, { bold: true, display: true }),
        t("Wynik ze skilli jest końcem analizy. UI pokazuje też, jak Copilot do niego doszedł i ile ta droga kosztowała.", 30, C.muted, {
          width: wrap(760),
        }),
        rule({ width: fixed(180), stroke: C.blue, weight: 6 }),
        t("To user-facing feedback loop, nie ukryta telemetria backendu.", 27, C.ink, { width: wrap(690), bold: true }),
      ]),
      column({ width: fill, height: hug, gap: 18 }, [
        claimLine("Reasoning", "jak agent dzielił problem, wracał do luk i podejmował decyzje", C.violet),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Evidence", "które skille i tools realnie wniosły dane do analizy", C.orange),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        claimLine("Usage & feedback", "tokens, cache, koszt, czas, ocena jakości i braki widoczności", C.green),
        rule({ width: fill, stroke: C.line, weight: 1 }),
        row({ width: fill, height: hug, gap: 18, align: "center" }, [
          t("Ten ślad mówi, co ulepszać: prompt, skill, tool, input albo operational context.", 26, C.ink, {
            width: fill,
            bold: true,
          }),
        ]),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "Po analizie nie zostaje tylko finalny wynik. Operator widzi ślad: jak agent pracował, jakie skille i toole wykorzystał, jakie koszty i ograniczenia pojawiły się po drodze. To jest podstawa świadomego ulepszania narzędzia.",
  );
}

function layerRow(name, detail, color) {
  return row(
    {
      width: fill,
      height: fixed(88),
      gap: 22,
      align: "center",
      padding: { x: 22, y: 18 },
    },
    [
      accentBar(color, 48),
      column({ width: fixed(350), height: hug, gap: 4 }, [
        t(name, 29, C.darkText, { bold: true, mono: name.includes(".") }),
        t(detail, 18, C.darkMuted),
      ]),
    ],
  );
}

function addArchitectureSlide(slide) {
  shell(
    slide,
    "architecture",
    12,
    grid({ width: fill, height: fill, columns: [fr(0.88), fr(1.12)], columnGap: 84, alignItems: "center" }, [
      column({ width: fill, height: hug, gap: 26 }, [
        t("To nie jest jeden feature.", 61, C.darkText, { bold: true, display: true }),
        t("To platforma do analiz.", 61, C.green, { bold: true, display: true }),
        t("Dołączając, budujesz kolejne capability, nie zaczynasz od zera.", 31, C.darkMuted, { width: wrap(720) }),
        column({ width: fill, height: hug, gap: 16, padding: { top: 32 } }, [
          claimLine("Nowy feature", "własny prompt, skille, kontrakt wyniku i UI", C.green, true),
          claimLine("Nowy tool", "neutralna capability dostępna dla różnych agentów", C.violet, true),
          claimLine("Nowa integracja", "port, adapter i typowany kontrakt do zewnętrznego systemu", C.orange, true),
          claimLine("Nowy read model", "kolejna projekcja operational contextu dla LLM albo UI", C.blue, true),
        ]),
      ]),
      column({ width: fill, height: hug, gap: 8 }, [
        layerRow("features.*", "prompt · skille · result · UI", C.green),
        rule({ width: fill, stroke: "#3A3A3C", weight: 1 }),
        layerRow("aiplatform.copilot", "sesja · allowlista tools · hidden context · usage", C.blue),
        rule({ width: fill, stroke: "#3A3A3C", weight: 1 }),
        layerRow("agenttools.*", "opctx · gitlab · db · elastic", C.violet),
        rule({ width: fill, stroke: "#3A3A3C", weight: 1 }),
        layerRow("integrations.*", "Elastic · Dynatrace · GitLab · DB · operational context", C.orange),
        rule({ width: fill, stroke: "#3A3A3C", weight: 1 }),
        layerRow("shared + api", "evidence · usage · options · operator API", C.red),
        t("Zasada: zależności idą w dół. Platforma, tools i integracje nie importują feature'ów.", 24, C.darkText, {
          bold: true,
          width: wrap(880),
        }),
      ]),
    ]),
    true,
  );

  slide.speakerNotes.setText(
    "Ten slajd ma zachęcić współtwórców: incident analysis jest pierwszym feature'em, ale architektura jest ustawiona jako platforma. Można dodać nowy feature, tool, integrację albo read model, korzystając z istniejących warstw i bez mieszania granic.",
  );
}

function takeawayItem(no, textValue, color) {
  return row({ width: fill, height: hug, gap: 22, align: "center" }, [
    t(String(no), 30, color, { width: fixed(44), bold: true, alignment: "center" }),
    t(textValue, 37, C.ink, { bold: true }),
  ]);
}

function addTakeawaySlide(slide) {
  shell(
    slide,
    "takeaway",
    13,
    grid({ width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 86, alignItems: "center" }, [
      column({ width: fill, height: hug, gap: 20 }, [
        t("Nie zaczynaj od lepszego", 62, C.ink, { bold: true, display: true }),
        t("modelu, markdownu,", 62, C.ink, { bold: true, display: true }),
        t("tooli", 62, C.ink, { bold: true, display: true }),
        t("Agentowość to", 58, C.red, { bold: true, display: true, width: wrap(680) }),
        t("automatyzacja twoich", 58, C.red, { bold: true, display: true, width: wrap(720) }),
        t("kompetencji", 58, C.red, { bold: true, display: true, width: wrap(680) }),
      ]),
      column({ width: fill, height: hug, gap: 26 }, [
        takeawayItem(1, "Zacznij od manualnej analizy.", C.blue),
        takeawayItem(2, "Nazwij wiedzę, której pipeline nie ma.", C.blue),
        takeawayItem(3, "Zbierz deterministyczne fakty przed AI.", C.blue),
        takeawayItem(4, "Rozbij diagnozę na mniejsze decyzje.", C.green),
        takeawayItem(5, "Zdefiniuj celowy pipeline.", C.green),
        takeawayItem(6, "Mierz koszt, ślady pracy i feedback.", C.green),
      ]),
    ]),
  );

  slide.speakerNotes.setText(
    "Końcowy slajd ma zamknąć prezentację prostym przekazem: nie zaczynamy od modelu ani promptowych trików. Zaczynamy od własnej kompetencji, manualnego playbooka i pomiaru tego, jak agent wykonuje ten proces.",
  );
}

function replaceSlide(deck, index, compose) {
  deck.slides.getItem(index).delete();
  const slide = deck.slides.add();
  slide.moveTo(index);
  slide.setViewportSize(W, H);
  compose(slide);
}

async function writeBlob(blob, filePath) {
  const buffer = Buffer.from(await blob.arrayBuffer());
  await fs.writeFile(filePath, buffer);
}

async function clearReviewDir() {
  await fs.mkdir(reviewDir, { recursive: true });
}

function inspectLayout(layout) {
  const issues = [];
  const texts = [];

  for (const element of layout.elements ?? []) {
    const bbox = element.bbox;
    const name = element.name ?? element.textPreview ?? element.kind ?? "element";
    if (element.textPreview) {
      texts.push(element.textPreview);
    }
    if (bbox?.length === 4) {
      const [left, top, right, bottom] = bbox;
      const tolerance = 2;
      if (left < -tolerance || top < -tolerance || right > W + tolerance || bottom > H + tolerance) {
        issues.push(`${name} outside slide bounds: [${bbox.join(", ")}]`);
      }
    }
    if (element.textPreview && element.resolvedFontSize && element.resolvedFontSize < 14) {
      issues.push(`${name} text below 14 px: ${element.resolvedFontSize}`);
    }
  }

  return { texts, issues };
}

async function exportPreviews(deck, outputPptx) {
  await clearReviewDir();
  const summary = [];
  const issues = [];
  const previewPaths = [];

  for (let i = 0; i < deck.slides.count; i += 1) {
    const slide = deck.slides.getItem(i);
    const slideNo = i + 1;
    const pngPath = path.join(reviewDir, `slide-${String(slideNo).padStart(2, "0")}.png`);
    await writeBlob(await slide.export({ format: "png", scale: 1 }), pngPath);
    previewPaths.push(pngPath);

    const layout = JSON.parse(await (await slide.export({ format: "layout" })).text());
    await fs.writeFile(path.join(reviewDir, `slide-${String(slideNo).padStart(2, "0")}.layout.json`), JSON.stringify(layout, null, 2), "utf8");
    const inspection = inspectLayout(layout);
    summary.push({ slide: slideNo, texts: inspection.texts });
    issues.push(...inspection.issues.map((issue) => ({ slide: slideNo, issue })));
  }

  const report = {
    generatedAt: new Date().toISOString(),
    sourcePptx,
    outputPptx,
    slideCount: deck.slides.count,
    issueCount: issues.length,
    issues,
    previewPaths,
    summary,
  };
  await fs.writeFile(path.join(reviewDir, "review-report.json"), JSON.stringify(report, null, 2), "utf8");
  return report;
}

async function savePptx(pptx) {
  try {
    await pptx.save(improvedPptx);
    return improvedPptx;
  } catch (error) {
    if (error?.code !== "EBUSY") {
      throw error;
    }
    const fallback = path.join(outputDir, `incident-tracker-ai-tooling-concept-apple-style-improved-${Date.now()}.pptx`);
    await pptx.save(fallback);
    return fallback;
  }
}

async function renumberDuplicateSlideShapeIds(pptxPath) {
  const python = path.resolve(runtimeNodeHome, "..", "python", "python.exe");
  const script = String.raw`
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

src = Path(sys.argv[1])
tmp = src.with_suffix(src.suffix + ".tmp")
P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
ET.register_namespace("p", P_NS)
ET.register_namespace("a", A_NS)
ET.register_namespace("r", R_NS)

def fix_slide_ids(xml_bytes):
    root = ET.fromstring(xml_bytes)
    nodes = list(root.iter(f"{{{P_NS}}}cNvPr"))
    used = set()
    max_id = 0
    for node in nodes:
        try:
            max_id = max(max_id, int(node.attrib.get("id", "0")))
        except ValueError:
            pass
    changed = False
    for node in nodes:
        value = node.attrib.get("id")
        if value in used:
            max_id += 1
            node.set("id", str(max_id))
            changed = True
        else:
            used.add(value)
    if not changed:
        return xml_bytes
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)

with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename.startswith("ppt/slides/slide") and info.filename.endswith(".xml"):
            data = fix_slide_ids(data)
        zout.writestr(info, data)

tmp.replace(src)
`;
  await execFileAsync(python, ["-c", script, pptxPath], { cwd: deckRoot });
}

async function main() {
  const sourceBytes = await fs.readFile(sourcePptx);
  const deck = await PresentationFile.importPptx(sourceBytes);

  const replacements = [
    [2, "manual analysis", addManualAnalysisSlide],
    [3, "correlation", addCorrelationSlide],
    [4, "operational context", addOperationalContextSlide],
    [6, "prompt", addPromptSlide],
    [10, "trace", addTraceSlide],
    [11, "architecture", addArchitectureSlide],
    [12, "takeaway", addTakeawaySlide],
  ];

  for (const [index, , compose] of replacements) {
    replaceSlide(deck, index, compose);
  }

  const pptx = await PresentationFile.exportPptx(deck);
  const outputPptx = await savePptx(pptx);
  await renumberDuplicateSlideShapeIds(outputPptx);

  const verificationDeck = await PresentationFile.importPptx(await fs.readFile(outputPptx));
  const report = await exportPreviews(verificationDeck, outputPptx);
  console.log(JSON.stringify(report, null, 2));
}

await main();

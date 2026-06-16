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
const reviewDir = path.join(deckRoot, "review-flow-summary");
const sourcePptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-reviewed-loop.pptx");
const summaryPptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-flow-summary.pptx");
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

function topRail(section) {
  return row({ width: fill, height: hug, gap: 24, align: "center" }, [
    t("INCIDENT TRACKER", 18, C.muted, { width: fixed(190), bold: true }),
    rule({ width: fill, stroke: C.line, weight: 1 }),
    t(section.toUpperCase(), 18, C.muted, { width: fixed(320), bold: true, alignment: "right" }),
  ]);
}

function footer(slideNo) {
  return row({ width: fill, height: hug, align: "center", gap: 24 }, [
    t("Team Delivery Workspace · AI tooling philosophy", 18, C.muted),
    t(String(slideNo).padStart(2, "0"), 18, C.muted, {
      width: fixed(44),
      bold: true,
      alignment: "right",
    }),
  ]);
}

function shell(slide, section, slideNo, body) {
  slide.compose(
    layers({ width: fill, height: fill }, [
      shape({ width: fill, height: fill, fill: C.bg }),
      column({ width: fill, height: fill, padding: { x: 96, y: 70 }, gap: 30 }, [
        topRail(section),
        body,
        footer(slideNo),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
}

function divider() {
  return rule({ width: fill, stroke: C.line, weight: 1 });
}

function marker(color) {
  return shape({ width: fixed(10), height: fixed(42), fill: color, borderRadius: 5 });
}

function traceItem(title, detail, color) {
  return row({ width: fill, height: hug, gap: 20, align: "start" }, [
    marker(color),
    column({ width: fill, height: hug, gap: 5 }, [
      t(title, 28, C.ink, { bold: true }),
      t(detail, 20, C.muted),
    ]),
  ]);
}

function metricPill(label, value, color) {
  return panel(
    {
      width: fixed(214),
      height: fixed(92),
      fill: C.paper,
      borderRadius: 18,
      padding: { x: 20, y: 16 },
      border: { color: C.softLine, width: 1 },
    },
    column({ width: fill, height: hug, gap: 5 }, [
      t(label, 16, C.muted, { bold: true }),
      t(value, 27, color, { bold: true, display: true }),
    ]),
  );
}

function addCopilotFlowSummarySlide(slide) {
  const trace = [
    [
      "Kontrakt pracy",
      "model, reasoning effort, preparedPrompt i skille jako opis oczekiwanego wyniku",
      C.blue,
    ],
    [
      "Przebieg pipeline'u",
      "kroki deterministyczne, statusy, item count oraz evidence czytane i publikowane",
      C.green,
    ],
    [
      "Tok działania Copilota",
      "widok AI / Tools / Runtime / Usage: wiadomości, requesty tooli, statusy i payload",
      C.violet,
    ],
    [
      "Tool evidence",
      "wyniki narzędzi z GitLaba, DB i operational contextu powiązane z toolCallId",
      C.orange,
    ],
    [
      "Koszt i pojemność",
      "tokens, cache, context, API calls, duration oraz estymacja credits / dollars",
      C.blue,
    ],
    [
      "Feedback jakości",
      "ocena użyteczności tooli, braków danych i sugestii usprawnień; także w follow-up chat",
      C.green,
    ],
  ];

  shell(
    slide,
    "copilot trace",
    11,
    grid(
      { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 76, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 28 }, [
          t("Po pętli zostaje ślad.", 70, C.ink, { bold: true, display: true }),
          t("Nie tylko diagnoza.", 70, C.red, { bold: true, display: true }),
          t(
            "Wynik ze skilli jest końcem analizy. UI pokazuje też, jak Copilot do niego doszedł i ile ta droga kosztowała.",
            29,
            C.muted,
            { width: wrap(690) },
          ),
          row({ width: fill, height: hug, gap: 14, padding: { top: 26 } }, [
            metricPill("AI", "reasoning", C.violet),
            metricPill("Tools", "evidence", C.orange),
            metricPill("Usage", "koszt", C.green),
          ]),
          t(
            "To jest user-facing feedback loop, nie ukryta telemetryka backendu.",
            25,
            C.ink,
            { bold: true, width: wrap(680) },
          ),
        ]),
        panel(
          {
            width: fill,
            height: hug,
            fill: C.paper,
            borderRadius: 20,
            padding: { x: 36, y: 32 },
            border: { color: C.softLine, width: 1 },
          },
          column(
            { width: fill, height: hug, gap: 15 },
            trace.flatMap(([title, detail, color], idx) => [
              traceItem(title, detail, color),
              idx === trace.length - 1 ? null : divider(),
            ]).filter(Boolean),
          ),
        ),
      ],
    ),
  );

  slide.speakerNotes.setText(
    "Ten slajd podsumowuje to, co obecnie front dostaje w AnalysisJobStateSnapshot poza samym rezultatem skillowym. Widoczny jest model i reasoning effort, przygotowany prompt, kroki pipeline'u, deterministyczne evidence, tool evidence, zdarzenia Copilota pogrupowane jako AI, Tools, Runtime i Usage, koszt/tokeny oraz feedback jakości tooli. Ważne: frontend nie ma osobnego pola 'skill usage' i nie pokazuje prywatnego chain-of-thought. Skille są kontraktem pracy zapisanym w promptach i rezultacie, a UI pokazuje ślad wykonania tego kontraktu.",
  );
}

function insertSlide(deck, index, compose) {
  const slide = deck.slides.add();
  slide.moveTo(index);
  slide.setViewportSize(W, H);
  compose(slide);
}

function normalizeText(value) {
  return String(value ?? "").replace(/\s+/g, " ").trim();
}

function updateFooterNumbers(deck) {
  for (let i = 0; i < deck.slides.count; i += 1) {
    const expected = String(i + 1).padStart(2, "0");
    const slide = deck.slides.getItem(i);
    for (const shapeItem of slide.shapes.items ?? []) {
      const frame = shapeItem.frame;
      const current = normalizeText(shapeItem.text?.toString?.());
      if (!shapeItem.text || !/^\d{1,2}$/.test(current)) {
        continue;
      }
      if (!frame || frame.left < 1600 || frame.top < 900) {
        continue;
      }
      shapeItem.text.replace(current, expected);
    }
  }
}

function applyTextReplacements(deck) {
  const replacements = [
    ["automatyzacje", "automatyzację"],
    ["chemy", "chcemy"],
    ["Jakbym", "Jak bym"],
    ["endpoint’y", "endpointy"],
    ["Dynarace", "Dynatrace"],
    ["rest api", "REST API"],
    ["Rest api", "REST API"],
    ["jak zamykają logikę", "gdy zamykają logikę"],
    ["jak zamykają", "gdy zamykają"],
    ["Instrukcji", "Instrukcja"],
  ];

  for (const slide of deck.slides.items) {
    for (const shapeItem of slide.shapes.items ?? []) {
      if (!shapeItem.text) continue;
      for (const [from, to] of replacements) {
        shapeItem.text.replace(from, to);
      }
    }
  }
}

async function writeBlob(blob, filePath) {
  const buffer = Buffer.from(await blob.arrayBuffer());
  await fs.writeFile(filePath, buffer);
}

async function clearReviewDir() {
  await fs.mkdir(reviewDir, { recursive: true });
  const files = await fs.readdir(reviewDir);
  await Promise.all(
    files
      .filter((file) => /^slide-\d+\.png$/.test(file) || file === "text-summary.json")
      .map((file) => fs.unlink(path.join(reviewDir, file))),
  );
}

function inspectLayout(layout, slideNo) {
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

  return { slide: slideNo, texts, issues };
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
    const inspection = inspectLayout(layout, slideNo);
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
  await fs.writeFile(path.join(reviewDir, "text-summary.json"), JSON.stringify(report, null, 2), "utf8");
  return report;
}

async function savePptx(pptx) {
  try {
    await pptx.save(summaryPptx);
    return summaryPptx;
  } catch (error) {
    if (error?.code !== "EBUSY") {
      throw error;
    }
    const fallback = path.join(
      outputDir,
      `incident-tracker-ai-tooling-concept-apple-style-flow-summary-${Date.now()}.pptx`,
    );
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

async function patchSlideTextInOpenXml(pptxPath) {
  const python = path.resolve(runtimeNodeHome, "..", "python", "python.exe");
  const script = String.raw`
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

src = Path(sys.argv[1])
tmp = src.with_suffix(src.suffix + ".text.tmp")
P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
ET.register_namespace("p", P_NS)
ET.register_namespace("a", A_NS)
ET.register_namespace("r", R_NS)
NS = {"p": P_NS, "a": A_NS}

REPLACEMENTS = [
    ("automatyzacje", "automatyzację"),
    ("chemy", "chcemy"),
    ("Jakbym", "Jak bym"),
    ("endpoint’y", "endpointy"),
    ("Dynarace", "Dynatrace"),
    ("rest api", "REST API"),
    ("Rest api", "REST API"),
    ("jak zamykają logikę", "gdy zamykają logikę"),
    ("jak zamykają", "gdy zamykają"),
    ("Instrukcji", "Instrukcja"),
]

def read_slide_size(zip_file):
    try:
        root = ET.fromstring(zip_file.read("ppt/presentation.xml"))
    except KeyError:
        return 12192000, 6858000
    size = root.find(".//p:sldSz", NS)
    if size is None:
        return 12192000, 6858000
    return int(size.attrib.get("cx", "12192000")), int(size.attrib.get("cy", "6858000"))

def replace_text_nodes(root):
    changed = False
    for node in root.iter(f"{{{A_NS}}}t"):
        if not node.text:
            continue
        value = node.text
        for source, target in REPLACEMENTS:
            value = value.replace(source, target)
        if value != node.text:
            node.text = value
            changed = True
    return changed

def shape_text(shape):
    return "".join(node.text or "" for node in shape.iter(f"{{{A_NS}}}t")).strip()

def set_shape_text(shape, value):
    nodes = list(shape.iter(f"{{{A_NS}}}t"))
    if not nodes:
        return False
    nodes[0].text = value
    for node in nodes[1:]:
        node.text = ""
    return True

def patch_specific_shapes(root):
    changed = False
    for shape in root.findall(".//p:sp", NS):
        text_value = " ".join(shape_text(shape).split())
        if text_value.startswith("Toole nie powinny być adapterem do REST API") and "zamykają" in text_value:
            changed = set_shape_text(
                shape,
                "Toole nie powinny być adapterem do REST API. Najlepiej działają, gdy zamykają logikę deterministyczną swojego kontekstu.",
            ) or changed
    return changed

def shape_offset(shape):
    xfrm = shape.find(".//p:spPr/a:xfrm", NS)
    if xfrm is None:
        return None
    off = xfrm.find("a:off", NS)
    if off is None:
        return None
    try:
        return int(off.attrib.get("x", "0")), int(off.attrib.get("y", "0"))
    except ValueError:
        return None

def patch_footer_number(root, slide_no, slide_width, slide_height):
    expected = f"{slide_no:02d}"
    changed = False
    for shape in root.findall(".//p:sp", NS):
        text_value = shape_text(shape)
        if not re.fullmatch(r"\d{1,2}", text_value):
            continue
        offset = shape_offset(shape)
        if offset is None:
            continue
        x, y = offset
        if x < slide_width * 0.80 or y < slide_height * 0.80:
            continue
        nodes = list(shape.iter(f"{{{A_NS}}}t"))
        if not nodes:
            continue
        nodes[0].text = expected
        for node in nodes[1:]:
            node.text = ""
        changed = True
    return changed

def patch_slide(xml_bytes, slide_no, slide_width, slide_height):
    root = ET.fromstring(xml_bytes)
    changed = replace_text_nodes(root)
    changed = patch_specific_shapes(root) or changed
    changed = patch_footer_number(root, slide_no, slide_width, slide_height) or changed
    if not changed:
        return xml_bytes
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)

with zipfile.ZipFile(src, "r") as zin:
    slide_width, slide_height = read_slide_size(zin)
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            match = re.fullmatch(r"ppt/slides/slide(\d+)\.xml", info.filename)
            if match:
                data = patch_slide(data, int(match.group(1)), slide_width, slide_height)
            zout.writestr(info, data)

tmp.replace(src)
`;
  await execFileAsync(python, ["-c", script, pptxPath], { cwd: deckRoot });
}

async function main() {
  const sourceBytes = await fs.readFile(sourcePptx);
  const deck = await PresentationFile.importPptx(sourceBytes);

  insertSlide(deck, 10, addCopilotFlowSummarySlide);
  updateFooterNumbers(deck);
  applyTextReplacements(deck);

  const pptx = await PresentationFile.exportPptx(deck);
  const outputPptx = await savePptx(pptx);
  await renumberDuplicateSlideShapeIds(outputPptx);
  await patchSlideTextInOpenXml(outputPptx);

  const verificationDeck = await PresentationFile.importPptx(await fs.readFile(outputPptx));
  const report = await exportPreviews(verificationDeck, outputPptx);
  console.log(JSON.stringify(report, null, 2));
}

await main();

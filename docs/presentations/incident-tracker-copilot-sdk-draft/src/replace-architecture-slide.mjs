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
const reviewDir = path.join(deckRoot, "review-architecture");
const sourcePptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-flow-summary.pptx");
const architecturePptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-architecture.pptx");
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
    t("Incident Tracker · AI tooling philosophy", 18, dark ? C.darkMuted : C.muted),
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

function layerBand(name, detail, examples, color) {
  return row(
    {
      width: fill,
      height: fixed(104),
      gap: 24,
      align: "center",
      padding: { x: 26, y: 20 },
      fill: C.darkPanel,
      borderRadius: 18,
      border: { color: "#3A3A3C", width: 1 },
    },
    [
      shape({ width: fixed(10), height: fixed(58), fill: color, borderRadius: 5 }),
      column({ width: fixed(350), height: hug, gap: 6 }, [
        t(name, 29, C.darkText, { bold: true, mono: name.includes(".") }),
        t(detail, 18, C.darkMuted),
      ]),
      t(examples, 20, C.darkText, { bold: true }),
    ],
  );
}

function contributionItem(title, detail, color) {
  return row({ width: fill, height: hug, gap: 16, align: "start" }, [
    shape({ width: fixed(8), height: fixed(36), fill: color, borderRadius: 4 }),
    column({ width: fill, height: hug, gap: 4 }, [
      t(title, 24, C.darkText, { bold: true }),
      t(detail, 18, C.darkMuted),
    ]),
  ]);
}

function addArchitectureSlide(slide) {
  const layersData = [
    [
      "features.*",
      "dedykowane analizy",
      "request · prompt · skille · policy · result · UI",
      C.green,
    ],
    [
      "aiplatform.copilot",
      "runtime AI",
      "sesja · allowlista tools · hidden context · usage · activity · budget",
      C.blue,
    ],
    [
      "agenttools.*",
      "neutralne capabilities",
      "opctx_* · gitlab_* · db_* · elastic_*",
      C.violet,
    ],
    [
      "integrations.*",
      "adaptery do systemów",
      "Elasticsearch · Dynatrace · GitLab · Database · Operational Context",
      C.orange,
    ],
    [
      "shared + api",
      "kontrakty i powierzchnie operatora",
      "evidence · AI usage · activity trace · options · operator API",
      C.red,
    ],
  ];

  shell(
    slide,
    "architecture",
    12,
    grid(
      { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 70, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 26 }, [
          t("Dołączając, nie zaczynasz od zera.", 66, C.darkText, { bold: true, display: true }),
          t("Budujesz na platformie.", 66, C.green, { bold: true, display: true }),
          t(
            "Incident analysis jest pierwszym feature'em. Kolejne analizy mogą używać platformy, tools, integracji i operational context bez importowania incident flow.",
            29,
            C.darkMuted,
            { width: wrap(710) },
          ),
          column({ width: fill, height: hug, gap: 16, padding: { top: 34 } }, [
            contributionItem("Nowy feature", "własny prompt, skille, kontrakt wyniku i UI", C.green),
            contributionItem("Nowy tool", "neutralny wrapper nad adapterem, dostępny dla różnych agentów", C.violet),
            contributionItem("Nowa integracja", "port, adapter i typowany kontrakt capability bez zależności od feature'u", C.orange),
            contributionItem("Nowy read model", "kolejny sposób projekcji operational contextu dla LLM i FE", C.blue),
          ]),
        ]),
        column({ width: fill, height: hug, gap: 11 }, [
          ...layersData.flatMap(([name, detail, examples, color], idx) => [
            layerBand(name, detail, examples, color),
            idx === layersData.length - 1
              ? null
              : t("↓", 20, "#636366", { width: fill, alignment: "center", bold: true }),
          ]).filter(Boolean),
          t(
            "Guardrail: zależności idą w dół. Platforma, tools i integracje nie importują feature'ów.",
            23,
            C.darkText,
            { bold: true, width: wrap(900) },
          ),
        ]),
      ],
    ),
    true,
  );

  slide.speakerNotes.setText(
    "Ten slajd ma zachęcić przyszłych współtwórców. Kod jest już podzielony na miejsca odpowiedzialności: features dostarczają konkretny use case, prompt, skille, policy i kontrakt wyniku; aiplatform.copilot daje runtime sesji, allowlistę tools, hidden context, usage, activity trace i budget; agenttools wystawiają neutralne capabilities nad adapterami; integrations utrzymują porty i adaptery do systemów zewnętrznych; shared oraz api dają neutralne kontrakty i operator-facing powierzchnie. Najważniejsza zasada: incident analysis jest pierwszym feature'em, nie generycznym core. Drugi feature, np. flow explorer albo natural-language data diagnostics, powinien korzystać z tych warstw bez importowania features.incidentanalysis.",
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
    await pptx.save(architecturePptx);
    return architecturePptx;
  } catch (error) {
    if (error?.code !== "EBUSY") {
      throw error;
    }
    const fallback = path.join(
      outputDir,
      `incident-tracker-ai-tooling-concept-apple-style-architecture-${Date.now()}.pptx`,
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

async function main() {
  const sourceBytes = await fs.readFile(sourcePptx);
  const deck = await PresentationFile.importPptx(sourceBytes);

  replaceSlide(deck, 11, addArchitectureSlide);

  const pptx = await PresentationFile.exportPptx(deck);
  const outputPptx = await savePptx(pptx);
  await renumberDuplicateSlideShapeIds(outputPptx);

  const verificationDeck = await PresentationFile.importPptx(await fs.readFile(outputPptx));
  const report = await exportPreviews(verificationDeck, outputPptx);
  console.log(JSON.stringify(report, null, 2));
}

await main();

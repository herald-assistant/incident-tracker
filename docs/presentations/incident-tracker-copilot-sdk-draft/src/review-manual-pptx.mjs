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
const reviewDir = path.join(deckRoot, "review-reviewed");
const sourcePptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style.pptx");
const reviewedPptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-reviewed.pptx");
const fallbackReviewedPptx = path.join(outputDir, "incident-tracker-ai-tooling-concept-apple-style-reviewed-loop.pptx");
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
  red: "#FF0A0A",
  blue: "#0071E3",
  green: "#34C759",
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
    t("INCIDENT TRACKER", 18, dark ? C.darkMuted : C.muted, {
      width: fixed(190),
      bold: true,
    }),
    rule({ width: fill, stroke: dark ? "#B8B8BE" : C.line, weight: 1 }),
    t(section.toUpperCase(), 18, dark ? C.darkMuted : C.muted, {
      width: fixed(260),
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
      column({ width: fill, height: fill, padding: { x: 96, y: 70 }, gap: 30 }, [
        topRail(section, dark),
        body,
        footer(slideNo, dark),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
}

function divider(dark = false) {
  return rule({ width: fill, stroke: dark ? "#66666A" : C.line, weight: 1 });
}

function openItem(title, detail, color = C.blue, dark = false) {
  return row({ width: fill, height: hug, gap: 18, align: "start" }, [
    shape({ width: fixed(8), height: fixed(42), fill: color, borderRadius: 4 }),
    column({ width: fill, height: hug, gap: 5 }, [
      t(title, 27, dark ? C.darkText : C.ink, { bold: true }),
      t(detail, 20, dark ? C.darkMuted : C.muted),
    ]),
  ]);
}

function numberedLabel(no, color = C.blue) {
  return panel(
    {
      width: fixed(52),
      height: fixed(52),
      fill: color,
      borderRadius: "rounded-full",
      align: "center",
      justify: "center",
    },
    t(String(no), 20, C.paper, { width: hug, bold: true, alignment: "center" }),
  );
}

function replaceSlide(deck, index, compose) {
  deck.slides.getItem(index).delete();
  const slide = deck.slides.add();
  slide.moveTo(index);
  slide.setViewportSize(W, H);
  compose(slide);
}

function addOperationalContextSlide(slide) {
  const concepts = [
    ["System", "kanoniczny punkt odniesienia dla runtime, repozytoriów i zakresu"],
    ["Repozytorium", "gdzie agent ma szukać kodu dla danego systemu"],
    ["Domena / bounded context", "jak techniczny ślad połączyć z obszarem funkcjonalnym"],
    ["Zespół", "kto może przejąć naprawę albo dalszą analizę"],
    ["Terminy", "język projektu potrzebny do poprawnej interpretacji faktów"],
  ];

  shell(
    slide,
    "operational context",
    5,
    column({ width: fill, height: fill, gap: 32 }, [
      column({ width: fill, height: hug, gap: 8 }, [
        t("Operational context", 72, C.red, { bold: true, display: true }),
        t("wypełnia lukę korelacji.", 72, C.ink, { bold: true, display: true }),
        t("To wiedza z głowy zamieniona w tool dostępny dla agenta.", 28, C.muted, {
          width: wrap(1180),
        }),
      ]),
      grid(
        { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 76, alignItems: "center" },
        [
          column({ width: fill, height: hug, gap: 26 }, [
            t("Pipeline miał fakty.", 44, C.ink, { bold: true, display: true }),
            t(
              "Nie miał mapy projektu, która pozwala osadzić je w systemie, procesie, zespole i handoffie.",
              30,
              C.muted,
            ),
            t(
              "Operational context powstał dopiero po manualnej analizie, gdy ta luka stała się widoczna.",
              26,
              C.ink,
              { bold: true },
            ),
          ]),
          column(
            { width: fill, height: hug, gap: 16 },
            concepts.flatMap(([name, detail], idx) => [
              openItem(name, detail, idx < 2 ? C.blue : C.green),
              idx === concepts.length - 1 ? null : divider(),
            ]).filter(Boolean),
          ),
        ],
      ),
    ]),
  );
  slide.speakerNotes.setText(
    "Operational context nie był dostępny w analizie ludzkiej jako osobny ekran albo dokument. Był wiedzą w głowie osoby, która zna system od lat. W automatyzacji ujawniła się luka: pipeline potrafił zebrać fakty, ale nie potrafił ich osadzić w realnym kontekście systemu, repozytorium, domeny, zespołu i handoffu.",
  );
}

function addFourPillarsSlide(slide) {
  const pillars = [
    ["PROMPT", "precyzyjnie zapisuje cel, kontekst i oczekiwany rezultat"],
    ["TOOLS", "dostarczają kontrolowane źródła faktów, nie losowy zrzut danych"],
    ["SKILLS", "prowadzą agenta przez ekspercki playbook analizy"],
    ["METRICS", "pokazują koszt, ślady pracy, jakość użycia tooli i feedback"],
  ];

  shell(
    slide,
    "prompt",
    6,
    grid(
      { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 72, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 34 }, [
          column({ width: fill, height: hug, gap: 12 }, [
            t("AI nie zwalnia z myślenia", 64, C.red, { bold: true, display: true }),
            t("AI egzekwuje ekspercki styl pracy", 62, C.darkText, { bold: true, display: true }),
          ]),
          row({ width: fill, height: hug, gap: 26, align: "end" }, [
            t("4", 180, C.blue, { width: fixed(190), bold: true, display: true }),
            t("filary\nincident trackera", 34, C.darkText, { width: fixed(300), bold: true }),
          ]),
        ]),
        column(
          { width: fill, height: hug, gap: 14 },
          pillars.flatMap(([name, detail], idx) => [
            openItem(name, detail, C.blue, true),
            idx === pillars.length - 1 ? null : divider(true),
          ]).filter(Boolean),
        ),
      ],
    ),
    true,
  );
  slide.speakerNotes.setText(
    "Ten slajd zbiera filozofię narzędzia: prompt nie jest zaklęciem, tools nie są zrzutem wszystkich danych, skille prowadzą pracę agenta, a metryki i feedback pozwalają ulepszać narzędzie po każdym uruchomieniu.",
  );
}

function addPromptPlaybookSlide(slide) {
  const columns = [
    ["01", "Fakty", ["wykryte środowisko", "daty i komponenty", "implementacje klas", "stan runtime"]],
    ["02", "Powiązania", ["bounded context", "system i repozytorium", "zespół", "ownership i handoff"]],
    ["03", "Interpretacja", ["kategoryzacja błędu", "wykryte luki", "dobór tooli", "aktualizacja hipotez", "forma odpowiedzi"]],
  ];

  shell(
    slide,
    "prompt",
    7,
    column({ width: fill, height: fill, gap: 70 }, [
      column({ width: fill, height: hug, gap: 12 }, [
        t("Prompt to nie triki semantyczne.", 68, C.ink, { bold: true, display: true }),
        t("Prompt to playbook wykonawczy agenta.", 68, C.ink, { bold: true, display: true }),
      ]),
      grid(
        { width: fill, height: fill, columns: [fr(1), fr(1), fr(1)], columnGap: 96, alignItems: "start" },
        columns.map(([no, title, items], idx) =>
          column({ width: fill, height: hug, gap: 20 }, [
            t(no, 23, idx === 2 ? C.green : C.blue, { bold: true }),
            t(title, 50, C.ink, { bold: true, display: true }),
            rule({ width: fixed(132), stroke: idx === 2 ? C.green : C.blue, weight: 6 }),
            t(items.join("\n"), 25, C.ink),
          ]),
        ),
      ),
    ]),
  );
  slide.speakerNotes.setText(
    "Profesjonalne promptowanie nie polega na szukaniu magicznych zwrotów. Polega na zapisaniu tego, co ekspert robi normalnie: oddziela fakty od powiązań, następnie prowadzi interpretację i pilnuje formy wyniku.",
  );
}

function addAgenticLoopSlide(slide) {
  const steps = [
    ["hipoteza", "co może wyjaśniać symptom"],
    ["luka", "czego jeszcze nie wiemy"],
    ["tool", "które źródło odpowie"],
    ["nowy fakt", "co zmienia evidence"],
    ["decyzja", "kończ albo wracaj"],
  ];
  const loopNode = (no, title, detail, color = C.blue, width = fixed(250)) =>
    column({ width, height: hug, gap: 12, align: "center" }, [
      numberedLabel(no, color),
      t(title, 29, C.ink, { width, bold: true, alignment: "center" }),
      t(detail, 20, C.muted, { width, alignment: "center" }),
    ]);

  shell(
    slide,
    "agentic loop",
    10,
    grid(
      { width: fill, height: fill, columns: [fr(0.75), fr(1.25)], columnGap: 64, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 24 }, [
          t("Agent pracuje w pętli.", 64, C.ink, { bold: true, display: true }),
          t("Aż dowiezie cel z promptu i skilli.", 58, C.ink, { bold: true, display: true }),
          t(
            "Orkiestrator nie zamienia analizy w jeden strzał. Pilnuje cyklu: hipoteza, luka, tool, nowy fakt, decyzja.",
            30,
            C.muted,
          ),
        ]),
        panel(
          { width: fill, height: hug, fill: C.paper, borderRadius: 18, padding: { x: 34, y: 30 } },
          column({ width: fill, height: hug, gap: 22 }, [
            row({ width: fill, height: hug, gap: 18, align: "center" }, [
              loopNode(1, steps[0][0], steps[0][1]),
              t("→", 42, C.line, { width: fixed(44), bold: true, alignment: "center" }),
              loopNode(2, steps[1][0], steps[1][1]),
              t("→", 42, C.line, { width: fixed(44), bold: true, alignment: "center" }),
              loopNode(3, steps[2][0], steps[2][1]),
            ]),
            t("↓", 42, C.line, { width: fill, bold: true, alignment: "right" }),
            row({ width: fill, height: hug, gap: 18, align: "center" }, [
              loopNode(5, steps[4][0], steps[4][1], C.green),
              t("←", 42, C.line, { width: fixed(44), bold: true, alignment: "center" }),
              loopNode(4, steps[3][0], steps[3][1]),
              t("↺", 44, C.blue, { width: fill, bold: true, alignment: "right" }),
            ]),
            divider(),
            row({ width: fill, height: hug, gap: 18, align: "center" }, [
              panel(
                {
                  width: fixed(78),
                  height: fixed(44),
                  fill: C.green,
                  borderRadius: "rounded-full",
                  align: "center",
                  justify: "center",
                },
                t("STOP", 18, C.paper, { width: hug, bold: true, alignment: "center" }),
              ),
              t(
                "pętla kończy się dopiero, gdy wynik spełnia kryteria zapisane w promptcie i skillach",
                26,
                C.ink,
                { bold: true },
              ),
            ]),
          ]),
        ),
      ],
    ),
  );
  slide.speakerNotes.setText(
    "Podejście agentowe nie jest jedną liniową iteracją. Skill orkiestratora prowadzi pętlę: agent formułuje hipotezę, nazywa lukę, dobiera tool, wraca z nowym faktem i podejmuje decyzję, czy wynik spełnia cel z promptu i skilli. Jeżeli nie, pętla wraca do kolejnej hipotezy albo kolejnego braku.",
  );
}

function addTakeawaySlide(slide) {
  const items = [
    "Zacznij od manualnej analizy.",
    "Nazwij wiedzę, której pipeline nie ma.",
    "Zbierz deterministyczne fakty przed AI.",
    "Rozbij diagnozę na mniejsze decyzje.",
    "Daj Copilotowi celowe toole.",
    "Mierz koszt, ślady pracy i feedback.",
  ];

  shell(
    slide,
    "takeaway",
    14,
    grid(
      { width: fill, height: fill, columns: [fr(0.88), fr(1.12)], columnGap: 70, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 28 }, [
          t("Nie zaczynaj od lepszego modelu.", 70, C.ink, { bold: true, display: true }),
          t("Zacznij od lepszego sposobu pracy.", 31, C.muted),
          column({ width: fill, height: hug, gap: 10, padding: { top: 170 } }, [
            t("Agentowość", 58, C.blue, { bold: true, display: true }),
            t("to kontrolowana pętla pracy: hipoteza, luka, tool, nowy fakt, decyzja.", 30, C.ink, {
              bold: true,
            }),
          ]),
        ]),
        column(
          { width: fill, height: hug, gap: 22 },
          items.map((item, idx) =>
            row({ width: fill, height: hug, gap: 20, align: "center" }, [
              numberedLabel(idx + 1, idx < 3 ? C.blue : C.green),
              t(item, 29, C.ink, { bold: true }),
            ]),
          ),
        ),
      ],
    ),
  );
  slide.speakerNotes.setText(
    "Zamknięcie prezentacji: skuteczne narzędzie AI zaczyna się od dobrego sposobu pracy. Model i SDK są ważne, ale dopiero na końcu procesu: po manualnej analizie, nazwaniu luk, zebraniu faktów, rozbiciu diagnozy na kroki i zbudowaniu pętli pomiaru.",
  );
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

async function exportReviewedPreviews(deck, outputPptx) {
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
    reviewedPptx: outputPptx,
    slideCount: deck.slides.count,
    issueCount: issues.length,
    issues,
    previewPaths,
    summary,
  };
  await fs.writeFile(path.join(reviewDir, "text-summary.json"), JSON.stringify(report, null, 2), "utf8");
  return report;
}

async function savePptxWithFallback(pptx) {
  try {
    await pptx.save(reviewedPptx);
    return reviewedPptx;
  } catch (error) {
    if (error?.code !== "EBUSY") {
      throw error;
    }
    await pptx.save(fallbackReviewedPptx);
    return fallbackReviewedPptx;
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

  replaceSlide(deck, 4, addOperationalContextSlide);
  replaceSlide(deck, 5, addFourPillarsSlide);
  replaceSlide(deck, 6, addPromptPlaybookSlide);
  replaceSlide(deck, 9, addAgenticLoopSlide);
  replaceSlide(deck, 13, addTakeawaySlide);
  applyTextReplacements(deck);

  const pptx = await PresentationFile.exportPptx(deck);
  const outputPptx = await savePptxWithFallback(pptx);
  await renumberDuplicateSlideShapeIds(outputPptx);
  const report = await exportReviewedPreviews(deck, outputPptx);
  console.log(JSON.stringify(report, null, 2));
}

await main();

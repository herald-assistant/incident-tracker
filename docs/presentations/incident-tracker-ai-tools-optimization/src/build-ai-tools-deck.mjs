import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";
import fs from "node:fs/promises";
import path from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const deckRoot = path.resolve(__dirname, "..");
const outputDir = path.join(deckRoot, "output");
const reviewDir = path.join(deckRoot, "review");
const reportsDir = path.join(deckRoot, "reports");

const runtimeNodeHome =
  process.env.CODEX_PRIMARY_RUNTIME_NODE_HOME ??
  "C:/Users/mknie/.cache/codex-runtimes/codex-primary-runtime/dependencies/node";
const runtimeRequire = createRequire(path.join(runtimeNodeHome, "package.json"));
const artifactToolPath = runtimeRequire.resolve("@oai/artifact-tool");
const artifactTool = await import(pathToFileURL(artifactToolPath).href);

const {
  Presentation,
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
  auto,
} = artifactTool;

const W = 1920;
const H = 1080;

const C = {
  bg: "#F5F5F7",
  paper: "#FFFFFF",
  ink: "#1D1D1F",
  muted: "#6E6E73",
  quiet: "#A1A1A6",
  line: "#D2D2D7",
  dark: "#111113",
  dark2: "#1D1D1F",
  darkText: "#F5F5F7",
  darkMuted: "#A1A1A6",
  blue: "#0071E3",
  blueSoft: "#E7F1FF",
  green: "#34C759",
  greenSoft: "#E8F8ED",
  orange: "#FF9F0A",
  orangeSoft: "#FFF2D9",
  red: "#FF3B30",
  redSoft: "#FFE6E3",
  violet: "#AF52DE",
  violetSoft: "#F4E8FB",
};

const font = {
  display: "Aptos Display",
  body: "Aptos",
  mono: "Cascadia Mono",
};

const slides = [
  {
    section: "01",
    title: "INCIDENT TRACKER — AI TOOLS",
    subtitle: "Jak optymalizować narzędzia dla agentów AI",
    note:
      "Ten deck jest drugim krokiem po prezentacji o filozofii agentów. Tym razem zawężamy temat do jednego filaru: AI Tools. Kluczowa teza: dobry tool zmniejsza koszt, czas i ryzyko pracy agenta.",
  },
  {
    section: "02",
    title: "Tool zaczyna się tam, gdzie zaczyna się logika deterministyczna.",
    note:
      "Najważniejsza zasada projektowa: jeżeli coś można policzyć, zwalidować, przefiltrować, ograniczyć, skorelować albo przetestować w zwykłym kodzie, powinno znaleźć się w toolu, a nie w promptcie.",
  },
  {
    section: "03",
    title: "AI interpretuje. Tool gwarantuje.",
    note:
      "Rozdzielamy odpowiedzialność. AI powinno stawiać hipotezy, interpretować fakty, wybierać kolejny krok i składać odpowiedź. Tool ma gwarantować fakty, zakres, walidację, limity, uprawnienia i powtarzalny wynik.",
  },
  {
    section: "04",
    title: "Stateful tool: AI nie zgaduje tego, co system wie na pewno.",
    note:
      "Stateful tool przenosi twardy kontekst sesji poza model. Aplikacja ustala sessionId, użytkownika, uprawnienia, środowisko, zakres, polityki i limity. Model w wywołaniu podaje tylko argumenty interpretacyjne, takie jak reason, filtr albo hipoteza.",
  },
  {
    section: "05",
    title: "Opis pomaga modelowi. Implementacja chroni system.",
    note:
      "Dobry opis uczy model, kiedy tool ma sens. Ale bezpieczeństwo, kontrola kosztu i powtarzalność wynikają z implementacji: walidacji, limitów, scope'u, struktury wyniku, logów i testów.",
  },
  {
    section: "06",
    title: "Nie optymalizujemy tooli dla elegancji kodu.",
    note:
      "Zamykamy przesłaniem: zły tool kosztuje więcej niż zły prompt, bo przerzuca pracę na AI. Projektujemy narzędzia tak, żeby agent robił mniej niepotrzebnej, kosztownej i ryzykownej pracy.",
  },
];

function style(size, color = C.ink, extra = {}) {
  const typeface = extra.mono ? font.mono : extra.display ? font.display : font.body;
  return {
    typeface,
    fontFamily: typeface,
    fontSize: size,
    color,
    bold: extra.bold ?? false,
    alignment: extra.alignment ?? "left",
  };
}

function txt(value, size, color = C.ink, extra = {}) {
  return text(value, {
    name: extra.name,
    width: extra.width ?? fill,
    height: extra.height ?? hug,
    style: style(size, color, extra),
  });
}

function eyebrow(value, color = C.muted) {
  return text(value.toUpperCase(), {
    width: hug,
    height: hug,
    style: style(16, color, { bold: true }),
  });
}

function foot(slide, dark = false) {
  return row({ name: "footer", width: fill, height: hug, align: "center", gap: 18 }, [
    text("Incident Tracker · AI Tools", {
      width: fill,
      height: hug,
      style: style(17, dark ? C.darkMuted : C.muted),
    }),
    text(String(slide.index + 1).padStart(2, "0"), {
      width: fixed(42),
      height: hug,
      style: style(17, dark ? C.darkMuted : C.muted, { bold: true, alignment: "right" }),
    }),
  ]);
}

function shell(slide, meta, body, options = {}) {
  const dark = options.dark ?? false;
  const bg = dark ? C.dark : C.bg;
  const ink = dark ? C.darkText : C.ink;
  const muted = dark ? C.darkMuted : C.muted;
  const accent = options.accent ?? C.blue;
  slide.compose(
    layers({ name: `slide-${meta.section}`, width: fill, height: fill }, [
      shape({ name: "background", width: fill, height: fill, fill: bg }),
      column({ name: "safe-area", width: fill, height: fill, padding: { x: 86, y: 58 }, gap: 20 }, [
        row({ name: "top-rail", width: fill, height: hug, align: "center", gap: 22 }, [
          eyebrow("INCIDENT TRACKER", dark ? C.darkMuted : C.muted),
          rule({ width: fill, stroke: dark ? "#343437" : C.line, weight: 1.5 }),
          eyebrow("AI TOOLS", accent),
        ]),
        text(meta.title, {
          name: "slide-title",
          width: wrap(options.titleWidth ?? 1600),
          height: fixed(options.titleHeight ?? 168),
          style: style(options.titleSize ?? 58, ink, { bold: true, display: true }),
        }),
        shape({ name: "accent-line", width: fixed(180), height: fixed(6), fill: accent, borderRadius: 3 }),
        body({ ink, muted, accent, dark }),
        foot(slide, dark),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
  slide.speakerNotes.setText(meta.note);
}

function softPanel({ title, detail, accent = C.blue, fillColor = C.paper, dark = false }) {
  return panel(
    {
      fill: fillColor,
      borderRadius: 8,
      padding: { x: 28, y: 24 },
      width: fill,
      height: hug,
      line: { style: "solid", fill: dark ? "#343437" : C.line, width: 1 },
    },
    column({ width: fill, height: hug, gap: 10 }, [
      row({ width: fill, height: hug, gap: 14, align: "center" }, [
        shape({ width: fixed(10), height: fixed(34), fill: accent, borderRadius: 4 }),
        text(title, { width: fill, height: hug, style: style(27, dark ? C.darkText : C.ink, { bold: true }) }),
      ]),
      text(detail, { width: fill, height: hug, style: style(20, dark ? C.darkMuted : C.muted) }),
    ]),
  );
}

function pill(value, accent, width = fixed(190), dark = false) {
  return panel(
    {
      fill: dark ? "#202024" : C.paper,
      borderRadius: "rounded-full",
      padding: { x: 18, y: 12 },
      width,
      height: fixed(54),
      align: "center",
      line: { style: "solid", fill: dark ? "#3A3A3D" : C.line, width: 1 },
    },
    text(value, {
      width: fill,
      height: hug,
      style: style(19, accent, { bold: true, alignment: "center" }),
    }),
  );
}

function arrow(color = C.line) {
  return text("→", {
    width: fixed(54),
    height: hug,
    style: style(35, color, { bold: true, alignment: "center" }),
  });
}

function addCover(presentation) {
  const slide = presentation.slides.add();
  const meta = slides[0];
  slide.compose(
    layers({ name: "cover", width: fill, height: fill }, [
      shape({ width: fill, height: fill, fill: C.dark }),
      shape({ width: fixed(920), height: fill, fill: C.dark2 }),
      shape({ width: fixed(18), height: fill, fill: C.blue }),
      column({ width: fill, height: fill, padding: { x: 88, y: 72 }, gap: 42 }, [
        row({ width: fill, height: hug, align: "center", gap: 22 }, [
          eyebrow("INCIDENT TRACKER", C.darkMuted),
          rule({ width: fill, stroke: "#343437", weight: 1.5 }),
          eyebrow("AI TOOLS", C.blue),
        ]),
        grid(
          {
            width: fill,
            height: fill,
            columns: [fr(0.96), fr(1.04)],
            columnGap: 78,
            alignItems: "center",
          },
          [
            column({ width: fill, height: hug, gap: 28 }, [
              text("AI Tools", {
                width: fill,
                height: hug,
                style: style(108, C.darkText, { bold: true, display: true }),
              }),
              text("Jak optymalizować narzędzia dla agentów AI", {
                width: fill,
                height: hug,
                style: style(34, C.darkMuted),
              }),
            ]),
            column({ width: fill, height: hug, gap: 36 }, [
              text("Dobry tool obniża koszt, czas i ryzyko działania agenta.", {
                width: wrap(760),
                height: hug,
                style: style(56, C.darkText, { bold: true, display: true }),
              }),
              row({ width: fill, height: hug, gap: 14, align: "center" }, [
                pill("tańszy", C.green, fixed(150), true),
                pill("szybszy", C.blue, fixed(155), true),
                pill("bezpieczniejszy", C.orange, fixed(245), true),
                pill("audytowalny", C.violet, fixed(215), true),
              ]),
            ]),
          ],
        ),
        foot(slide, true),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
  slide.speakerNotes.setText(meta.note);
}

function addDeterministicBoundary(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slides[1],
    () =>
      grid(
        {
          width: fill,
          height: fill,
          columns: [fr(0.82), fr(1.18)],
          columnGap: 70,
          alignItems: "center",
        },
        [
          column({ width: fill, height: hug, gap: 28 }, [
            text("Jeśli zwykły kod może to zrobić, prompt nie powinien za to płacić.", {
              width: fill,
              height: hug,
              style: style(46, C.ink, { bold: true, display: true }),
            }),
            text("Model nie jest tańszą wersją walidatora, filtra ani agregatora.", {
              width: wrap(620),
              height: hug,
              style: style(27, C.muted),
            }),
          ]),
          column({ width: fill, height: fixed(310), gap: 14 }, [
            row({ width: fill, height: hug, gap: 14, align: "center" }, [
              pill("policz", C.blue, fixed(145)),
              pill("zwaliduj", C.green, fixed(170)),
              pill("przefiltruj", C.orange, fixed(205)),
            ]),
            row({ width: fill, height: hug, gap: 14, align: "center" }, [
              pill("ogranicz", C.red, fixed(178)),
              pill("skoreluj", C.violet, fixed(170)),
              pill("przetestuj", C.blue, fixed(190)),
            ]),
            panel(
              {
                fill: C.paper,
                borderRadius: 8,
                padding: { x: 32, y: 28 },
                width: fill,
                height: hug,
                line: { style: "solid", fill: C.line, width: 1 },
              },
              row({ width: fill, height: hug, gap: 28, align: "center" }, [
                text("AI", { width: fixed(110), height: hug, style: style(38, C.blue, { bold: true, display: true }) }),
                text("interpretuje wynik pracy toola, zamiast wykonywać deterministyczną robotę tokenami.", {
                  width: fill,
                  height: hug,
                  style: style(29, C.ink, { bold: true }),
                }),
              ]),
            ),
          ]),
        ],
      ),
    { accent: C.blue, titleSize: 56, titleHeight: 190 },
  );
}

function addResponsibilitySplit(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slides[2],
    () =>
      grid(
        { width: fill, height: fill, columns: [fr(1), auto, fr(1)], columnGap: 38, alignItems: "center" },
        [
          column({ width: fill, height: fixed(620), gap: 12 }, [
            text("AI", { width: fill, height: hug, style: style(54, C.blue, { bold: true, display: true }) }),
            softPanel({ title: "Hipoteza", detail: "co może tłumaczyć obserwowany problem", accent: C.blue, fillColor: C.blueSoft }),
            softPanel({ title: "Interpretacja", detail: "jak połączyć fakty w spójną historię", accent: C.blue }),
            softPanel({ title: "Kolejny krok", detail: "który brak widoczności warto uzupełnić", accent: C.blue }),
            softPanel({ title: "Synteza", detail: "jak oddać wynik człowiekowi", accent: C.blue }),
          ]),
          arrow(C.line),
          column({ width: fill, height: fixed(620), gap: 12 }, [
            text("TOOL", { width: fill, height: hug, style: style(54, C.green, { bold: true, display: true }) }),
            softPanel({ title: "Fakty", detail: "pewne dane z kontrolowanego źródła", accent: C.green, fillColor: C.greenSoft }),
            softPanel({ title: "Zakres i uprawnienia", detail: "hard scope sesji, a nie sugestia modelu", accent: C.green }),
            softPanel({ title: "Walidacja i limity", detail: "czas, rozmiar, koszt, format wejścia", accent: C.green }),
            softPanel({ title: "Powtarzalny wynik", detail: "struktura, błędy, logi i testy", accent: C.green }),
          ]),
        ],
      ),
    { accent: C.green, titleSize: 60 },
  );
}

function addStatefulTool(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slides[3],
    () =>
      column({ width: fill, height: fill, gap: 34, justify: "center" }, [
        row({ width: fill, height: hug, gap: 18, align: "center" }, [
          softPanel({
            title: "Start sesji",
            detail: "sessionId, użytkownik, uprawnienia, środowisko, scope, kontekst systemu, polityki, limity, allowlista tooli, stabilne identyfikatory",
            accent: C.blue,
            fillColor: C.blueSoft,
          }),
          arrow(C.line),
          softPanel({
            title: "Tool server",
            detail: "odtwarza twardy kontekst sesji i pilnuje granic wykonania",
            accent: C.green,
            fillColor: C.greenSoft,
          }),
          arrow(C.line),
          softPanel({
            title: "Tool call",
            detail: "model podaje tylko reason, filtr, wzorzec, hipotezę albo poziom szczegółowości",
            accent: C.violet,
            fillColor: C.violetSoft,
          }),
        ]),
        panel(
          {
            fill: C.dark,
            borderRadius: 8,
            padding: { x: 42, y: 34 },
            width: fill,
            height: hug,
          },
          grid(
            { width: fill, height: hug, columns: [fr(0.88), fr(1.12)], columnGap: 42, alignItems: "center" },
            [
              text("Agent może pomylić argument interpretacyjny.", {
                width: fill,
                height: hug,
                style: style(38, C.darkText, { bold: true, display: true }),
              }),
              text("Nie powinien móc zmienić użytkownika, środowiska, zakresu analizy, polityk ani limitów.", {
                width: fill,
                height: hug,
                style: style(31, C.darkMuted),
              }),
            ],
          ),
        ),
      ]),
    { accent: C.violet, titleSize: 54, titleHeight: 190 },
  );
}

function addGoodToolTraits(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slides[4],
    () =>
      grid(
        { width: fill, height: fill, columns: [fr(0.78), fr(1.22)], columnGap: 62, alignItems: "center" },
        [
          column({ width: fill, height: hug, gap: 20 }, [
            text("Opis jest zaproszeniem.", {
              width: fill,
              height: hug,
              style: style(45, C.ink, { bold: true, display: true }),
            }),
            text("Kontrakt, limity i logi są pasami bezpieczeństwa.", {
              width: wrap(620),
              height: hug,
              style: style(29, C.muted),
            }),
          ]),
          grid(
            { width: fill, height: hug, columns: [fr(1), fr(1), fr(1)], columnGap: 18, alignItems: "stretch" },
            [
              panel(
                { fill: C.paper, borderRadius: 8, padding: { x: 26, y: 28 }, width: fill, height: fixed(430), line: { style: "solid", fill: C.line, width: 1 } },
                column({ width: fill, height: fill, gap: 20 }, [
                  text("Kontrakt", { width: fill, height: hug, style: style(33, C.blue, { bold: true, display: true }) }),
                  txt("konkretny cel", 22, C.ink, { bold: true }),
                  txt("kiedy użyć", 22, C.ink, { bold: true }),
                  txt("minimalne parametry", 22, C.ink, { bold: true }),
                  txt("bez zgadywania scope'u", 22, C.ink, { bold: true }),
                ]),
              ),
              panel(
                { fill: C.paper, borderRadius: 8, padding: { x: 26, y: 28 }, width: fill, height: fixed(430), line: { style: "solid", fill: C.line, width: 1 } },
                column({ width: fill, height: fill, gap: 20 }, [
                  text("Kontrola", { width: fill, height: hug, style: style(33, C.green, { bold: true, display: true }) }),
                  txt("walidacja inputu", 22, C.ink, { bold: true }),
                  txt("safe defaults", 22, C.ink, { bold: true }),
                  txt("limity czasu i rozmiaru", 22, C.ink, { bold: true }),
                  txt("uprawnienia i scope", 22, C.ink, { bold: true }),
                ]),
              ),
              panel(
                { fill: C.paper, borderRadius: 8, padding: { x: 26, y: 28 }, width: fill, height: fixed(430), line: { style: "solid", fill: C.line, width: 1 } },
                column({ width: fill, height: fill, gap: 20 }, [
                  text("Ślad", { width: fill, height: hug, style: style(33, C.violet, { bold: true, display: true }) }),
                  txt("structured result", 22, C.ink, { bold: true }),
                  txt("czytelne błędy", 22, C.ink, { bold: true }),
                  txt("request/result log", 22, C.ink, { bold: true }),
                  txt("sessionId, toolCallId, reason", 22, C.ink, { bold: true }),
                  txt("testy kontraktu", 22, C.ink, { bold: true }),
                ]),
              ),
            ],
          ),
        ],
      ),
    { accent: C.orange, titleSize: 58 },
  );
}

function addRisks(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slides[5],
    () =>
      grid(
        { width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 70, alignItems: "center" },
        [
          column({ width: fill, height: hug, gap: 28 }, [
            text("Optymalizujemy je, żeby AI robiło mniej niepotrzebnej pracy.", {
              width: fill,
              height: hug,
              style: style(48, C.darkText, { bold: true, display: true }),
            }),
            text("To jest praktyczne podejście do promptowania: najpierw projektujemy system pracy, dopiero potem prosimy model o wykonanie.", {
              width: wrap(700),
              height: hug,
              style: style(27, C.darkMuted),
            }),
          ]),
          grid(
            { width: fill, height: hug, columns: [fr(1), fr(1)], columnGap: 18, rowGap: 18 },
            [
              softPanel({ title: "Koszt", detail: "model filtruje i agreguje tokenami", accent: C.orange, fillColor: "#202024", dark: true }),
              softPanel({ title: "Czas", detail: "agent robi kolejne niepotrzebne kroki", accent: C.blue, fillColor: "#202024", dark: true }),
              softPanel({ title: "Powtarzalność", detail: "ten sam problem daje różne ścieżki pracy", accent: C.violet, fillColor: "#202024", dark: true }),
              softPanel({ title: "Bezpieczeństwo", detail: "scope i uprawnienia są podatne na sugestię", accent: C.red, fillColor: "#202024", dark: true }),
              softPanel({ title: "Audytowalność", detail: "nie widać, skąd wzięła się odpowiedź", accent: C.green, fillColor: "#202024", dark: true }),
              softPanel({ title: "Zaufanie", detail: "użytkownik nie wie, kiedy wynik jest solidny", accent: C.blue, fillColor: "#202024", dark: true }),
            ],
          ),
        ],
      ),
    { dark: true, accent: C.red, titleSize: 58, titleHeight: 150 },
  );
}

function addSlides(presentation) {
  addCover(presentation);
  addDeterministicBoundary(presentation);
  addResponsibilitySplit(presentation);
  addStatefulTool(presentation);
  addGoodToolTraits(presentation);
  addRisks(presentation);
}

async function writeBlob(blob, destination) {
  await fs.writeFile(destination, new Uint8Array(await blob.arrayBuffer()));
}

async function ensureDirs() {
  await fs.mkdir(outputDir, { recursive: true });
  await fs.mkdir(reviewDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });
}

async function exportSlidePreviews(presentation, prefix) {
  const paths = [];
  for (let i = 0; i < presentation.slides.count; i += 1) {
    const slide = presentation.slides.getItem(i);
    const pngPath = path.join(reviewDir, `${prefix}-slide-${String(i + 1).padStart(2, "0")}.png`);
    const png = await slide.export({ format: "png", scale: 1 });
    await writeBlob(png, pngPath);
    paths.push(pngPath);
  }
  return paths;
}

async function exportLayouts(presentation) {
  const reports = [];
  for (let i = 0; i < presentation.slides.count; i += 1) {
    const slide = presentation.slides.getItem(i);
    const layoutPath = path.join(reportsDir, `slide-${String(i + 1).padStart(2, "0")}.layout.json`);
    const layoutBlob = await slide.export({ format: "layout" });
    const layoutText = await layoutBlob.text();
    await fs.writeFile(layoutPath, layoutText, "utf8");
    const layout = JSON.parse(layoutText);
    reports.push(inspectLayout(layout, i + 1));
  }
  return reports;
}

function inspectLayout(layout, slideNo) {
  const issues = [];
  for (const element of layout.elements ?? []) {
    const bbox = element.bbox;
    if (!bbox || bbox.length !== 4) {
      continue;
    }
    const [left, top, right, bottom] = bbox;
    const name = element.name ?? element.textPreview ?? element.kind ?? "element";
    const tolerance = 3;
    if (left < -tolerance || top < -tolerance || right > W + tolerance || bottom > H + tolerance) {
      issues.push(`${name} outside slide bounds: [${bbox.join(", ")}]`);
    }
    if (element.textPreview && element.resolvedFontSize && element.resolvedFontSize < 14) {
      issues.push(`${name} text below 14 px: ${element.resolvedFontSize}`);
    }
  }
  return { slide: slideNo, issues };
}

async function writePlanningArtifacts() {
  const sourceNotes = [
    "Source notes",
    "",
    "User-provided material: C:/Users/mknie/Desktop/prompt ppt.txt.",
    "Claims used: AI Tools should reduce cost, time, risk, improve repeatability and auditability; tools should own deterministic logic, validation, limits, permissions, session context, structured results, logging and tests.",
    "Style source: previous local Incident Tracker presentation used only as visual inspiration; no external assets, logos or screenshots were used.",
  ].join("\n");

  const slidePlan = [
    "Slide plan",
    "",
    "Mode: create. Six-slide conceptual deck, no implementation example.",
    "Palette: neutral Apple-like background #F5F5F7 / dark #111113 with blue #0071E3 as primary accent, plus green/orange/red/violet for semantic contrast.",
    "Fonts: Aptos Display for headings, Aptos for body, Cascadia Mono only if technical labels are needed.",
    "Slide 1: title and premise.",
    "Slide 2: deterministic boundary principle.",
    "Slide 3: responsibility split between AI and tool.",
    "Slide 4: stateful tool and hard session context.",
    "Slide 5: traits of a good tool.",
    "Slide 6: consequences of a bad tool and takeaway.",
  ].join("\n");

  await fs.writeFile(path.join(reportsDir, "source-notes.txt"), sourceNotes, "utf8");
  await fs.writeFile(path.join(reportsDir, "slide-plan.txt"), slidePlan, "utf8");
}

async function savePptx(presentation) {
  const pptxPath = path.join(outputDir, "incident-tracker-ai-tools-optimization.pptx");
  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(pptxPath);
  return pptxPath;
}

async function build() {
  await ensureDirs();
  await writePlanningArtifacts();

  const presentation = Presentation.create({
    slideSize: { width: W, height: H },
  });

  addSlides(presentation);

  const sourcePreviews = await exportSlidePreviews(presentation, "source");
  const layoutReports = await exportLayouts(presentation);
  const pptxPath = await savePptx(presentation);

  const imported = await PresentationFile.importPptx(await fs.readFile(pptxPath));
  const pptxPreviews = await exportSlidePreviews(imported, "pptx");
  const montagePath = path.join(reviewDir, "pptx-montage.webp");
  await writeBlob(await imported.export({ format: "webp", montage: true, scale: 1 }), montagePath);

  const notesPath = path.join(reportsDir, "speaker-notes.txt");
  await fs.writeFile(
    notesPath,
    slides.map((slide, index) => `${index + 1}. ${slide.title}\n${slide.note}\n`).join("\n"),
    "utf8",
  );

  const issues = layoutReports.flatMap((report) =>
    report.issues.map((issue) => ({ slide: report.slide, issue })),
  );
  const report = {
    generatedAt: new Date().toISOString(),
    slideCount: presentation.slides.count,
    importedSlideCount: imported.slides.count,
    pptxPath,
    notesPath,
    sourcePreviews,
    pptxPreviews,
    montagePath,
    layoutIssueCount: issues.length,
    issues,
  };
  const reportPath = path.join(reportsDir, "qa-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await fs.writeFile(
    path.join(reportsDir, "visual-qa.txt"),
    [
      "Visual QA",
      "",
      `Slides rendered: ${presentation.slides.count}`,
      `PPTX imported slide count: ${imported.slides.count}`,
      `Layout issue count: ${issues.length}`,
      "Manual image inspection is performed after script execution using rendered PNG files.",
    ].join("\n"),
    "utf8",
  );

  console.log(JSON.stringify(report, null, 2));
}

build().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

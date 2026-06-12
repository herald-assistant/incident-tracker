import { createRequire } from "node:module";
import { fileURLToPath, pathToFileURL } from "node:url";
import fs from "node:fs/promises";
import path from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const deckRoot = path.resolve(__dirname, "..");
const outputDir = path.join(deckRoot, "output");
const previewDir = path.join(deckRoot, "previews");
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
  grow,
  fr,
  auto,
} = artifactTool;

const W = 1920;
const H = 1080;

const C = {
  bg: "#F7F4EE",
  paper: "#FFFDF7",
  ink: "#17202A",
  muted: "#5B6470",
  faint: "#E5DED2",
  line: "#CFC5B6",
  teal: "#0F766E",
  tealSoft: "#DCEFEA",
  amber: "#D97706",
  amberSoft: "#FDECC8",
  blue: "#2563EB",
  blueSoft: "#DCEAFE",
  red: "#B42318",
  redSoft: "#F7D6D0",
  green: "#15803D",
  greenSoft: "#DFF4E7",
  violet: "#6D28D9",
  violetSoft: "#E9DFFB",
  graphite: "#2B3138",
  graphiteSoft: "#ECEFF1",
};

const font = {
  display: "Aptos Display",
  body: "Aptos",
  mono: "Cascadia Mono",
};

const slides = [
  {
    section: "opening",
    title: "Nie dajemy modelowi wszystkiego. Dajemy mu sposób pracy.",
    note:
      "Otwieramy od głównej filozofii: incident tracker nie jest pudełkiem, do którego wrzucamy wszystkie dane. To narzędzie, które prowadzi Copilota przez zaprojektowany sposób diagnozowania incydentu.",
  },
  {
    section: "philosophy",
    title: "LLM nie zastępuje metody diagnozy",
    note:
      "Ten slajd ma zdjąć magiczne oczekiwanie wobec AI. Model jest dobry w interpretacji i syntezie, ale potrzebuje prowadzenia: co najpierw sprawdzić, kiedy doczytać kontekst, kiedy przerwać eksplorację i jak sformułować wynik.",
  },
  {
    section: "professional prompting",
    title: "Profesjonalne promptowanie to spisana metoda pracy",
    note:
      "To odpowiada na pytanie: jak promptują profesjonaliści? Nie szukają zaklęć. Najpierw robią zwykłą analizę systemową, a potem zapisują ją jako cel, kontekst, kolejność pracy, ograniczenia i kryteria dobrego wyniku dla AI.",
  },
  {
    section: "human analysis",
    title: "Manualna analiza zaczynała się od traceId",
    note:
      "Tutaj pokazujemy punkt wyjścia: postawiłem się w sytuacji członka zespołu wytwórczego, który ma traceId i chce sam rozwiązać problem. Patrzy w logi, metryki, kod i dane, ale część korelacji robi dzięki wiedzy o projekcie, którą ma w głowie po latach pracy.",
  },
  {
    section: "product idea",
    title: "Incident tracker jest przewodnikiem po analizie",
    note:
      "Mówimy o produkcie i sposobie pracy. Narzędzie zaczyna od sygnału incydentu, samo przygotowuje kontekst i dopiero potem uruchamia AI, które pracuje według skilli oraz dostępnych tooli.",
  },
  {
    section: "pipeline",
    title: "Najpierw fakty, potem interpretacja AI",
    note:
      "Ważna rzecz dla odbiorców: główny pipeline analizy jest deterministyczny. Dopiero na końcu przechodzimy do pipeline'u AI, który korzysta ze skilli, zebranych faktów i kontrolowanych narzędzi do doczytania braków.",
  },
  {
    section: "ai tools",
    title: "AI tools są źródłami kontekstu, nie zrzutem danych",
    note:
      "Tutaj opisujemy poziom szczegółowości, którego chcemy: logi z Elasticsearch, metryki z Dynatrace, kod z GitLaba, dane z DB i operational context jako osobne, celowe źródła kontekstu dostępne dla Copilota.",
  },
  {
    section: "operational context",
    title: "Operational context to wiedza z głowy zamieniona w tool",
    note:
      "Operational context nie był dostępny jako narzędzie podczas manualnej analizy. Powstał po niej, gdy zobaczyłem lukę: pipeline miał fakty, ale nie miał tej nieformalnej wiedzy o systemie, procesach, ownershipie i handoffie, którą człowiek wnosi z doświadczenia.",
  },
  {
    section: "skills",
    title: "Skille są playbookiem pracy Copilota",
    note:
      "Ten slajd odpowiada na pytanie, co robią konkretne skille. Pokazujemy nazwy, bo są częścią produktu, ale każde wyjaśnienie zostaje jednozdaniowe i konceptualne.",
  },
  {
    section: "tool use",
    title: "Tool ma odpowiedzieć na konkretne pytanie analityczne",
    note:
      "To jest praktyczna zasada: tool nie jest po to, żeby model eksplorował bez końca. Każde źródło kontekstu ma pytanie, na które odpowiada. Dzięki temu koszty, szum i ryzyko halucynacji spadają.",
  },
  {
    section: "result",
    title: "Wynik ma być użyteczny dla operatora",
    note:
      "Zamiast mówić o formacie odpowiedzi, mówimy o tym, co operator musi dostać: co się prawdopodobnie stało, jaki proces lub system jest dotknięty, jak to rozumieć funkcjonalnie, co przekazać technicznie i czego nie widzimy.",
  },
  {
    section: "learning loop",
    title: "UI i metryki zamykają pętlę uczenia narzędzia",
    note:
      "UI nie jest dodatkiem. Pokazuje prompt, zebrane evidence, aktywność AI, użycie tooli, koszt i feedback. To pozwala poprawiać narzędzie iteracyjnie, zamiast oceniać tylko finalną odpowiedź.",
  },
  {
    section: "takeaway",
    title: "Najpierw projektujemy sposób pracy, potem narzędzie AI",
    note:
      "Zamykamy checklistą filozofii: plan eksperta, deterministyczne fakty, celowe toole, skille, użyteczny wynik i pętla feedbacku. To jest wzorzec do przeniesienia na inne narzędzia AI.",
  },
];

function textStyle(size, color = C.ink, extra = {}) {
  return {
    fontFamily: extra.mono ? font.mono : extra.display ? font.display : font.body,
    fontSize: size,
    color,
    bold: extra.bold ?? false,
    italic: extra.italic ?? false,
    alignment: extra.alignment ?? "left",
  };
}

function label(value, color = C.muted, width = hug) {
  return text(value.toUpperCase(), {
    width,
    height: hug,
    style: textStyle(18, color, { bold: true, alignment: width === hug ? "left" : "right" }),
  });
}

function strongText(value, size = 44, color = C.ink, width = fill, name) {
  return text(value, {
    name,
    width,
    height: hug,
    style: textStyle(size, color, { bold: true, display: true }),
  });
}

function bodyText(value, size = 28, color = C.muted, width = fill, name) {
  return text(value, {
    name,
    width,
    height: hug,
    style: textStyle(size, color),
  });
}

function footnote(value, color = C.muted, width = fill) {
  return text(value, {
    width,
    height: hug,
    style: textStyle(18, color),
  });
}

function slideMeta(section) {
  const meta = slides.find((candidate) => candidate.section === section);
  if (!meta) {
    throw new Error(`Unknown slide section: ${section}`);
  }
  return meta;
}

function chip(value, fillColor, color = C.ink, width = fixed(Math.min(240, Math.max(86, value.length * 8 + 34)))) {
  return panel(
    {
      fill: fillColor,
      borderRadius: "rounded-full",
      padding: { x: 14, y: 8 },
      width: hug,
      height: hug,
      align: "center",
    },
    text(value, {
      width,
      height: hug,
      style: textStyle(17, color, { bold: true, alignment: "center" }),
    }),
  );
}

function conceptCard(title, detail, fillColor, accent = C.teal, width = fill, name) {
  return panel(
    {
      name,
      fill: fillColor,
      borderRadius: 14,
      padding: { x: 24, y: 18 },
      width,
      height: hug,
    },
    row({ width: fill, height: hug, gap: 16, align: "center" }, [
      shape({ width: fixed(10), height: fixed(50), fill: accent, borderRadius: 4 }),
      column({ width: fill, height: hug, gap: 6 }, [
        text(title, { width: fill, height: hug, style: textStyle(27, C.ink, { bold: true }) }),
        text(detail, { width: fill, height: hug, style: textStyle(20, C.muted) }),
      ]),
    ]),
  );
}

function slimCard(title, detail, fillColor, accent) {
  return panel(
    { fill: fillColor, borderRadius: 14, padding: { x: 22, y: 16 }, width: fill, height: hug },
    column({ width: fill, height: hug, gap: 6 }, [
      row({ width: fill, height: hug, gap: 12, align: "center" }, [
        shape({ width: fixed(10), height: fixed(28), fill: accent, borderRadius: 4 }),
        text(title, { width: fill, height: hug, style: textStyle(25, C.ink, { bold: true }) }),
      ]),
      text(detail, { width: fill, height: hug, style: textStyle(19, C.muted) }),
    ]),
  );
}

function arrow(color = C.line) {
  return text("->", {
    width: fixed(52),
    height: hug,
    style: textStyle(30, color, { bold: true, alignment: "center" }),
  });
}

function shell(slide, meta, body, options = {}) {
  const accent = options.accent ?? C.teal;
  slide.compose(
    layers({ name: `slide-${meta.section}`, width: fill, height: fill }, [
      shape({ name: "background", width: fill, height: fill, fill: options.bg ?? C.bg }),
      column({ name: "safe-area", width: fill, height: fill, padding: { x: 88, y: 58 }, gap: 22 }, [
        row({ name: "top-rail", width: fill, height: hug, align: "center", gap: 24 }, [
          label("incident tracker // ai tooling", accent),
          rule({ width: fill, stroke: C.line, weight: 2 }),
          label(meta.section, C.muted, fixed(250)),
        ]),
        text(meta.title, {
          name: "slide-title",
          width: wrap(options.titleWidth ?? 1500),
          height: fixed(options.titleHeight ?? 138),
          style: textStyle(options.titleSize ?? 54, C.ink, { bold: true, display: true }),
        }),
        rule({ name: "title-accent", width: fixed(210), stroke: accent, weight: 6 }),
        body,
        row({ name: "footer", width: fill, height: hug, align: "center", gap: 20 }, [
          footnote("Draft v0.5  |  filozofia narzędzia i koncepty", C.muted, fill),
          text(String(slide.index + 1).padStart(2, "0"), {
            width: fixed(44),
            height: hug,
            style: textStyle(18, C.muted, { bold: true, alignment: "right" }),
          }),
        ]),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
  slide.speakerNotes.setText(meta.note);
}

function addCover(presentation) {
  const slide = presentation.slides.add();
  const meta = slides[0];
  slide.compose(
    layers({ name: "cover", width: fill, height: fill }, [
      shape({ width: fill, height: fill, fill: C.bg }),
      shape({ width: fixed(850), height: fill, fill: C.ink }),
      shape({ width: fixed(24), height: fill, fill: C.teal }),
      column({ width: fill, height: fill, padding: { x: 88, y: 72 }, gap: 42 }, [
        row({ width: fill, height: hug, gap: 20, align: "center" }, [
          text("INCIDENT TRACKER", {
            width: fixed(360),
            height: hug,
            style: textStyle(22, C.paper, { bold: true }),
          }),
          rule({ width: fill, stroke: C.line, weight: 2 }),
          chip("AI tools", C.blueSoft, C.blue, fixed(90)),
          chip("Copilot", C.greenSoft, C.green, fixed(90)),
        ]),
        grid(
          {
            width: fill,
            height: fill,
            columns: [fr(0.96), fr(1.04)],
            columnGap: 76,
            alignItems: "center",
          },
          [
            column({ width: fill, height: hug, gap: 26 }, [
              text("Nie dajemy modelowi wszystkiego.\nDajemy mu sposób pracy.", {
                name: "cover-title",
                width: fill,
                height: hug,
                style: textStyle(76, C.paper, { bold: true, display: true }),
              }),
              text(
                "Draft prezentacji o filozofii incident trackera: jak zaprojektować narzędzie AI, które prowadzi analizę zamiast liczyć na przypadkową odpowiedź modelu.",
                {
                  name: "cover-subtitle",
                  width: wrap(700),
                  height: hug,
                  style: textStyle(28, "#DFE7E5"),
                },
              ),
            ]),
            column({ width: fill, height: fill, gap: 36, justify: "center" }, [
              text("Teza", {
                width: hug,
                height: hug,
                style: textStyle(22, C.teal, { bold: true }),
              }),
              text(
                "Jakość narzędzia AI wynika z zaprojektowanego sposobu pracy: faktów, tooli, skilli i pętli feedbacku.",
                {
                  width: wrap(850),
                  height: fixed(250),
                  style: textStyle(42, C.ink, { bold: true, display: true }),
                },
              ),
              row({ width: fill, height: hug, gap: 14, align: "center" }, [
                chip("fakty", C.amberSoft, C.amber, fixed(78)),
                arrow(C.line),
                chip("skille", C.violetSoft, C.violet, fixed(78)),
                arrow(C.line),
                chip("toole", C.blueSoft, C.blue, fixed(78)),
                arrow(C.line),
                chip("wynik", C.greenSoft, C.green, fixed(78)),
              ]),
            ]),
          ],
        ),
      ]),
    ]),
    { frame: { left: 0, top: 0, width: W, height: H }, baseUnit: 8 },
  );
  slide.speakerNotes.setText(meta.note);
}

function addPhilosophy(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("philosophy"),
    grid(
      { width: fill, height: fill, columns: [fr(1), auto, fr(1)], columnGap: 34, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 20 }, [
          strongText("Antywzorzec", 46, C.red),
          conceptCard("Dane bez metody", "model dostaje wszystko i sam ma odkryć, co jest ważne", C.redSoft, C.red),
          conceptCard("Efekt uboczny", "więcej szumu, kosztu i odpowiedzi, której trudno zaufać", C.graphiteSoft, C.graphite),
        ]),
        arrow(C.line),
        column({ width: fill, height: hug, gap: 20 }, [
          strongText("Wzorzec", 46, C.teal),
          conceptCard("Plan analizy", "narzędzie wie, jakie fakty zebrać i kiedy poprosić AI o interpretację", C.tealSoft, C.teal),
          conceptCard("Kontrolowane doczytanie", "Copilot używa tooli tylko po coś: żeby uzupełnić konkretną lukę", C.blueSoft, C.blue),
        ]),
      ],
    ),
    { accent: C.red },
  );
}

function addProfessionalPrompting(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("professional prompting"),
    grid(
      { width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 62, alignItems: "center" },
      [
        column({ width: fill, height: fill, gap: 24, justify: "center" }, [
          strongText("Prompt nie jest sztuczką językową.", 54, C.ink),
          bodyText("Jest instrukcją wykonawczą dla AI, wynikającą z tego, jak ekspert sam poprowadziłby analizę.", 31),
        ]),
        column({ width: fill, height: hug, gap: 14 }, [
          conceptCard("Najpierw analiza systemowa", "co wiemy, czego nie wiemy, co trzeba rozstrzygnąć", C.paper, C.teal),
          conceptCard("Potem plan pracy", "kolejność kroków, warunki użycia tooli i granice eksploracji", C.blueSoft, C.blue),
          conceptCard("Na końcu prompt", "zwięzły zapis celu, kontekstu, oczekiwanego wyniku i ograniczeń", C.amberSoft, C.amber),
          conceptCard("AI jako egzekutor", "model wykonuje plan, a człowiek ocenia wynik i poprawia narzędzie", C.greenSoft, C.green),
        ]),
      ],
    ),
    { accent: C.amber },
  );
}

function addHumanAnalysis(presentation) {
  const slide = presentation.slides.add();
  const steps = [
    ["Zbieram fakty", "logi, metryki, kod i dane odpowiadają na różne pytania o ten sam ślad", C.greenSoft, C.green],
    ["Łączę sygnały", "sprawdzam, które obserwacje mówią o tym samym flow albo tym samym błędzie", C.blueSoft, C.blue],
    ["Dedukuję", "odrzucam hipotezy, szukam brakujących potwierdzeń i pilnuję poziomu pewności", C.amberSoft, C.amber],
    ["Definiuję rezultat", "na końcu potrzebuję diagnozy, wpływu, handoffu i ograniczeń widoczności", C.violetSoft, C.violet],
    ["Korzystam z pamięci projektu", "część powiązań jest w głowie człowieka, a nie w dokumentacji ani w narzędziach", C.paper, C.teal],
  ];
  shell(
    slide,
    slideMeta("human analysis"),
    grid(
      { width: fill, height: fill, columns: [fr(0.86), fr(1.14)], columnGap: 58, alignItems: "center" },
      [
        column({ width: fill, height: fill, gap: 24, justify: "center" }, [
          strongText("Najpierw pytam: jak sam bym to rozwiązał?", 54, C.ink),
          bodyText("Dopiero po przejściu tej ścieżki widać, co da się zautomatyzować, a czego pipeline jeszcze nie wie.", 31),
        ]),
        column(
          { width: fill, height: hug, gap: 12 },
          steps.map(([title, detail, bg, accent]) => slimCard(title, detail, bg, accent)),
        ),
      ],
    ),
    { accent: C.teal },
  );
}

function addProductIdea(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("product idea"),
    grid(
      { width: fill, height: fill, columns: [fr(0.85), fr(1.15)], columnGap: 64, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 24 }, [
          strongText("Narzędzie prowadzi analizę tak, jak zrobiłby to ekspert.", 54, C.ink),
          bodyText("AI nie startuje w próżni. Dostaje kontekst przygotowany przez aplikację i pracuje według skilli.", 31),
        ]),
        column({ width: fill, height: hug, gap: 12 }, [
          slimCard("Minimalny sygnał incydentu", "na wejściu wystarcza trop, od którego zaczyna się analiza", C.paper, C.teal),
          slimCard("Kontekst przygotowany przez narzędzie", "logi, metryki, kod i dane są zbierane celowo", C.greenSoft, C.green),
          slimCard("Luka wiedzy z głowy", "katalog operacyjny uzupełnia to, co wcześniej człowiek wnosił z doświadczenia", C.amberSoft, C.amber),
          slimCard("AI jako interpretator", "Copilot łączy fakty, doczytuje braki i formułuje wynik dla operatora", C.blueSoft, C.blue),
        ]),
      ],
    ),
    { accent: C.teal },
  );
}

function addPipeline(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("pipeline"),
    column({ width: fill, height: fill, gap: 34, justify: "center" }, [
      row({ width: fill, height: hug, gap: 18, align: "center" }, [
        conceptCard("Pipeline deterministyczny", "zbiera i porządkuje fakty przed AI", C.greenSoft, C.green, fixed(470)),
        arrow(C.teal),
        conceptCard("Punkt przejścia", "wiemy, co mamy, czego nie mamy i które luki warto uzupełnić", C.amberSoft, C.amber, fixed(540)),
        arrow(C.teal),
        conceptCard("Pipeline AI", "Copilot interpretuje fakty w oparciu o skille i dostępne toole", C.blueSoft, C.blue, fixed(470)),
      ]),
      panel(
        { fill: C.paper, borderRadius: 18, padding: { x: 34, y: 28 }, width: fill, height: hug },
        row({ width: fill, height: hug, gap: 30, align: "center" }, [
          strongText("To nie jest jeden wielki prompt.", 42, C.ink, fixed(520)),
          bodyText("Najpierw narzędzie przygotowuje sytuację analityczną. Dopiero potem AI dostaje zadanie, skille i możliwość celowego doczytania braków.", 29, C.muted),
        ]),
      ),
    ]),
    { accent: C.green },
  );
}

function addAiTools(presentation) {
  const slide = presentation.slides.add();
  const tools = [
    ["Logi z Elasticsearch", "co wydarzyło się w korelowanym flow i gdzie pojawił się symptom", C.amberSoft, C.amber],
    ["Metryki z Dynatrace", "czy obserwacje z systemu potwierdzają problem: obciążenie, błędy, degradację albo brak sygnału", C.greenSoft, C.green],
    ["Kod z GitLaba", "który fragment kodu pomaga zrozumieć flow, punkt wejścia albo miejsce naprawy", C.blueSoft, C.blue],
    ["Dane z DB", "czy hipoteza dotycząca danych da się bezpiecznie potwierdzić lub odrzucić", C.violetSoft, C.violet],
    ["Operational context", "jaki system, proces, bounded context, zespół i handoff są właściwym zakresem", C.tealSoft, C.teal],
  ];
  shell(
    slide,
    slideMeta("ai tools"),
    grid(
      { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 54, alignItems: "center" },
      [
        column({ width: fill, height: fill, gap: 20, justify: "center" }, [
          strongText("Copilot widzi wbudowane AI tools, ale nie używa ich przypadkowo.", 52),
          bodyText("Każdy tool ma cel analityczny i jest uruchamiany po to, żeby uzupełnić konkretny brak widoczności.", 30),
        ]),
        column(
          { width: fill, height: hug, gap: 12 },
          tools.map(([title, detail, bg, accent]) => slimCard(title, detail, bg, accent)),
        ),
      ],
    ),
    { accent: C.blue },
  );
}

function addOperationalContext(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("operational context"),
    grid(
      { width: fill, height: fill, columns: [fr(0.9), fr(1.1)], columnGap: 58, alignItems: "center" },
      [
        column({ width: fill, height: fill, gap: 20, justify: "center" }, [
          strongText("To nie był kolejny ekran w manualnej analizie.", 52),
          bodyText("To brak, który ujawnił się dopiero przy próbie zamiany ludzkiego toku myślenia w powtarzalny pipeline.", 30),
        ]),
        column({ width: fill, height: hug, gap: 14 }, [
          conceptCard("Problem", "człowiek korelował fakty wiedzą zdobytą przez lata pracy z projektem", C.paper, C.teal),
          conceptCard("Luka automatyzacji", "pipeline miał fakty, ale nie miał mapy, która mówi, jak je osadzić w systemie", C.amberSoft, C.amber),
          conceptCard("Rozwiązanie", "operational context daje Copilotowi uporządkowaną pamięć projektu", C.blueSoft, C.blue),
          conceptCard("Granica", "to wskazówka do zakresu i handoffu, nie samodzielny dowód root cause", C.graphiteSoft, C.graphite),
        ]),
      ],
    ),
    { accent: C.teal },
  );
}

function addSkills(presentation) {
  const slide = presentation.slides.add();
  const skills = [
    ["starterSkillName", "incident-analysis-orchestrator", "prowadzi główną kolejność pracy AI: od zebranych faktów, przez luki widoczności, do finalnej analizy", C.tealSoft, C.teal],
    ["diagnosticSkillNames", "incident-operational-context-tools", "uczy, kiedy użyć katalogu operacyjnego do osadzenia incydentu w systemie, procesie i handoffie", C.greenSoft, C.green],
    ["diagnosticSkillNames", "incident-analysis-gitlab-tools", "uczy ukierunkowanego czytania kodu z GitLaba wtedy, gdy techniczny handoff potrzebuje konkretu", C.blueSoft, C.blue],
    ["diagnosticSkillNames", "incident-data-diagnostics", "uczy bezpiecznej weryfikacji hipotez danych przez DB tool, bez traktowania bazy jak przeglądarki danych", C.violetSoft, C.violet],
    ["resultSkillNames", "incident-functional-analysis", "pilnuje, żeby wynik był zrozumiały dla analityka biznesowo-systemowego", C.amberSoft, C.amber],
    ["resultSkillNames", "incident-technical-handoff", "pilnuje, żeby wynik techniczny dało się naprawić, zweryfikować albo przekazać dalej", C.redSoft, C.red],
  ];
  shell(
    slide,
    slideMeta("skills"),
    panel(
      { fill: C.paper, borderRadius: 18, padding: { x: 30, y: 24 }, width: fill, height: fill },
      grid(
        { width: fill, height: fill, columns: [fr(1), fr(1)], columnGap: 18, rowGap: 14 },
        skills.map(([group, name, detail, bg, accent]) =>
          panel(
            { fill: bg, borderRadius: 14, padding: { x: 24, y: 18 }, width: fill, height: hug },
            column({ width: fill, height: hug, gap: 8 }, [
              text(group, { width: fill, height: hug, style: textStyle(17, accent, { bold: true }) }),
              text(name, { width: fill, height: hug, style: textStyle(25, C.ink, { bold: true, mono: true }) }),
              text(detail, { width: fill, height: hug, style: textStyle(19, C.muted) }),
            ]),
          ),
        ),
      ),
    ),
    { accent: C.violet, titleSize: 48 },
  );
}

function addToolUse(presentation) {
  const slide = presentation.slides.add();
  const questions = [
    ["Logi", "co dokładnie zaszło?"],
    ["Metryki", "czy metryki to potwierdzają?"],
    ["Kod", "gdzie to się dzieje?"],
    ["Dane", "czy stan danych pasuje do hipotezy?"],
    ["Operational context", "jaki jest właściwy zakres i właściciel?"],
  ];
  shell(
    slide,
    slideMeta("tool use"),
    grid(
      { width: fill, height: fill, columns: [fr(0.82), fr(1.18)], columnGap: 56, alignItems: "center" },
      [
        column({ width: fill, height: fill, gap: 20, justify: "center" }, [
          strongText("Najważniejsze pytanie nie brzmi: „jakie dane mamy?”", 52),
          bodyText("Brzmi: „który tool odpowie na następną potrzebną decyzję analityczną?”", 32, C.muted),
        ]),
        column(
          { width: fill, height: hug, gap: 14 },
          questions.map(([title, q], idx) =>
            row({ width: fill, height: hug, gap: 18, align: "center" }, [
              panel(
                {
                  fill: idx < 2 ? C.ink : C.teal,
                  borderRadius: "rounded-full",
                  width: fixed(52),
                  height: fixed(52),
                  align: "center",
                  justify: "center",
                },
                text(String(idx + 1), { width: hug, height: hug, style: textStyle(21, C.paper, { bold: true, alignment: "center" }) }),
              ),
              panel(
                { fill: idx % 2 === 0 ? C.paper : C.graphiteSoft, borderRadius: 14, padding: { x: 24, y: 18 }, width: fill, height: hug },
                row({ width: fill, height: hug, gap: 18, align: "center" }, [
                  text(title, { width: fixed(260), height: hug, style: textStyle(25, C.ink, { bold: true }) }),
                  text(q, { width: fill, height: hug, style: textStyle(25, C.muted) }),
                ]),
              ),
            ]),
          ),
        ),
      ],
    ),
    { accent: C.blue },
  );
}

function addResult(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("result"),
    grid(
      { width: fill, height: fill, columns: [fr(0.95), fr(1.05)], columnGap: 58, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 28 }, [
          strongText("AI ma oddać wynik, z którym da się pracować.", 54),
          bodyText("Nie chodzi o ładną narrację. Chodzi o decyzję: co wiemy, co sprawdzić dalej i komu przekazać temat.", 31),
        ]),
        column({ width: fill, height: hug, gap: 14 }, [
          conceptCard("Co prawdopodobnie jest problemem", "krótka diagnoza osadzona w zebranych faktach", C.paper, C.teal),
          conceptCard("Analiza funkcjonalna", "wyjaśnienie wpływu na proces, system, kontekst i użytkownika", C.greenSoft, C.green),
          conceptCard("Handoff techniczny", "materiał do naprawy, weryfikacji albo przekazania poza analizowany system", C.blueSoft, C.blue),
          conceptCard("Ograniczenia widoczności", "jawne wskazanie, czego narzędzie nie widziało lub nie potwierdziło", C.amberSoft, C.amber),
        ]),
      ],
    ),
    { accent: C.amber },
  );
}

function addLearningLoop(presentation) {
  const slide = presentation.slides.add();
  shell(
    slide,
    slideMeta("learning loop"),
    panel(
      { fill: C.paper, borderRadius: 18, padding: { x: 32, y: 28 }, width: fill, height: fill },
      grid(
        { width: fill, height: fill, columns: [fr(0.85), fr(1.15)], columnGap: 36, alignItems: "center" },
        [
          column({ width: fill, height: fill, gap: 20, justify: "center" }, [
            strongText("UI jest miejscem, gdzie uczymy się poprawiać narzędzie.", 48),
            bodyText("Operator widzi nie tylko odpowiedź, ale też ślady pracy AI i koszt eksploracji.", 30),
          ]),
          grid(
            { width: fill, height: hug, columns: [fr(1), fr(1)], columnGap: 16, rowGap: 16 },
            [
              slimCard("Prompt", "czy instrukcja i skille prowadzą model we właściwą stronę", C.amberSoft, C.amber),
              slimCard("Evidence", "czy zebrany kontekst jest wystarczający i czytelny", C.greenSoft, C.green),
              slimCard("Tool activity", "które toole zostały użyte i po co", C.blueSoft, C.blue),
              slimCard("Usage i koszt", "ile zapłaciliśmy za interpretację i doczytanie braków", C.graphiteSoft, C.graphite),
              slimCard("Feedback", "które wyniki tooli były użyteczne, puste albo za szerokie", C.violetSoft, C.violet),
              slimCard("Kolejna iteracja", "co poprawić: skill, opis toola, zakres albo katalog", C.tealSoft, C.teal),
            ],
          ),
        ],
      ),
    ),
    { accent: C.teal, titleSize: 50 },
  );
}

function addTakeaway(presentation) {
  const slide = presentation.slides.add();
  const checklist = [
    "Zacznij od manualnej analizy: jak człowiek rozwiązałby problem.",
    "Nazwij wiedzę z głowy, której pipeline nie ma.",
    "Zbierz deterministyczne fakty, zanim poprosisz AI o interpretację.",
    "Daj Copilotowi toole jako celowe źródła kontekstu.",
    "Użyj skilli jako playbooka i oddaj wynik do pracy.",
    "Mierz koszt, ślady pracy i feedback; poprawiaj narzędzie iteracyjnie.",
  ];
  shell(
    slide,
    slideMeta("takeaway"),
    grid(
      { width: fill, height: fill, columns: [fr(0.92), fr(1.08)], columnGap: 64, alignItems: "center" },
      [
        column({ width: fill, height: hug, gap: 28 }, [
          text("Nie zaczynaj od większego modelu.\nZacznij od lepszego sposobu pracy.", {
            width: fill,
            height: hug,
            style: textStyle(56, C.ink, { bold: true, display: true }),
          }),
          bodyText("To jest wzorzec budowy narzędzi AI, nie tylko incident trackera.", 31),
        ]),
        column(
          { width: fill, height: hug, gap: 14 },
          checklist.map((item, idx) =>
            row({ width: fill, height: hug, gap: 18, align: "center" }, [
              panel(
                { fill: idx < 3 ? C.ink : C.teal, borderRadius: "rounded-full", width: fixed(48), height: fixed(48), align: "center", justify: "center" },
                text(String(idx + 1), { width: hug, height: hug, style: textStyle(20, C.paper, { bold: true, alignment: "center" }) }),
              ),
              text(item, { width: fill, height: hug, style: textStyle(25, C.ink) }),
            ]),
          ),
        ),
      ],
    ),
    { accent: C.teal, titleSize: 50 },
  );
}

function addSlides(presentation) {
  addCover(presentation);
  addPhilosophy(presentation);
  addProfessionalPrompting(presentation);
  addHumanAnalysis(presentation);
  addProductIdea(presentation);
  addPipeline(presentation);
  addAiTools(presentation);
  addOperationalContext(presentation);
  addSkills(presentation);
  addToolUse(presentation);
  addResult(presentation);
  addLearningLoop(presentation);
  addTakeaway(presentation);
}

async function writeBlob(blob, filePath) {
  const buffer = Buffer.from(await blob.arrayBuffer());
  await fs.writeFile(filePath, buffer);
}

async function clearGeneratedArtifacts() {
  await fs.mkdir(previewDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });

  const previewFiles = await fs.readdir(previewDir);
  await Promise.all(
    previewFiles
      .filter((file) => /^(source|pptx)-slide-\d+\.png$/.test(file))
      .map((file) => fs.unlink(path.join(previewDir, file))),
  );

  const reportFiles = await fs.readdir(reportsDir);
  await Promise.all(
    reportFiles
      .filter((file) => /^(slide-\d+\.layout\.json|qa-report\.json)$/.test(file))
      .map((file) => fs.unlink(path.join(reportsDir, file))),
  );
}

async function exportSlidePreviews(presentation, prefix) {
  const paths = [];
  for (let i = 0; i < presentation.slides.count; i += 1) {
    const slide = presentation.slides.getItem(i);
    const pngPath = path.join(previewDir, `${prefix}-slide-${String(i + 1).padStart(2, "0")}.png`);
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

async function savePptxWithFallback(presentation) {
  const preferredPath = path.join(outputDir, "incident-tracker-ai-tooling-concept.pptx");
  const fallbackPath = path.join(outputDir, "incident-tracker-ai-tooling-concept-v2.pptx");
  const pptx = await PresentationFile.exportPptx(presentation);

  try {
    await pptx.save(preferredPath);
    return preferredPath;
  } catch (error) {
    if (error?.code !== "EBUSY") {
      throw error;
    }
    await pptx.save(fallbackPath);
    return fallbackPath;
  }
}

function inspectLayout(layout, slideNo) {
  const issues = [];
  for (const element of layout.elements ?? []) {
    const bbox = element.bbox;
    if (!bbox || bbox.length !== 4) continue;
    const [left, top, right, bottom] = bbox;
    const name = element.name ?? element.textPreview ?? element.kind ?? "element";
    const tolerance = 2;
    if (left < -tolerance || top < -tolerance || right > W + tolerance || bottom > H + tolerance) {
      issues.push(`${name} outside slide bounds: [${bbox.join(", ")}]`);
    }
    if (element.textPreview && element.resolvedFontSize && element.resolvedFontSize < 14) {
      issues.push(`${name} text below 14 px: ${element.resolvedFontSize}`);
    }
  }
  return { slide: slideNo, issues };
}

async function build() {
  await fs.mkdir(outputDir, { recursive: true });
  await fs.mkdir(previewDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });
  await clearGeneratedArtifacts();

  const presentation = Presentation.create({
    slideSize: { width: W, height: H },
  });

  addSlides(presentation);

  const pptxPath = await savePptxWithFallback(presentation);

  const sourcePreviews = await exportSlidePreviews(presentation, "source");
  const layoutReports = await exportLayouts(presentation);

  const pptxBytes = await fs.readFile(pptxPath);
  const imported = await PresentationFile.importPptx(pptxBytes);
  const pptxPreviews = await exportSlidePreviews(imported, "pptx");

  const notes = slides
    .map((s, i) => `## ${i + 1}. ${s.title}\n\n${s.note}\n`)
    .join("\n");
  const notesPath = path.join(outputDir, "speaker-notes.md");
  await fs.writeFile(notesPath, notes, "utf8");

  const issues = layoutReports.flatMap((r) => r.issues.map((issue) => ({ slide: r.slide, issue })));
  const report = {
    generatedAt: new Date().toISOString(),
    slideCount: presentation.slides.count,
    pptxPath,
    notesPath,
    sourcePreviews,
    pptxPreviews,
    layoutIssueCount: issues.length,
    issues,
  };
  const reportPath = path.join(reportsDir, "qa-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  console.log(JSON.stringify(report, null, 2));
}

await build();

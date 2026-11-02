# Release summary

Feature posiada porownanie dwoch refow systemu, interpretacje Liquibase,
prompt, policy, raport, job i historie. Jedyny tryb to LIQUIBASE; API poza zakresem.
Nie importuj sibling feature'ow. Reuse integrations, agenttools, aiplatform i shared.
Testy i przyklady sa w calosci fikcyjnym CRM.
Kanoniczny runtime: `docs/architecture/release-summary-runtime-flow.md`.

Refy pinuj osobno per repo, bez fallbacku. Wybieraj tylko primary/priority 1
i zaakceptowane prefixy. Initial context to pelne captured encje opctx,
manifest i patche wylacznie zmian dopasowanych przez
`release-summary.diff.path-contains-any` (domyslnie `sql,changelog`): contains
any of, bez rozrozniania wielkosci liter, na obu pelnych sciezkach.
Negatywne contains `src/test` na dowolnej stronie ma pierwszenstwo nad
pozytywnym filtrem, bez rozrozniania wielkosci liter, takze dla rename.
Pozostale pliki nie trafiaja do evidence, eksportu ani initial promptu.
ReleaseSummaryPreparation uzupelnia brakujace
wybrane roznice przez verified reads obu istniejacych stron i JGit; nie czyta
pozostalych modulow. Dolacza dopasowane `.github/**/*.instructions.md` z target
SHA przez jawne discoverApplicable/applyTo. Instrukcje sa wiedza repozytorium,
nie moga nadpisywac scope, bezpieczenstwa ani kontraktu raportu.
GitLabRepositoryComparisonPort posiada transport i dowod kompletnej listy
przez metadane path/id/mode obu drzew. Feature filtruje oba konce rename.
Niepelny manifest blokuje AI. Blad przygotowania wybranego patcha jest jawna
luka, nie brakiem zmiany ani powodem do skanowania modulow. AI interpretuje
przygotowane roznice i doczytuje potrzebne pelne pliki, niezmienione include,
SQL/CSV i kod przez obecne tools. Brak zaleznosci to badanie albo jawna luka.
Nie przywracaj parsera, pelnego skanu, archiwow, klonow ani starego fallbacku.

QUEUED z planem krokow utrwal przed dispatch. COLLECT_DIFF publikuje manifest
i patch jako patch, nigdy jako pelne beforeContent/afterContent. PREPARE_PROMPT
utrwala dokladny tekst przed modelem i preflight. Evidence i ukonczone kroki
pozostaja po bledzie i restarcie. Kompletna lista Git bez dopasowanych zmian daje NO_CHANGES w zakresie filtra,
bez deklaracji identycznych rewizji. Niepusty wybrany diff wymaga AI.

AI uzywa report tools i GOAL_DRIVEN. Hidden scope zachowuje obie rewizje,
osobne pinowanie research i captured katalog. Sam patch compare nie rejestruje
pelnego sourceRef. Pelne odczyty przygotowania roznic i kompletne instrukcje
rejestruja zrodla zachowywane w prywatnym runtime; nie powtarzaj odczytu tylko
dla cytowania. Cytowania GitLab wymagaja rzeczywistego odczytu. Nie rozszerzaj
uprawnien ani allowlisty narzedzi. Polski skill jest runtime resource;
seed nie nadpisuje effective kopii, wiec sprawdz ich zgodnosc przy zmianie.

AnalysisReport jest trescia wyniku. Sekcje: OVERVIEW=1, TABLE_LIST=2,
DB_DETAIL_<slug>=3..999999 per repo/kwalifikowana tabela, APPENDIX=1000000.
Po walidacji feature dopisuje nowe id do wlasnej session-bound listy sekcji,
ktora platforma odczytuje przy kazdym wywolaniu. Bez wildcardow platformy.
Szczegoly maja Obiekt, Zmiana, Opis funkcjonalny. Zmiana zachowuje SQL;
funkcja jest krotka i bez zargonu kodu. Lista tabel i tytuly sekcji musza
byc zgodne. Cytowania to URL w meta, nigdy linki w tresci. SQL rozwin na
obiekty, bez powtarzajacego wiersza skryptu ani fikcyjnego sql-coverage.
Brak dowodu funkcji trafia do meta.gaps. Nie udawaj stanu dzialajacej bazy.

Wynik release-summary-diff-report-v1 odroznia changeListComplete i changedFiles
od documentedTables, readSources oraz semanticCoverage=AI_ASSESSED.
Nie przywracaj expectedElements, strukturalnego coverage parsera ani
deklaracji pelnej interpretacji. Zapisany raport bez jawnych luk moze miec
COMPLETED, ale nadal nosi systemowe ograniczenie semantyczne w visibilityLimits.
Scaffold jest prywatny; tylko zaakceptowany zapis sekcji publikuje checkpoint.
Waliduj patch tak jak pelny zapis. Model i platformowy preflight pozostaja.

Kontynuacja zachowuje SHA, captured katalog i istniejaca sesje. Follow-up
jest read-only; mutacje wymagaja updateReport. Manual edit wymaga rewizji.
Shared operation guard serializuje mutacje. Usage chatu nie dubluje job.usage.
Import/eksport i zapis maja jeden aktualny format V1, bez migracji i odczytu
starego ksztaltu. Lokalne stare runy usuwaj tylko w osobno zatwierdzonym zakresie.
package pl.mkn.tdw.features.releasesummary.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.shared.ai.report.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ReleaseSummaryReportService {
    public static final String CONTRACT="release-summary-analyst-report-v1";
    public static final String PENDING="Uzupełnij opis funkcjonalny.";
    public static final String SQL_PENDING="Wyjaśnij operację na podstawie pełnego źródła.";
    public static final List<String> DETAIL=List.of("Obiekt","Zmiana","Opis funkcjonalny");
    public static final Map<String,List<String>> HEADERS=Map.of(
            "TABLE_LIST",List.of("Tabela","Zakres zmian"));
    private static final Pattern LINK=Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern DEVELOPER_LANGUAGE=Pattern.compile("(?iu)\\b(?:DAO|DTO|changelog\\w*|changeset\\w*|Liquibase|relativeToChangelogFile|logicalFilePath|deserializ\\w*)\\b|(?:src/|\\.(?:java|ya?ml|xml|kt|tsx?)\\b)|\\b(?:Potwierdzone|Wniosek|Do wyjaśnienia):");
    private final ObjectMapper mapper;
    private final ReleaseSummaryReportInventory inventory;
    private final GitLabSettingsPort settings;
    public record Coverage(String unitId,int expectedElements,int documentedElements,boolean structurallyVerified,List<String> gaps) {}
    public record Result(String resultContract,String status,int expectedUnits,int documentedUnits,int expectedElements,
                         int documentedElements,boolean sourceComplete,List<Coverage> coverage,List<String> visibilityLimits) {}

    public List<String> sectionIds(ReleaseSummaryEvidence evidence) {
        var ids=new ArrayList<>(List.of("OVERVIEW","TABLE_LIST"));
        inventory.groups(evidence).stream().filter(g->!g.appendix()).forEach(g->ids.add(detailId(g)));
        ids.add("APPENDIX");return List.copyOf(ids);
    }
    public void validateOrder(String id,int order,ReleaseSummaryEvidence evidence) {
        int expected=sectionIds(evidence).indexOf(id)+1;
        if(expected==0||order!=expected)throw invalid("Zachowaj id i order sekcji z planu raportu; oczekiwane order: "+expected);
    }
    public AnalysisReport scaffold(String id,ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        // This private seed is not a report result. No placeholder tables are published on checkpoints.
        return new AnalysisReport(id,"Zmiany bazodanowe we wdrożeniu",scope.systemName(),
                "",List.of(),new AnalysisReportMeta(List.of(),List.of(),List.of(),List.of(),null,List.of("Porównanie opisuje planowane zmiany. Nie sprawdzono stanu działającej bazy ani historii wykonania migracji.")));
    }
    public AnalysisReport noChanges(String id,ReleaseSummaryScope scope) {
        return new AnalysisReport(id,"Brak zmian bazodanowych",scope.systemName(),
                "Nie znaleziono zmian definicji bazy danych w porównywanych wersjach.",
                List.of(section("OVERVIEW","Zakres porównania",1,comparison(scope)+
                        "\n\nZweryfikowano pełne drzewa i wymagane pliki źródłowe. Analiza AI nie była potrzebna.")),AnalysisReportMeta.empty());
    }
    public List<AnalysisReportSection> templates(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var groups=inventory.groups(evidence);var result=new ArrayList<AnalysisReportSection>();
        result.add(section("OVERVIEW","Podsumowanie",1,"Napisz 2–3 krótkie zdania o zmianach danych i ich celu."));
        var tables=groups.stream().filter(g->!g.appendix()).toList();
        result.add(section("TABLE_LIST","Lista tabel",2,tables.isEmpty()?"Brak zmian przypisanych do tabel.":ReleaseSummaryTables.table(HEADERS.get("TABLE_LIST"),tables.stream()
                .map(g->List.of(g.title(),"Opisz krótko zakres zmian.")).toList())));
        int order=3;
        for(var group:tables)result.add(new AnalysisReportSection(detailId(group),group.title(),order++,detailTemplate(group),groupMeta(group)));
        var appendix=groups.stream().filter(ReleaseSummaryReportInventory.Group::appendix).findFirst();
        result.add(new AnalysisReportSection("APPENDIX","Appendix",order,appendix.map(this::detailTemplate).orElse("Opisz krótko istotne warunki wdrożenia i ograniczenia."),
                appendix.map(this::groupMeta).orElse(AnalysisReportMeta.empty())));
        return List.copyOf(result);
    }
    public String detailTemplate(ReleaseSummaryReportInventory.Group group) {
        return ReleaseSummaryTables.table(DETAIL,group.items().stream().filter(item->!expandedSql(group,item)).map(item->List.of(item.label(),
                technical(item),PENDING)).toList());
    }
    private boolean expandedSql(ReleaseSummaryReportInventory.Group group,ReleaseSummaryReportInventory.Item item) {
        return !group.appendix()&&item.kind().equals("sql");
    }
    private AnalysisReportMeta groupMeta(ReleaseSummaryReportInventory.Group group) {
        var refs=new ArrayList<AnalysisReportReference>();
        group.items().stream().flatMap(i->i.sources().stream()).distinct().forEach(ref->refs.add(
                new AnalysisReportReference("source",ref.substring(ref.lastIndexOf(':')+1),sourceLink(ref),"Źródło definicji zmian.")));
        group.items().stream().filter(i->expandedSql(group,i)).forEach(i->refs.add(new AnalysisReportReference(
                "sql-coverage",i.label(),sourceLink(i.sources().get(i.sources().size()-1)),"Wpisz nazwy opisanych obiektów, każdą w osobnej linii.")));
        return new AnalysisReportMeta(refs,List.of(),List.of(),List.of(),null,List.of());
    }
    public String technical(ReleaseSummaryReportInventory.Item item) {
        return item.change()+". "+(item.interpreted()?SQL_PENDING:item.definition());
    }
    private AnalysisReportSection section(String id,String title,int order,String markdown) {
        return new AnalysisReportSection(id,title,order,markdown,AnalysisReportMeta.empty());
    }
    private String comparison(ReleaseSummaryScope scope) {
        return "**System:** "+scope.systemName()+".\n\n"+scope.repositories().stream().map(r->
                "**"+r.project()+":** "+shortRef(r.baselineRef())+" → "+shortRef(r.targetRef()))
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }
    private String shortRef(String ref){return ref.replaceFirst("^refs/(heads|tags)/","");}
    private String detailId(ReleaseSummaryReportInventory.Group group){return group.appendix()?"APPENDIX":"DB_DETAIL_"+group.id();}
    public String json(Object value) {
        try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Nie można przygotować kontraktu raportu.",e);}
    }
    public Set<String> initialSources(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var refs=new HashSet<String>();evidence.sources().forEach(s->refs.add(s.sourceRef()));
        scope.entities().forEach(e->refs.add("opctx:"+e.get("type")+":"+e.get("id")));return refs;
    }
    public List<String> sourceRefs(ReleaseSummaryEvidence.Unit unit) {
        var refs=new ArrayList<String>();if(unit.baselineSource()!=null)refs.add(unit.baselineSource());
        if(unit.targetSource()!=null&&!refs.contains(unit.targetSource()))refs.add(unit.targetSource());return refs;
    }
    public Map<String,String> sourceLinks(Set<String> refs) {
        var links=new TreeMap<String,String>();refs.forEach(ref->links.put(ref,sourceLink(ref)));return links;
    }
    private String sourceLink(String ref) {
        if(ref.startsWith("opctx:")) {
            var parts=ref.substring(6).split(":",2);
            if(parts.length!=2)throw invalid("Niepoprawne źródło Operational Context.");
            return "/api/operational-context/entities/"+encode(parts[0])+"/"+encode(parts[1]);
        }
        var matcher=Pattern.compile("^gitlab:(.+)@([a-fA-F0-9]{40}|[a-fA-F0-9]{64}):(.+)$").matcher(ref);
        if(!matcher.matches())throw invalid("Źródło nie wskazuje odczytanego pliku na przypiętej rewizji.");
        var base=Objects.toString(settings.getBaseUrl(),"").replaceFirst("/+$","").replaceFirst("/api/v4$","");
        if(!base.startsWith("https://")&&!base.startsWith("http://"))throw invalid("Brak adresu GitLaba dla odnośników do źródeł.");
        return base+"/"+encodePath(matcher.group(1))+"/-/blob/"+matcher.group(2)+"/"+encodePath(matcher.group(3));
    }
    private String encodePath(String value){return Arrays.stream(value.split("/",-1)).map(this::encode).collect(java.util.stream.Collectors.joining("/"));}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}

    public void validateSection(String id,String markdown,ReleaseSummaryEvidence evidence,Set<String> knownSources) {
        if(markdown==null||markdown.isBlank())throw invalid("Sekcja wymaga treści dla analityka.");
        if(List.of("Napisz 2–3 krótkie zdania", "Opisz krótko zakres zmian.","Opisz krótko istotne warunki wdrożenia i ograniczenia.")
                .stream().anyMatch(markdown::contains))throw invalid("Zastąp instrukcje konkretną treścią raportu.");
        if(markdown.contains("/databaseChangeLog:")||markdown.contains("DO_UZUPELNIENIA_")||markdown.contains("| elementId |")||markdown.contains("| unitId |"))
            throw invalid("Raport ma opisywać obiekty bazy danych, bez identyfikatorów i ścieżek parsera.");
        if(id.equals("OVERVIEW")){validateNarrative(markdown);return;}
        var groups=inventory.groups(evidence);
        if(HEADERS.containsKey(id)) {
            var tables=groups.stream().filter(g->!g.appendix()).toList();
            if(tables.isEmpty()&&markdown.equals("Brak zmian przypisanych do tabel."))return;
            var table=requiredTable(markdown,HEADERS.get(id));
            var rows=uniqueRows(table);
            if(!rows.keySet().equals(new HashSet<>(tables.stream().map(ReleaseSummaryReportInventory.Group::title).toList())))
                throw invalid("Lista tabel musi zawierać każdą zmienianą tabelę, bez odnośników do innych sekcji.");
            validateNarrative(markdown);
            return;
        }
        if(id.equals("APPENDIX")&&groups.stream().noneMatch(ReleaseSummaryReportInventory.Group::appendix)){validateNoLinks(markdown);return;}
        var group=groups.stream().filter(g->detailId(g).equals(id)).findFirst().orElseThrow(()->invalid("Nieznana sekcja raportu."));
        var rows=uniqueRows(requiredTable(markdown,DETAIL));
        for(var item:group.items()) {
            if(expandedSql(group,item)) {
                if(itemRow(item,rows)!=null)throw invalid("Opisz obiekty skryptu SQL bez dodatkowego wiersza powtarzającego skrypt. Mapowanie zapisz w meta.references typu sql-coverage.");
                continue;
            }
            var row=itemRow(item,rows);if(row==null)throw invalid("Brak opisu: "+item.label());
            if(!item.interpreted()&&!preservesDefinition(row.get(1),item))throw invalid("Zachowaj zweryfikowaną definicję na początku pola Zmiana; dodatkowe szczegóły dopisz po <br>: "+item.label());
            if(item.interpreted()&&!row.get(1).startsWith(item.change()+"."))throw invalid("Zachowaj kierunek zmiany: "+item.change()+". "+item.label());
        }
        if(group.structurallyVerified()&&rows.size()!=group.items().size())throw invalid("Dodatkowe obiekty wymagają nierozpoznanej operacji w źródłach.");
        for(var row:rows.values()) {
            if(row.get(1).contains(SQL_PENDING)||row.get(2).equals(PENDING))throw invalid("Uzupełnij opis przed opublikowaniem sekcji: "+row.get(0));
            validateNoLinks(row.get(0));
            var verifiedDefinition=group.items().stream().filter(item->!item.interpreted()&&item.label().equals(row.get(0))).findFirst();
            validateNoLinks(verifiedDefinition.map(item->row.get(1).substring(technical(item).length())).orElse(row.get(1)));
            if(!group.appendix())validateNarrative(row.get(2));else validateNoLinks(row.get(2));
        }
        // Technical definitions may legitimately contain URLs or application-like literals.
        // Check the authored narrative without rejecting those verified SQL values.
        var prose=markdown.lines().filter(line->!line.strip().startsWith("|")).collect(java.util.stream.Collectors.joining("\n"));
        if(!group.appendix()&&!prose.isBlank())throw invalid("Szczegóły obiektów umieść w tabeli: SQL w kolumnie Zmiana, zastosowanie w Opis funkcjonalny; źródła i ograniczenia w meta.");
        validateNoLinks(prose);
    }
    private boolean preservesDefinition(String value,ReleaseSummaryReportInventory.Item item) {
        var required=technical(item);
        return value.equals(required)||(value.startsWith(required+"<br>")&&!value.substring(required.length()+4).isBlank());
    }
    private void validateNarrative(String text) {
        validateNoLinks(text);
        if(DEVELOPER_LANGUAGE.matcher(text).find())throw invalid("Opis ma być zrozumiały bez znajomości kodu i Liquibase. Usuń żargon i prefiksy Potwierdzone/Wniosek; szczegóły źródeł przenieś do meta.");
    }
    private void validateNoLinks(String text) {
        if(LINK.matcher(text).find()||text.contains("https://")||text.contains("http://")||text.contains("#report-section-"))
            throw invalid("Odnośniki do źródeł umieść wyłącznie w meta.references; sekcje mają być samodzielne.");
    }
    public void validateMeta(AnalysisReportMeta meta,Set<String> known) {
        var allowed=new HashSet<>(sourceLinks(known).values());
        if(meta!=null)for(var ref:meta.references())if(ref==null||!allowed.contains(ref.target()))throw invalid("Metadane mogą cytować tylko URL odczytanego źródła.");
    }
    public void validatePublication(String id,String markdown,AnalysisReportMeta meta,ReleaseSummaryEvidence evidence,Set<String> known) {
        validateSection(id,markdown,evidence,known);validateMeta(meta,known);
        var group=inventory.groups(evidence).stream().filter(g->detailId(g).equals(id)).findFirst();
        if(group.isEmpty())return;
        var rows=uniqueRows(requiredTable(markdown,DETAIL));
        var refs=meta==null?List.<AnalysisReportReference>of():meta.references();
        for(var item:group.get().items()) {
            var urls=item.sources().stream().map(this::sourceLink).collect(java.util.stream.Collectors.toSet());
            if(refs.stream().noneMatch(ref->urls.contains(ref.target())))throw invalid("Dodaj źródło definicji w meta.references: "+item.label());
            if(!expandedSql(group.get(),item))continue;
            var mappings=refs.stream().filter(ref->"sql-coverage".equals(ref.type())&&item.label().equals(ref.label())&&urls.contains(ref.target())).toList();
            if(mappings.size()!=1)throw invalid("Zapisz jedno mapowanie sql-coverage w meta.references dla: "+item.label());
            var mapped=Objects.toString(mappings.get(0).description(),"").lines().map(String::strip).filter(s->!s.isBlank()).toList();
            if(mapped.isEmpty()||!rows.keySet().containsAll(mapped))throw invalid("Opis sql-coverage musi zawierać dokładne nazwy opisanych obiektów, po jednej w każdej linii: "+item.label());
            for(var label:mapped)group.get().items().stream().filter(knownItem->!knownItem.interpreted()&&knownItem.label().equals(label)).findFirst().ifPresent(knownItem->{
                if(rows.get(label).get(1).equals(technical(knownItem)))throw invalid("Dopisz szczegóły operacji SQL w kolumnie Zmiana przy obiekcie: "+label);
            });
        }
    }
    private ReleaseSummaryTables.Table requiredTable(String markdown,List<String> headers) {
        var tables=ReleaseSummaryTables.parse(markdown);
        var matches=tables.stream().filter(t->t.headers().equals(headers)).toList();
        if(matches.size()!=1||tables.size()!=1)throw invalid("Wymagana jedna tabela z kolumnami: "+headers);
        for(var row:matches.get(0).rows())if(row.stream().anyMatch(String::isBlank))throw invalid("Opisz każdą komórkę albo wskaż konkretny brak wiedzy.");
        return matches.get(0);
    }
    private Map<String,List<String>> uniqueRows(ReleaseSummaryTables.Table table) {
        var result=new LinkedHashMap<String,List<String>>();for(var row:table.rows())if(result.putIfAbsent(row.get(0),row)!=null)throw invalid("Powtórzony obiekt: "+row.get(0));return result;
    }
    private List<String> itemRow(ReleaseSummaryReportInventory.Item item,Map<String,List<String>> rows) {
        if(rows.containsKey(item.label()))return rows.get(item.label());
        if(!item.interpreted())return null;
        var found=rows.entrySet().stream().filter(e->e.getKey().startsWith(item.label()+" — ")).map(Map.Entry::getValue).toList();
        return found.size()==1?found.get(0):null;
    }
    private IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}

    public Result project(AnalysisReport report,ReleaseSummaryEvidence evidence,Set<String> known) {
        var groups=inventory.groups(evidence);var limits=new LinkedHashSet<String>();
        evidence.gaps().forEach(g->limits.add(g.detail()));
        var sections=new HashMap<String,AnalysisReportSection>();
        if(report!=null)for(var section:report.sections())if(sections.put(section.id(),section)!=null)limits.add("Powtórzona sekcja: "+section.title());
        var required=evidence.noChanges()?List.of("OVERVIEW"):sectionIds(evidence);
        for(var id:required) {
            var section=sections.get(id);
            if(section==null){limits.add("Nie zapisano jeszcze wszystkich wymaganych części raportu.");continue;}
            try{validatePublication(id,section.markdown(),section.meta(),evidence,known);}
            catch(IllegalArgumentException e){limits.add(section.title()+": "+e.getMessage());}
            limits.addAll(section.meta().gaps());limits.addAll(section.meta().visibilityLimits());
        }
        var coverage=new ArrayList<Coverage>();
        for(var group:groups) {
            var gaps=new ArrayList<String>();int documented=0;var section=sections.get(detailId(group));
            if(section==null)gaps.add("Nie zapisano szczegółów zmian.");
            else try {
                validatePublication(section.id(),section.markdown(),section.meta(),evidence,known);
                var rows=uniqueRows(requiredTable(section.markdown(),DETAIL));documented=group.items().size();
                for(var row:rows.values())if(row.get(2).startsWith("Nie ustalono"))gaps.add(row.get(0)+": "+row.get(2));
            }catch(IllegalArgumentException e){gaps.add(e.getMessage());}
            if(!group.structurallyVerified())gaps.add("Pełność operacji SQL lub niestandardowych zmian wymaga weryfikacji analityka.");
            coverage.add(new Coverage(group.id(),group.items().size(),documented,group.structurallyVerified(),List.copyOf(gaps)));
            gaps.forEach(g->limits.add(group.title()+": "+g));
        }
        if(report!=null){try{validateMeta(report.meta(),known);}catch(IllegalArgumentException e){limits.add(e.getMessage());}
            limits.addAll(report.meta().gaps());limits.addAll(report.meta().visibilityLimits());}
        var status=limits.isEmpty()&&evidence.sourceComplete()?(evidence.noChanges()?"NO_CHANGES":"COMPLETED"):"PARTIAL";
        return new Result(CONTRACT,status,groups.size(),(int)coverage.stream().filter(c->c.expectedElements()==c.documentedElements()).count(),
                coverage.stream().mapToInt(Coverage::expectedElements).sum(),coverage.stream().mapToInt(Coverage::documentedElements).sum(),
                evidence.sourceComplete(),coverage,List.copyOf(limits));
    }
}

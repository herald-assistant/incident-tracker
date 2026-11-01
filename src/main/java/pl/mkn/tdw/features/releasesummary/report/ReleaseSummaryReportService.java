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
    public static final List<String> DETAIL=List.of("Obiekt","Zmiana techniczna","Znaczenie funkcjonalne","Źródła");
    public static final Map<String,List<String>> HEADERS=Map.of(
            "CHANGE_INDEX",List.of("Obszar","Co się zmienia","Szczegóły"),
            "DEPLOYMENT_IMPACT",List.of("Obszar","Wpływ i ryzyko","Co sprawdzić","Źródła"));
    private static final Pattern LINK=Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private final ObjectMapper mapper;
    private final ReleaseSummaryReportInventory inventory;
    private final GitLabSettingsPort settings;
    public record Coverage(String unitId,int expectedElements,int documentedElements,boolean structurallyVerified,List<String> gaps) {}
    public record Result(String resultContract,String status,int expectedUnits,int documentedUnits,int expectedElements,
                         int documentedElements,boolean sourceComplete,List<Coverage> coverage,List<String> visibilityLimits) {}

    public List<String> sectionIds(ReleaseSummaryEvidence evidence) {
        var ids=new ArrayList<>(List.of("OVERVIEW","CHANGE_INDEX","DEPLOYMENT_IMPACT"));
        inventory.groups(evidence).forEach(g->ids.add(detailId(g)));return List.copyOf(ids);
    }
    public AnalysisReport scaffold(String id,ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        // This private seed is not a report result. No placeholder tables are published on checkpoints.
        return new AnalysisReport(id,"Zmiany bazodanowe we wdrożeniu",scope.systemName(),
                "Porównanie definicji migracji. Raport nie potwierdza wykonania zmian w konkretnej bazie.",
                List.of(),AnalysisReportMeta.empty());
    }
    public AnalysisReport noChanges(String id,ReleaseSummaryScope scope) {
        return new AnalysisReport(id,"Brak zmian bazodanowych",scope.systemName(),
                "Nie znaleziono zmian Liquibase w porównywanych rewizjach.",
                List.of(section("OVERVIEW","Zakres porównania",0,comparison(scope)+
                        "\n\nZweryfikowano pełne drzewa i wymagane pliki źródłowe. Analiza AI nie była potrzebna.")),AnalysisReportMeta.empty());
    }
    public List<AnalysisReportSection> templates(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var groups=inventory.groups(evidence);var result=new ArrayList<AnalysisReportSection>();
        result.add(section("OVERVIEW","W skrócie",0,comparison(scope)+"\n\nNapisz 2–4 zdania: najważniejsze zmiany danych, ich cel i główny wpływ na wdrożenie."));
        result.add(section("CHANGE_INDEX","Lista zmian",1,ReleaseSummaryTables.table(HEADERS.get("CHANGE_INDEX"),groups.stream()
                .map(g->List.of(g.title(),"Opisz krótko zakres zmian.",detailLink(g))).toList())));
        int order=2;
        for(var group:groups)result.add(section(detailId(group),group.title(),order++,detailTemplate(group)));
        result.add(section("DEPLOYMENT_IMPACT","Wpływ na wdrożenie i weryfikacja",order,
                ReleaseSummaryTables.table(HEADERS.get("DEPLOYMENT_IMPACT"),List.of(List.of("Migracje","Opisz wpływ i ryzyko.","Podaj konkretne sprawdzenia.","Nie dotyczy")))));
        return List.copyOf(result);
    }
    public String detailTemplate(ReleaseSummaryReportInventory.Group group) {
        return ReleaseSummaryTables.table(DETAIL,group.items().stream().map(item->List.of(item.label(),
                technical(item),PENDING,citations(item.sources()))).toList());
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
    private String detailId(ReleaseSummaryReportInventory.Group group){return "DB_DETAIL_"+group.id();}
    private String detailLink(ReleaseSummaryReportInventory.Group group){return "[Zobacz szczegóły](#report-section-"+detailId(group)+")";}
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
    public String citations(Collection<String> refs) {
        int i=0;var links=new ArrayList<String>();for(var ref:refs)links.add("["+(++i)+"]("+sourceLink(ref)+")");
        return links.isEmpty()?"Nie dotyczy":String.join(" ",links);
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
        if(List.of("Napisz 2–4 zdania:","Opisz krótko zakres zmian.","Opisz wpływ i ryzyko.","Podaj konkretne sprawdzenia.")
                .stream().anyMatch(markdown::contains))throw invalid("Zastąp instrukcje konkretną treścią raportu.");
        if(markdown.contains("/databaseChangeLog:")||markdown.contains("DO_UZUPELNIENIA_")||markdown.contains("| elementId |")||markdown.contains("| unitId |"))
            throw invalid("Raport ma opisywać obiekty bazy danych, bez identyfikatorów i ścieżek parsera.");
        if(id.equals("OVERVIEW")){validateLinks(markdown,knownSources);return;}
        var groups=inventory.groups(evidence);
        if(HEADERS.containsKey(id)) {
            var table=requiredTable(markdown,HEADERS.get(id));
            if(id.equals("CHANGE_INDEX")) {
                var links=new HashSet<String>();
                for(var row:table.rows())if(!links.add(row.get(2)))throw invalid("Powtórzony zakres w liście zmian.");
                if(!links.equals(new HashSet<>(groups.stream().map(this::detailLink).toList())))throw invalid("Lista zmian musi odsyłać do każdego wymaganego zakresu.");
            } else validateLinks(markdown,knownSources);
            return;
        }
        var group=groups.stream().filter(g->detailId(g).equals(id)).findFirst().orElseThrow(()->invalid("Nieznana sekcja raportu."));
        var rows=uniqueRows(requiredTable(markdown,DETAIL));
        for(var item:group.items()) {
            var row=itemRow(item,rows);if(row==null)throw invalid("Brak opisu: "+item.label());
            if(!item.interpreted()&&!row.get(1).equals(technical(item)))throw invalid("Zachowaj zweryfikowaną definicję: "+item.label());
            if(item.interpreted()&&!row.get(1).startsWith(item.change()+"."))throw invalid("Zachowaj kierunek zmiany: "+item.change()+". "+item.label());
        }
        if(group.structurallyVerified()&&rows.size()!=group.items().size())throw invalid("Dodatkowe obiekty wymagają nierozpoznanej operacji w źródłach.");
        for(var row:rows.values()) {
            if(row.get(1).contains(SQL_PENDING)||row.get(2).equals(PENDING))throw invalid("Uzupełnij opis przed opublikowaniem sekcji: "+row.get(0));
            if(!row.get(2).matches("(?s)^(Potwierdzone|Wniosek|Do wyjaśnienia):\\s+\\S.*"))throw invalid("Znaczenie funkcjonalne zaczynaj od Potwierdzone:, Wniosek: albo Do wyjaśnienia:, a następnie podaj opis.");
            if(!LINK.matcher(row.get(3)).find())throw invalid("Każdy obiekt wymaga krótkiego linku do odczytanego źródła.");
            validateLinks(row.get(3),knownSources);
        }
        validateLinks(markdown,knownSources);
    }
    private void validateLinks(String text,Set<String> known) {
        var allowed=new HashSet<>(sourceLinks(known).values());var matcher=LINK.matcher(text);
        while(matcher.find())if(!allowed.contains(matcher.group(2)))throw invalid("Odnośnik nie prowadzi do odczytanego źródła: "+matcher.group(1));
    }
    public void validateMeta(AnalysisReportMeta meta,Set<String> known) {
        if(meta!=null)for(var ref:meta.references())if(ref==null||!known.contains(ref.target()))throw invalid("Metadane mogą cytować tylko odczytane źródła.");
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
            try{validateSection(id,section.markdown(),evidence,known);validateMeta(section.meta(),known);}
            catch(IllegalArgumentException e){limits.add(section.title()+": "+e.getMessage());}
            limits.addAll(section.meta().gaps());limits.addAll(section.meta().visibilityLimits());
        }
        var coverage=new ArrayList<Coverage>();
        for(var group:groups) {
            var gaps=new ArrayList<String>();int documented=0;var section=sections.get(detailId(group));
            if(section==null)gaps.add("Nie zapisano szczegółów zmian.");
            else try {
                validateSection(section.id(),section.markdown(),evidence,known);
                var rows=uniqueRows(requiredTable(section.markdown(),DETAIL));documented=group.items().size();
                for(var item:group.items())if(itemRow(item,rows).get(2).startsWith("Do wyjaśnienia:"))gaps.add(item.label()+": "+itemRow(item,rows).get(2).substring(15).strip());
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

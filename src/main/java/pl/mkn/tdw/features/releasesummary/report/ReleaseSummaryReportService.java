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
    public static final String CONTRACT="release-summary-diff-report-v1";
    public static final List<String> DETAIL=List.of("Obiekt","Zmiana","Opis funkcjonalny");
    private static final List<String> TABLE_LIST=List.of("Tabela","Zakres zmian");
    private static final String SEMANTIC_LIMIT="Kompletna lista zmian Git nie jest dowodem pelnego rozpoznania skutkow bazodanowych. Raport jest analiza AI, nie wynikiem wykonania migracji ani kontroli stanu bazy.";
    private static final Pattern DETAIL_ID=Pattern.compile("DB_DETAIL_[A-Za-z0-9_]{1,100}");
    private static final Pattern LINK=Pattern.compile("\\[[^]]+]\\([^)]+\\)|https?://|#report-section-");
    private static final Pattern JARGON=Pattern.compile("(?iu)\\b(?:DAO|DTO|changelog\\w*|changeset\\w*|Liquibase|relativeToChangelogFile|logicalFilePath)\\b|(?:src/|\\.(?:java|ya?ml|xml|kt|tsx?)\\b)");
    private final ObjectMapper mapper;
    private final GitLabSettingsPort settings;
    public record Result(String resultContract,String status,boolean changeListComplete,int changedFiles,
                         int documentedTables,int readSources,String semanticCoverage,List<String> visibilityLimits) {
        public Result {
            if(!CONTRACT.equals(resultContract)||!Set.of("COMPLETED","PARTIAL","NO_CHANGES").contains(status)
                    ||changedFiles<0||documentedTables<0||readSources<0||!Set.of("AI_ASSESSED","NOT_APPLICABLE").contains(semanticCoverage))
                throw new IllegalArgumentException("Niepoprawny wynik diff-first.");
            visibilityLimits=List.copyOf(visibilityLimits);
        }
    }
    public String gitLabBaseUrl(){return Objects.toString(settings.getBaseUrl(),"").replaceFirst("/+$","").replaceFirst("/api/v4$","");}
    public List<String> sectionIds(ReleaseSummaryEvidence evidence){return List.of("OVERVIEW","TABLE_LIST","APPENDIX");}
    public boolean detailSection(String id){return id!=null&&DETAIL_ID.matcher(id).matches();}
    public void validateOrder(String id,int order,ReleaseSummaryEvidence evidence) {
        boolean valid="OVERVIEW".equals(id)?order==1:"TABLE_LIST".equals(id)?order==2:
                "APPENDIX".equals(id)?order==1_000_000:detailSection(id)&&order>=3&&order<1_000_000;
        if(!valid)throw invalid("Kolejnosc: OVERVIEW=1, TABLE_LIST=2, DB_DETAIL_*=3..999999, APPENDIX=1000000.");
    }
    public AnalysisReport scaffold(String id,ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        return new AnalysisReport(id,"Zmiany bazodanowe we wdrozeniu",scope.systemName(),"",List.of(),AnalysisReportMeta.empty());
    }
    public AnalysisReport noChanges(String id,ReleaseSummaryScope scope) {
        return new AnalysisReport(id,"Brak zmian w skonfigurowanym zakresie",scope.systemName(),"Nie wykryto zmian plikow pasujacych do skonfigurowanego filtra sciezek.",
                List.of(new AnalysisReportSection("OVERVIEW","Zakres porownania",1,
                        "Potwierdzono brak zmian pasujacych do skonfigurowanego filtra sciezek w zakresie repozytoriow. Nie oznacza to identycznych rewizji ani braku zmian bazodanowych poza filtrem. Nie ustalano stanu dzialajacej bazy.",AnalysisReportMeta.empty())),AnalysisReportMeta.empty());
    }
    public Set<String> initialSources(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var refs=new HashSet<String>();scope.entities().forEach(entity->refs.add("opctx:"+entity.get("type")+":"+entity.get("id")));return refs;
    }
    public Map<String,String> sourceLinks(Set<String> refs) {
        var links=new TreeMap<String,String>();refs.forEach(ref->links.put(ref,sourceLink(ref)));return links;
    }
    private String sourceLink(String ref) {
        if(ref.startsWith("opctx:")) {
            var parts=ref.substring(6).split(":",2);if(parts.length!=2)throw invalid("Niepoprawne zrodlo Operational Context.");
            return "/api/operational-context/entities/"+encode(parts[0])+"/"+encode(parts[1]);
        }
        var matcher=Pattern.compile("^gitlab:(.+)@([a-fA-F0-9]{40}|[a-fA-F0-9]{64}):(.+)$").matcher(ref);
        if(!matcher.matches())throw invalid("Zrodlo nie wskazuje odczytanego pliku na przypietej rewizji.");
        var base=gitLabBaseUrl();
        if(!base.startsWith("https://")&&!base.startsWith("http://"))throw invalid("Brak adresu GitLaba dla zrodel.");
        return base+"/"+encodePath(matcher.group(1))+"/-/blob/"+matcher.group(2)+"/"+encodePath(matcher.group(3));
    }
    private String encodePath(String value){return Arrays.stream(value.split("/",-1)).map(this::encode).collect(java.util.stream.Collectors.joining("/"));}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    public String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception exception){throw invalid("Nie mozna przygotowac JSON raportu.");}}
    public void validateMeta(AnalysisReportMeta meta,Set<String> known) {
        var allowed=new HashSet<>(sourceLinks(known).values());
        if(meta!=null)for(var ref:meta.references())if(ref==null||!allowed.contains(ref.target()))throw invalid("Metadane moga cytowac tylko URL rzeczywiscie odczytanego zrodla.");
    }
    public void validateSection(String id,String markdown,ReleaseSummaryEvidence evidence,Set<String> known) {
        if(markdown==null||markdown.isBlank())throw invalid("Sekcja wymaga tresci.");
        if(!sectionIds(evidence).contains(id)&&!detailSection(id))throw invalid("Nieznana sekcja raportu.");
        if(LINK.matcher(markdown).find())throw invalid("Zrodla umieszczaj tylko w meta.references.");
        if(markdown.contains("DO_UZUPELNIENIA_")||markdown.contains("Uzupełnij opis funkcjonalny.")||markdown.contains("| elementId |"))throw invalid("Nie publikuj placeholderow ani identyfikatorow parsera.");
        if("OVERVIEW".equals(id)&&JARGON.matcher(markdown).find())throw invalid("Podsumowanie powinno opisywac zmiany danych, bez zargonu implementacji.");
        if(detailSection(id)) {
            var table=requiredTable(markdown,DETAIL);if(table.rows().isEmpty())throw invalid("Opisz co najmniej jeden obiekt tabeli.");
            for(var row:table.rows())if(JARGON.matcher(row.get(2)).find()||Pattern.compile("(?iu)^(?:Potwierdzone|Wniosek|Do wyja[nśs]nienia)\\s*:").matcher(row.get(2)).find())throw invalid("Opis funkcjonalny nie moze byc opisem implementacji ani etykieta wniosku.");
            if(markdown.lines().anyMatch(line->!line.isBlank()&&!line.strip().startsWith("|")))throw invalid("Szczegoly i funkcje obiektow umiesc w komorkach tabeli, a ograniczenia w meta.");
        }
        if("TABLE_LIST".equals(id)&&!markdown.equals("Nie zidentyfikowano zmian tabel."))requiredTable(markdown,TABLE_LIST);
    }
    public void validatePublication(String id,String markdown,AnalysisReportMeta meta,ReleaseSummaryEvidence evidence,Set<String> known) {
        validateSection(id,markdown,evidence,known);validateMeta(meta,known);
        if(detailSection(id)) {
            var codeUrls=sourceLinks(known).entrySet().stream().filter(entry->entry.getKey().startsWith("gitlab:")).map(Map.Entry::getValue).collect(java.util.stream.Collectors.toSet());
            if(meta==null||meta.references().stream().noneMatch(ref->codeUrls.contains(ref.target())))throw invalid("Sekcja tabeli wymaga zrodla GitLab odczytanego przez narzedzie, nie samego patcha.");
        }
    }
    private ReleaseSummaryTables.Table requiredTable(String markdown,List<String> headers) {
        var tables=ReleaseSummaryTables.parse(markdown);
        if(tables.size()!=1||!tables.get(0).headers().equals(headers))throw invalid("Wymagana jedna tabela z kolumnami: "+headers);
        if(tables.get(0).rows().isEmpty())throw invalid("Tabela wymaga co najmniej jednego uzupelnionego wiersza.");
        var labels=new HashSet<String>();for(var row:tables.get(0).rows())if(row.stream().anyMatch(String::isBlank)||!labels.add(row.get(0)))throw invalid("Wypelnij komorki i nie powtarzaj obiektow.");
        return tables.get(0);
    }
    public void validateLayout(AnalysisReport report,ReleaseSummaryEvidence evidence) {
        var ids=new HashSet<String>();var orders=new HashSet<Integer>();
        for(var section:report.sections()) {
            validateOrder(section.id(),section.order(),evidence);
            if(!ids.add(section.id())||!orders.add(section.order()))throw invalid("Powtorzona sekcja lub kolejnosc.");
        }
        var index=report.sections().stream().filter(section->section.id().equals("TABLE_LIST")).findFirst();
        if(index.isPresent()) {
            var markdown=index.get().markdown();
            var listed=markdown.equals("Nie zidentyfikowano zmian tabel.")?Set.<String>of():requiredTable(markdown,TABLE_LIST).rows().stream().map(row->row.get(0)).collect(java.util.stream.Collectors.toSet());
            var details=report.sections().stream().filter(section->detailSection(section.id())).toList();
            var documented=details.stream().map(AnalysisReportSection::title).collect(java.util.stream.Collectors.toSet());
            if(!listed.equals(documented)||documented.size()!=details.size())throw invalid("Lista tabel i sekcje szczegolow nie sa zgodne.");
        }
    }
    public Result project(AnalysisReport report,ReleaseSummaryEvidence evidence,Set<String> known) {
        var problems=new LinkedHashSet<String>();var limits=new LinkedHashSet<String>();
        var sections=report==null?List.<AnalysisReportSection>of():report.sections();var ids=new HashSet<String>();var orders=new HashSet<Integer>();
        for(var section:sections) {
            if(!ids.add(section.id())||!orders.add(section.order()))problems.add("Powtorzona sekcja lub kolejnosc.");
            try{validateOrder(section.id(),section.order(),evidence);validatePublication(section.id(),section.markdown(),section.meta(),evidence,known);}
            catch(IllegalArgumentException exception){problems.add(exception.getMessage());}
            problems.addAll(section.meta().gaps());problems.addAll(section.meta().visibilityLimits());
        }
        var required=evidence.noChanges()?List.of("OVERVIEW"):sectionIds(evidence);
        if(!ids.containsAll(required))problems.add("Nie zapisano wszystkich wymaganych czesci raportu.");
        var details=sections.stream().filter(section->detailSection(section.id())).toList();
        if(!evidence.noChanges())sections.stream().filter(section->section.id().equals("TABLE_LIST")).findFirst().ifPresent(section->{
            try {
                var listed=section.markdown().equals("Nie zidentyfikowano zmian tabel.")?Set.<String>of():requiredTable(section.markdown(),TABLE_LIST).rows().stream().map(row->row.get(0)).collect(java.util.stream.Collectors.toSet());
                var documented=details.stream().map(AnalysisReportSection::title).collect(java.util.stream.Collectors.toSet());
                if(!listed.equals(documented)||documented.size()!=details.size())problems.add("Lista tabel i sekcje szczegolow nie sa zgodne.");
            }catch(IllegalArgumentException exception){problems.add(exception.getMessage());}
        });
        if(report!=null) {
            try{validateMeta(report.meta(),known);}catch(IllegalArgumentException exception){problems.add(exception.getMessage());}
            problems.addAll(report.meta().gaps());problems.addAll(report.meta().visibilityLimits());
        }
        evidence.gaps().stream().filter(ReleaseSummaryEvidence.Gap::blocking).forEach(gap->problems.add(gap.detail()));
        if(!evidence.changeListComplete())problems.add("Brak potwierdzonej kompletnej listy zmian.");
        limits.addAll(problems);if(!evidence.noChanges())limits.add(SEMANTIC_LIMIT);
        var status=problems.isEmpty()?evidence.noChanges()?"NO_CHANGES":"COMPLETED":"PARTIAL";
        return new Result(CONTRACT,status,evidence.changeListComplete(),evidence.changes().size(),details.size(),
                (int)known.stream().filter(ref->ref.startsWith("gitlab:")).count(),evidence.noChanges()?"NOT_APPLICABLE":"AI_ASSESSED",List.copyOf(limits));
    }
    private IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
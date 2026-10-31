package pl.mkn.tdw.features.releasesummary.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.shared.ai.report.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ReleaseSummaryReportService {
    private final ObjectMapper mapper;
    public static final List<String> TECH=List.of("elementId","Obiekt","Zmiana","Przed","Po","Opis techniczny","Źródła");
    public static final List<String> FUNCTION=List.of("elementId","Cel funkcjonalny","Działanie","Wpływ","Podstawa","Źródła","Luka");
    public static final Map<String,List<String>> HEADERS=Map.of(
            "OVERVIEW",List.of("Obszar","Opis","Źródła"),
            "CHANGE_INDEX",List.of("unitId","Plik","Zmiana","Repozytorium","Zakres","Szczegóły","Status"),
            "DEPLOYMENT_IMPACT",List.of("Obszar","Wpływ","Weryfikacja","Źródła"),
            "COVERAGE",List.of("unitId","Status","Ograniczenia"));
    public record Coverage(String unitId,int expectedElements,int documentedElements,boolean structurallyVerified,List<String> gaps) {}
    public record Result(String resultContract,String status,int expectedUnits,int documentedUnits,int expectedElements,
                         int documentedElements,boolean sourceComplete,List<Coverage> coverage,List<String> visibilityLimits) {}

    public List<String> sectionIds(ReleaseSummaryEvidence evidence) {
        var ids=new ArrayList<>(List.of("OVERVIEW","CHANGE_INDEX","DEPLOYMENT_IMPACT","COVERAGE"));
        evidence.units().forEach(u->ids.add("DB_DETAIL_"+u.unitId())); return List.copyOf(ids);
    }
    public AnalysisReport scaffold(String id,ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var sections=new ArrayList<AnalysisReportSection>();
        sections.add(section("OVERVIEW","Zakres wdrożenia",0,table("OVERVIEW",List.of(List.of("System",scope.systemName()!=null?scope.systemName():scope.systemId(),"[]")))));
        sections.add(section("CHANGE_INDEX","Indeks zmian",1,table("CHANGE_INDEX",evidence.units().stream().map(u->List.of(u.unitId(),u.path(),u.changeType(),u.repositoryId(),unitScope(u,evidence),detailLink(u.unitId()),"EXPECTED")).toList())));
        sections.add(section("DEPLOYMENT_IMPACT","Wpływ wdrożenia",2,table("DEPLOYMENT_IMPACT",List.of(List.of("Migracje","DO_UZUPELNIENIA","DO_UZUPELNIENIA","[]")))));
        sections.add(section("COVERAGE","Kompletność",3,table("COVERAGE",evidence.units().stream().map(u->List.of(u.unitId(),"EXPECTED",u.structurallyVerified()?"Brak potwierdzenia raportu":"AI_IDENTIFIED")).toList())));
        int order=4;
        for(var unit:evidence.units()) {
            var tech=new ArrayList<List<String>>(); var functional=new ArrayList<List<String>>();
            for(var element:unit.elements()) {
                var refs=sourceRefs(unit);
                tech.add(List.of(element.elementId(),element.kind()+" "+element.locator(),unit.changeType(),json(element.before()),json(element.after()),"DO_UZUPELNIENIA_"+element.elementId()+"_TECH",json(refs)));
                functional.add(List.of(element.elementId(),"BRAK_DANYCH","BRAK_DANYCH","BRAK_DANYCH","BRAK_DANYCH","[]","DO_UZUPELNIENIA_"+element.elementId()+"_FUNCTION"));
            }
            sections.add(section("DB_DETAIL_"+unit.unitId(),unit.repositoryId()+" / "+unit.path()+" — "+unitScope(unit,evidence),order++,
                    "### Zmiana techniczna\n\n"+ReleaseSummaryTables.table(TECH,tech)+"\n### Znaczenie funkcjonalne\n\n"+ReleaseSummaryTables.table(FUNCTION,functional)));
        }
        return new AnalysisReport(id,"Release summary",scope.systemName(),"Podsumowanie zmian Liquibase.",sections,AnalysisReportMeta.empty());
    }
    private AnalysisReportSection section(String id,String title,int order,String markdown) {
        return new AnalysisReportSection(id,title,order,markdown,AnalysisReportMeta.empty());
    }
    private String unitScope(ReleaseSummaryEvidence.Unit unit,ReleaseSummaryEvidence evidence) {
        if(evidence.units().stream().filter(u->u.repositoryId().equals(unit.repositoryId())&&u.path().equals(unit.path())).count()==1)return "Zmiany w pliku";
        var first=unit.elements().get(0);var last=unit.elements().get(unit.elements().size()-1);
        return first.locator()+ (unit.elements().size()>1?" … "+last.locator():"");
    }
    private String table(String id,List<List<String>> rows) {return ReleaseSummaryTables.table(HEADERS.get(id),rows);}
    private String detailLink(String unitId) {return "[Szczegóły](#report-section-DB_DETAIL_"+unitId+")";}
    public String json(Object value) {
        try {return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Nie można zapisać kontraktu raportu.",e);}
    }
    public Set<String> initialSources(ReleaseSummaryScope scope,ReleaseSummaryEvidence evidence) {
        var refs=new HashSet<String>(); evidence.sources().forEach(s->refs.add(s.sourceRef()));
        scope.entities().forEach(e->refs.add("opctx:"+e.get("type")+":"+e.get("id"))); return refs;
    }
    public List<String> sourceRefs(ReleaseSummaryEvidence.Unit unit) {
        var refs=new ArrayList<String>(); if(unit.baselineSource()!=null)refs.add(unit.baselineSource());
        if(unit.targetSource()!=null&&!refs.contains(unit.targetSource()))refs.add(unit.targetSource()); return refs;
    }
    public void validateSection(String id,String markdown,ReleaseSummaryEvidence evidence,Set<String> knownSources) {
        var tables=ReleaseSummaryTables.parse(markdown);
        if(HEADERS.containsKey(id)) {
            var table=requiredTable(tables,HEADERS.get(id));
            if(id.equals("CHANGE_INDEX")||id.equals("COVERAGE")) {
                var ids=uniqueRows(table);
                if(!ids.keySet().equals(new HashSet<>(evidence.units().stream().map(ReleaseSummaryEvidence.Unit::unitId).toList())))
                    throw invalid("Tabela musi zachować każdą unitId.");
                if(id.equals("CHANGE_INDEX")) for(var unit:evidence.units()) {
                    var row=ids.get(unit.unitId());
                    if(!row.get(1).equals(unit.path())||!row.get(2).equals(unit.changeType())||!row.get(3).equals(unit.repositoryId()))
                        throw invalid("Indeks musi zachować repozytorium, ścieżkę i typ zmiany.");
                    if(!row.get(5).equals(detailLink(unit.unitId())))throw invalid("Szczegóły muszą wskazywać "+detailLink(unit.unitId()));
                    validateCoverageStatus(unit,row.get(6));
                }
                if(id.equals("COVERAGE")) for(var unit:evidence.units()) {
                    var status=ids.get(unit.unitId()).get(1);
                    validateCoverageStatus(unit,status);
                }
            }
            validateSources(table,knownSources,false); return;
        }
        var unit=evidence.units().stream().filter(u->id.equals("DB_DETAIL_"+u.unitId())).findFirst().orElseThrow(()->invalid("Nieznana sekcja."));
        var tech=requiredTable(tables,TECH); var function=requiredTable(tables,FUNCTION);
        var technical=uniqueRows(tech); var functional=uniqueRows(function);
        if(!technical.keySet().equals(functional.keySet())) throw invalid("Obie tabele muszą dokumentować te same elementId.");
        for(var element:unit.elements()) {
            var row=technical.get(element.elementId());
            if(row==null) throw invalid("Brak elementId "+element.elementId());
            if(!parseJson(row.get(3)).equals(mapper.valueToTree(element.before()))
                    || !parseJson(row.get(4)).equals(mapper.valueToTree(element.after())))
                throw invalid("Przed i Po muszą zachować pełne, niezmienione JSON właściwości elementu "+element.elementId());
        }
        var expected=new HashSet<>(unit.elements().stream().map(ReleaseSummaryEvidence.Element::elementId).toList());
        for(var key:technical.keySet()) if(!expected.contains(key)&&!key.matches("AI_"+unit.unitId()+"_[0-9]+"))
            throw invalid("Dodatkowe elementy SQL muszą mieć elementId AI_"+unit.unitId()+"_<numer>.");
        for(var row:function.rows()) if(!Set.of("FAKT","WNIOSEK","BRAK_DANYCH").contains(row.get(4)))
            throw invalid("Element "+row.get(0)+": Podstawa wymaga jednej wartości FAKT, WNIOSEK albo BRAK_DANYCH; otrzymano: "+row.get(4));
        validateSources(tech,knownSources,true); validateSources(function,knownSources,false);
        for(var row:function.rows()) if(!row.get(4).equals("BRAK_DANYCH")&&parseJson(row.get(5)).isEmpty())
            throw invalid("Fakt lub wniosek wymaga źródeł.");
    }
    public void validateMeta(AnalysisReportMeta meta,Set<String> known) {
        if(meta!=null) for(var ref:meta.references()) if(ref==null||!known.contains(ref.target()))
            throw invalid("Reference wskazuje nieodczytane źródło.");
    }
    private void validateSources(ReleaseSummaryTables.Table table,Set<String> known,boolean required) {
        int index=table.headers().indexOf("Źródła"); if(index<0)return;
        for(var row:table.rows()) {
            var refs=parseJson(row.get(index));
            if(!refs.isArray()||required&&refs.isEmpty()) throw invalid("Źródła muszą być tablicą JSON odczytanych sourceRef.");
            for(var ref:refs) if(!ref.isTextual()||!known.contains(ref.asText())) throw invalid("Nieodczytane źródło: "+ref.asText());
        }
    }
    private com.fasterxml.jackson.databind.JsonNode parseJson(String value) {
        try {var result=mapper.readTree(value); if(result==null)throw invalid("Brak JSON.");return result;}
        catch(Exception e){throw invalid("Komórka wymaga poprawnego JSON: "+value);}
    }
    private ReleaseSummaryTables.Table requiredTable(List<ReleaseSummaryTables.Table> tables,List<String> headers) {
        var found=tables.stream().filter(t->t.headers().equals(headers)).toList();
        if(found.size()!=1) throw invalid("Wymagana dokładnie jedna tabela: "+headers);
        for(var row:found.get(0).rows()) if(row.stream().anyMatch(String::isBlank)) throw invalid("Każda komórka wymaga wartości lub jawnego BRAK_DANYCH.");
        return found.get(0);
    }
    private Map<String,List<String>> uniqueRows(ReleaseSummaryTables.Table table) {
        var values=new LinkedHashMap<String,List<String>>();
        for(var row:table.rows()) if(values.putIfAbsent(row.get(0),row)!=null) throw invalid("Powtórzony identyfikator: "+row.get(0));
        return values;
    }
    private IllegalArgumentException invalid(String text) {return new IllegalArgumentException(text);}
    private boolean pending(String value) {return value.matches("DO_UZUPELNIENIA(?:_[a-zA-Z0-9]+)*");}
    private boolean pendingTableContent(String markdown) {
        for(var table:ReleaseSummaryTables.parse(markdown))for(var row:table.rows())for(int i=0;i<row.size();i++)
            if(!Set.of("Przed","Po","Źródła").contains(table.headers().get(i))&&pending(row.get(i)))return true;
        return false;
    }
    private void validateCoverageStatus(ReleaseSummaryEvidence.Unit unit,String status) {
        if(!Set.of("EXPECTED","DOCUMENTED","AI_IDENTIFIED","OPEN_GAP").contains(status))
            throw invalid("Status coverage: EXPECTED, DOCUMENTED, AI_IDENTIFIED lub OPEN_GAP.");
        if(!unit.structurallyVerified()&&status.equals("DOCUMENTED"))throw invalid("Surowy SQL lub custom change pozostaje AI_IDENTIFIED.");
    }

    public Result project(AnalysisReport report,ReleaseSummaryEvidence evidence,Set<String> known) {
        var limits=new ArrayList<String>(); evidence.gaps().forEach(g->limits.add(g.code()+": "+g.detail()));
        var sections=new HashMap<String,AnalysisReportSection>();
        if(report!=null) for(var section:report.sections()) if(sections.put(section.id(),section)!=null) limits.add("Powtórzona sekcja "+section.id());
        for(var id:sectionIds(evidence)) {
            var section=sections.get(id);
            if(section==null) {limits.add("Brak sekcji "+id);continue;}
            try {validateSection(id,section.markdown(),evidence,known);validateMeta(section.meta(),known);}
            catch(IllegalArgumentException e){limits.add(id+": "+e.getMessage());}
            try {if(pendingTableContent(section.markdown()))limits.add(id+": sekcja wymaga uzupełnienia.");}
            catch(IllegalArgumentException e){ /* The malformed table is already recorded above. */ }
            if(section.meta()!=null) {limits.addAll(section.meta().gaps());limits.addAll(section.meta().visibilityLimits());}
            if(id.equals("COVERAGE")) try {
                var rows=requiredTable(ReleaseSummaryTables.parse(section.markdown()),HEADERS.get(id)).rows();
                if(rows.stream().anyMatch(r->r.get(1).equals("EXPECTED")||r.get(1).equals("OPEN_GAP")))limits.add("COVERAGE: niezamknięte jednostki.");
            } catch(IllegalArgumentException e) { /* The structural error is already recorded above. */ }
        }
        if(report!=null) {try{validateMeta(report.meta(),known);}catch(IllegalArgumentException e){limits.add(e.getMessage());}
            if(report.meta()!=null){limits.addAll(report.meta().gaps());limits.addAll(report.meta().visibilityLimits());}}
        if(sections.containsKey("CHANGE_INDEX")&&sections.containsKey("COVERAGE"))try {
            var index=uniqueRows(requiredTable(ReleaseSummaryTables.parse(sections.get("CHANGE_INDEX").markdown()),HEADERS.get("CHANGE_INDEX")));
            var declared=uniqueRows(requiredTable(ReleaseSummaryTables.parse(sections.get("COVERAGE").markdown()),HEADERS.get("COVERAGE")));
            for(var unit:evidence.units())if(index.containsKey(unit.unitId())&&declared.containsKey(unit.unitId())
                    &&!index.get(unit.unitId()).get(6).equals(declared.get(unit.unitId()).get(1)))limits.add("Niespójny status indeksu i coverage: "+unit.unitId());
        }catch(IllegalArgumentException e){ /* The malformed table is already recorded above. */ }
        var coverage=new ArrayList<Coverage>();
        for(var unit:evidence.units()) {
            var gaps=new ArrayList<String>(); int count=0;
            var section=sections.get("DB_DETAIL_"+unit.unitId());
            if(section!=null) try {
                var tables=ReleaseSummaryTables.parse(section.markdown()); var technical=uniqueRows(requiredTable(tables,TECH));
                var functional=uniqueRows(requiredTable(tables,FUNCTION));
                for(var element:unit.elements()) {
                    var a=technical.get(element.elementId()); var b=functional.get(element.elementId());
                    if(a!=null&&b!=null&&!pending(a.get(5))&&b.subList(1,b.size()).stream().noneMatch(this::pending)) count++;
                    else gaps.add("Nieudokumentowany "+element.elementId());
                    if(b!=null&&b.get(4).equals("BRAK_DANYCH")) gaps.add("Brak uzasadnienia funkcjonalnego "+element.elementId());
                }
                validateSection(section.id(),section.markdown(),evidence,known);
            } catch(IllegalArgumentException e){gaps.add(e.getMessage());count=0;}
            else gaps.add("Brak sekcji szczegółowej.");
            if(!unit.structurallyVerified()) gaps.add("AI_IDENTIFIED: strukturalna pełność SQL/danych/custom change nie jest potwierdzona deterministycznie.");
            coverage.add(new Coverage(unit.unitId(),unit.elements().size(),count,unit.structurallyVerified(),List.copyOf(gaps)));
            limits.addAll(gaps.stream().map(g->unit.unitId()+": "+g).toList());
        }
        int expected=evidence.units().stream().mapToInt(u->u.elements().size()).sum();
        int documented=coverage.stream().mapToInt(Coverage::documentedElements).sum();
        var status=limits.isEmpty()&&evidence.sourceComplete()?(evidence.noChanges()?"NO_CHANGES":"COMPLETED"):"PARTIAL";
        return new Result("release-summary-result-v1",status,evidence.units().size(),
                (int)coverage.stream().filter(c->c.expectedElements()==c.documentedElements()).count(),expected,documented,
                evidence.sourceComplete(),coverage,List.copyOf(new LinkedHashSet<>(limits)));
    }
}


package pl.mkn.tdw.features.releasesummary.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.releasesummary.context.LiquibaseDocumentParser;
import pl.mkn.tdw.features.releasesummary.context.LiquibaseDocumentParser.Node;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Reader-facing inventory. The complete parser manifest remains in evidence, never in report cells. */
@Component
@RequiredArgsConstructor
public class ReleaseSummaryReportInventory {
    private final LiquibaseDocumentParser parser;
    private static final Set<String> CONTAINERS=Set.of("databaseChangeLog","changeSet","changes","columns","rollback");
    private static final Set<String> COMPOUND=Set.of("createIndex","dropIndex","addPrimaryKey","dropPrimaryKey",
            "addUniqueConstraint","dropUniqueConstraint","addForeignKeyConstraint","dropForeignKeyConstraint",
            "insert","update","delete","loadData","loadUpdateData","customChange","createProcedure","createView","preConditions","preconditions");
    private static final Set<String> INTERPRETED=Set.of("sql","sqlFile","data","dependency","ordering","rollback",
            "customChange","createProcedure","createView","modifySql","executeCommand");
    public record Item(String label,String change,String definition,boolean interpreted,List<String> elementIds,
                       List<String> sources) {}
    public record Group(String id,String title,String repositoryId,String path,List<Item> items,boolean structurallyVerified) {}

    public List<Group> groups(ReleaseSummaryEvidence evidence) {
        var files=new TreeMap<String,List<ReleaseSummaryEvidence.Unit>>();
        evidence.units().forEach(u->files.computeIfAbsent(u.repositoryId()+"/"+u.path(),ignored->new ArrayList<>()).add(u));
        var result=new ArrayList<Group>();
        for(var entry:files.entrySet()) {
            var units=entry.getValue();var first=units.get(0);
            var before=nodes(evidence,first,"BASELINE");var after=nodes(evidence,first,"TARGET");
            var elements=new LinkedHashMap<String,ReleaseSummaryEvidence.Element>();
            units.forEach(u->u.elements().forEach(e->elements.put(e.locator(),e)));
            // Synthetic test/input manifests may carry a changed node without a source tree.
            elements.values().forEach(e->{
                if(e.before()!=null)before.putIfAbsent(e.locator(),new Node(e.kind(),e.before(),List.of()));
                if(e.after()!=null)after.putIfAbsent(e.locator(),new Node(e.kind(),e.after(),List.of()));
            });
            var all=new LinkedHashMap<String,Node>(before);all.putAll(after);
            var owners=new LinkedHashMap<String,List<ReleaseSummaryEvidence.Element>>();
            for(var element:elements.values()) {
                var owner=owner(element,all);
                if(owner!=null)owners.computeIfAbsent(owner,ignored->new ArrayList<>()).add(element);
            }
            if(owners.isEmpty()) owners.put("configuration",new ArrayList<>(elements.values()));
            var refs=new LinkedHashSet<String>();
            units.forEach(u->{if(u.baselineSource()!=null)refs.add(u.baselineSource());if(u.targetSource()!=null)refs.add(u.targetSource());});
            var items=new ArrayList<Item>();var labels=new HashMap<String,Integer>();var tables=new LinkedHashSet<String>();
            for(var owned:owners.entrySet()) {
                var path=owned.getKey();var b=before.get(path);var a=after.get(path);var node=a!=null?a:b;
                var kind=node!=null?node.kind():owned.getValue().get(0).kind();
                var attributes=node!=null?node.attributes():Map.<String,Object>of();
                var table=tableName(path,all);if(!table.isBlank())tables.add(table);
                boolean rollback=path.contains("/rollback:");
                var label=(rollback?"Wycofanie: ":"")+label(kind,attributes,table,items.size()+1);
                int duplicate=labels.merge(label,1,Integer::sum);if(duplicate>1)label+=" (operacja "+duplicate+")";
                boolean interpreted=INTERPRETED.contains(kind)||node==null;
                String change=first.changeType().equals("REMOVED")?"Usunięcie z changelogu":
                        b==null?"Dodanie":a==null?"Usunięcie definicji":"Zmiana";
                var definition=interpreted?"":definition(b,a,kind);
                items.add(new Item(label,change,definition,interpreted,
                        owned.getValue().stream().map(ReleaseSummaryEvidence.Element::elementId).toList(),List.copyOf(refs)));
            }
            String title=tables.isEmpty()?"Reguły i operacje migracji":tables.size()==1?"Tabela "+tables.iterator().next():"Tabele "+String.join(", ",tables);
            // A human filename disambiguates migrations of the same table, without parser paths or hashes.
            title+=" · "+first.path().substring(first.path().lastIndexOf('/')+1);
            result.add(new Group(id(entry.getKey()),title,first.repositoryId(),first.path(),List.copyOf(items),units.stream().allMatch(ReleaseSummaryEvidence.Unit::structurallyVerified)));
        }
        return List.copyOf(result);
    }

    private Map<String,Node> nodes(ReleaseSummaryEvidence evidence,ReleaseSummaryEvidence.Unit unit,String side) {
        var source=evidence.sources().stream().filter(s->s.repositoryId().equals(unit.repositoryId())&&s.path().equals(unit.path())&&s.side().equals(side)).findFirst();
        return source.map(s->new LinkedHashMap<>(parser.flatten(parser.parse(s.path(),s.content())))).orElseGet(LinkedHashMap::new);
    }
    private String owner(ReleaseSummaryEvidence.Element element,Map<String,Node> nodes) {
        var path=element.locator();
        if(element.kind().equals("dependency"))return path;
        if(element.kind().equals("addColumn")&&nodes.keySet().stream().anyMatch(p->p.startsWith(path+"/")&&nodes.get(p).kind().equals("column"))) {
            var fields=new HashSet<String>();if(element.before()!=null)fields.addAll(element.before().keySet());if(element.after()!=null)fields.addAll(element.after().keySet());
            if(Set.of("tableName","schemaName","catalogName").containsAll(fields))return null;
        }
        for(var ancestor:ancestors(path)) {
            var node=nodes.get(ancestor);
            if(node!=null&&COMPOUND.contains(node.kind()))return ancestor;
        }
        if(element.kind().equals("constraints"))for(var ancestor:ancestors(path)) {
            var node=nodes.get(ancestor);if(node!=null&&node.kind().equals("column"))return ancestor;
        }
        if(CONTAINERS.contains(element.kind())) {
            var before=element.before();var after=element.after();
            if((before==null||before.isEmpty())&&(after==null||after.isEmpty()))return null;
            if(element.kind().equals("changeSet")&&(before==null||after==null)) {
                var attrs=after!=null?after:before;
                if(attrs.keySet().stream().allMatch(Set.of("id","author","comment")::contains))return null;
            }
        }
        return path;
    }
    private List<String> ancestors(String path) {
        var result=new ArrayList<String>();
        for(var current=path;!current.isBlank();) {
            result.add(current);int slash=current.lastIndexOf('/');if(slash<0)break;current=current.substring(0,slash);
        }
        return result;
    }
    private String tableName(String path,Map<String,Node> nodes) {
        for(var ancestor:ancestors(path)) {
            var node=nodes.get(ancestor);if(node==null)continue;
            for(var key:List.of("tableName","baseTableName","oldTableName"))if(node.attributes().get(key)!=null) {
                var schema=node.attributes().get("schemaName");
                return (schema!=null?schema+".":"")+node.attributes().get(key);
            }
        }
        return "";
    }
    private String label(String kind,Map<String,Object> attributes,String table,int ordinal) {
        var name=Objects.toString(attributes.getOrDefault("name",attributes.getOrDefault("columnName",attributes.getOrDefault("indexName",attributes.getOrDefault("constraintName","")))),"");
        var qualified=table.isBlank()?name:name.isBlank()?table:table+"."+name;
        return switch(kind) {
            case "column","addColumn","dropColumn","renameColumn","modifyDataType" -> "Kolumna "+qualified;
            case "createTable","dropTable","renameTable" -> "Tabela "+table;
            case "createIndex","dropIndex" -> "Indeks "+qualified;
            case "addForeignKeyConstraint","dropForeignKeyConstraint" -> "Klucz obcy "+qualified;
            case "addPrimaryKey","dropPrimaryKey" -> "Klucz główny "+qualified;
            case "addUniqueConstraint","dropUniqueConstraint" -> "Unikalność "+qualified;
            case "createSequence","dropSequence","alterSequence" -> "Sekwencja "+Objects.toString(attributes.get("sequenceName"),name);
            case "include","includeAll" -> "Dołączenie "+Objects.toString(attributes.get("file"),Objects.toString(attributes.get("path"),"migracji"));
            case "ordering" -> "Kolejność operacji";
            case "preConditions","preconditions" -> "Warunki uruchomienia migracji";
            case "dependency" -> "Zależności migracji";
            case "databaseChangeLog","changeSet","changes","columns" -> "Ustawienia migracji";
            case "sql","sqlFile" -> "Operacja SQL "+ordinal;
            case "insert","update","delete","loadData","loadUpdateData" -> "Dane w tabeli "+table+" ("+kind+")";
            default -> kind+" "+(qualified.isBlank()?Integer.toString(ordinal):qualified);
        };
    }
    private String definition(Node before,Node after,String kind) {
        if(before==null)return describe(after,kind);
        if(after==null)return describe(before,kind);
        var b=describe(before,kind);var a=describe(after,kind);
        return b.equals(a)?a:"Przed: "+b+"<br>Po: "+a;
    }
    private String describe(Node node,String kind) {
        if(node==null)return "Nie występuje";
        var parts=new ArrayList<String>();
        node.attributes().forEach((key,value)->{
            if(!Set.of("name","tableName","columnName","indexName","remarks","comment").contains(key))
                parts.add(property(key,value));
        });
        if(kind.equals("column")) {
            node.children().forEach(child->parts.add(describe(child,child.kind())));
            if(node.children().stream().noneMatch(c->c.attributes().containsKey("nullable")))parts.add("NULL: nie określono");
            if(node.attributes().keySet().stream().noneMatch(k->k.startsWith("defaultValue")))parts.add("wartość domyślna: nie określono");
        } else if(kind.equals("createIndex")) {
            var columns=new ArrayList<Node>();
            for(var child:node.children()) {
                if(child.kind().equals("columns"))columns.addAll(child.children());
                else if(child.kind().equals("column"))columns.add(child);
                else parts.add(nested(child));
            }
            if(!columns.isEmpty())parts.add("kolumny w kolejności: "+columns.stream().map(this::indexColumn).collect(Collectors.joining(", ")));
        } else if(COMPOUND.contains(kind)) {
            node.children().forEach(child->parts.add(nested(child)));
        }
        return parts.isEmpty()?"Definicja obiektu w migracji":String.join("; ",parts);
    }
    private String nested(Node node) {
        var values=new ArrayList<String>();node.attributes().forEach((k,v)->values.add(property(k,v)));
        node.children().forEach(c->values.add(nested(c)));
        return node.kind()+": "+String.join(", ",values);
    }
    private String indexColumn(Node node) {
        var result=Objects.toString(node.attributes().get("name"),"wyrażenie");var extra=new ArrayList<String>();
        node.attributes().forEach((k,v)->{if(!k.equals("name"))extra.add(property(k,v));});
        node.children().forEach(c->extra.add(nested(c)));
        return result+(extra.isEmpty()?"":" ("+String.join("; ",extra)+")");
    }
    private String property(String key,Object value) {
        if(key.equals("nullable"))return Boolean.parseBoolean(Objects.toString(value))?"NULL dozwolony":"NOT NULL";
        if(key.equals("primaryKey"))return Boolean.parseBoolean(Objects.toString(value))?"klucz główny":"bez klucza głównego";
        if(key.equals("unique"))return Boolean.parseBoolean(Objects.toString(value))?"unikalny":"nieunikalny";
        var label=switch(key){case "type"->"typ";case "defaultValue","defaultValueNumeric","defaultValueBoolean","defaultValueDate"->"wartość domyślna";case "defaultValueComputed"->"domyślne wyrażenie";
            case "autoIncrement"->"automatyczna numeracja";case "referencedTableName"->"tabela referencjonowana";
            case "referencedColumnNames"->"kolumny referencjonowane";case "columnNames"->"kolumny";case "onDelete"->"przy usunięciu";
            case "onUpdate"->"przy aktualizacji";case "schemaName"->"schemat";case "catalogName"->"katalog";
            case "primaryKeyName"->"nazwa klucza głównego";case "uniqueConstraintName"->"nazwa ograniczenia unikalności";
            case "foreignKeyName"->"nazwa klucza obcego";case "descending"->"sortowanie malejące";default->key;};
        return label+": "+text(value);
    }
    private String text(Object value) {
        if(value instanceof Map<?,?> map)return map.entrySet().stream().map(e->e.getKey()+": "+text(e.getValue())).collect(Collectors.joining(", "));
        if(value instanceof Collection<?> list)return list.stream().map(this::text).collect(Collectors.joining(", "));
        return Objects.toString(value,"nie określono").replace("\r","").replace("\n"," ");
    }
    private String id(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,16);}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}

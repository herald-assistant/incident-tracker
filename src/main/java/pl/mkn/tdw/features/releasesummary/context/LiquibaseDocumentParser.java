package pl.mkn.tdw.features.releasesummary.context;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

/** Structural discovery only. Never evaluates properties, SQL, scripts or custom changes. */
@Component
public class LiquibaseDocumentParser {
    public record Node(String kind,Map<String,Object> attributes,List<Node> children) {}
    public record Parsed(boolean changelog,List<Node> roots,boolean structured,List<String> limits) {}
    private static final Set<String> UNVERIFIED=Set.of("sql","sqlFile","loadData","loadUpdateData","customChange",
            "executeCommand","createProcedure","createView","modifySql");
    private static final Set<String> KNOWN=Set.of("databaseChangeLog","changeSet","changes","columns","column","constraints",
            "include","includeAll","property","preConditions","preconditions","onFail","onError","and","or","not",
            "dbms","runningAs","tableExists","columnExists","indexExists","sequenceExists","foreignKeyConstraintExists",
            "primaryKeyExists","uniqueConstraintExists","sqlCheck","changeLogPropertyDefined","changeSetExecuted",
            "rollback","validCheckSum","comment","createTable","dropTable","renameTable","addColumn","dropColumn",
            "renameColumn","modifyDataType","addDefaultValue","dropDefaultValue","addNotNullConstraint","dropNotNullConstraint",
            "addPrimaryKey","dropPrimaryKey","addUniqueConstraint","dropUniqueConstraint","addForeignKeyConstraint",
            "dropForeignKeyConstraint","dropAllForeignKeyConstraints","createIndex","dropIndex","createSequence",
            "alterSequence","dropSequence","renameSequence","insert","update","delete","where","whereParams","param",
            "addAutoIncrement","addLookupTable","createView","dropView","renameView","createProcedure","dropProcedure",
            "createFunction","dropFunction","createTrigger","dropTrigger","createPackage","createPackageBody",
            "setTableRemarks","setColumnRemarks","tagDatabase","stop","empty","output","sql","sqlFile","loadData",
            "loadUpdateData","modifySql","append","prepend","replace","regExpReplace","customChange","executeCommand");
    private static final Pattern SQL_INCLUDE=Pattern.compile("(?im)^\\s*--\\s*(includeAll|include|rollbackSqlFile)\\s+(.+)$");
    private static final Pattern SQL_ARGUMENT=Pattern.compile("(\\w+):(?:\"([^\"]*)\"|'([^']*)'|(\\S+))");

    public Parsed parse(String path,String content) {
        if(content.toLowerCase(Locale.ROOT).contains("--liquibase formatted sql")
                || content.toLowerCase(Locale.ROOT).contains("-- liquibase formatted sql")) {
            var nodes=new ArrayList<Node>();
            nodes.add(new Node("sql",Map.of("text",content),List.of()));
            var matcher=SQL_INCLUDE.matcher(content);
            while(matcher.find()) {
                var attrs=new LinkedHashMap<String,Object>();
                var args=SQL_ARGUMENT.matcher(matcher.group(2));
                while(args.find()) attrs.put(args.group(1), args.group(2)!=null?args.group(2):args.group(3)!=null?args.group(3):args.group(4));
                nodes.add(new Node(matcher.group(1),attrs,List.of()));
            }
            return new Parsed(true,nodes,false,List.of("RAW_SQL_REQUIRES_AI"));
        }
        if(path.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            if(!content.contains("databaseChangeLog")) return new Parsed(false,List.of(),false,List.of());
            try {
                var factory=DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                var root=factory.newDocumentBuilder().parse(new InputSource(new StringReader(content))).getDocumentElement();
                if(!"databaseChangeLog".equals(root.getLocalName())) return new Parsed(false,List.of(),false,List.of());
                return result(List.of(xml(root)));
            } catch(Exception e) { throw new IllegalArgumentException("Nie można bezpiecznie odczytać changelogu XML.",e); }
        }
        if(!content.contains("databaseChangeLog")) return new Parsed(false,List.of(),false,List.of());
        try {
            var options=new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(50);
            options.setCodePointLimit(Math.max(content.length()+1,3_000_000));
            var value=new Yaml(new SafeConstructor(options)).load(content);
            if(!(value instanceof Map<?,?> map) || !map.containsKey("databaseChangeLog"))
                return new Parsed(false,List.of(),false,List.of());
            return result(List.of(new Node("databaseChangeLog",Map.of(),nodes(map.get("databaseChangeLog")))));
        } catch(RuntimeException e) { throw new IllegalArgumentException("Nie można odczytać changelogu YAML/JSON.",e); }
    }
    private Parsed result(List<Node> nodes) {
        var limits=new LinkedHashSet<String>(); collectLimits(nodes,limits);
        return new Parsed(true,List.copyOf(nodes),limits.isEmpty(),List.copyOf(limits));
    }
    private void collectLimits(List<Node> nodes,Set<String> limits) {
        for(var n:nodes) {
            if(UNVERIFIED.contains(n.kind())) limits.add("AI_IDENTIFIED:"+n.kind());
            if(!KNOWN.contains(n.kind())) limits.add("UNSUPPORTED_CHANGE:"+n.kind());
            if(n.attributes().toString().contains("${")) limits.add("UNRESOLVED_PROPERTY");
            if(n.attributes().containsKey("filter") || n.attributes().containsKey("resourceComparator")) limits.add("CUSTOM_INCLUDE_FILTER");
            collectLimits(n.children(),limits);
        }
    }
    private Node xml(Element e) {
        var attrs=new LinkedHashMap<String,Object>(); var children=new ArrayList<Node>(); var text=new StringBuilder();
        for(int i=0;i<e.getAttributes().getLength();i++) {
            var a=e.getAttributes().item(i);
            if(!a.getNodeName().startsWith("xmlns")) attrs.put(a.getNodeName(),a.getNodeValue());
        }
        for(int i=0;i<e.getChildNodes().getLength();i++) {
            var child=e.getChildNodes().item(i);
            if(child instanceof Element el) children.add(xml(el));
            else if(child.getNodeType()==org.w3c.dom.Node.TEXT_NODE || child.getNodeType()==org.w3c.dom.Node.CDATA_SECTION_NODE)
                text.append(child.getNodeValue());
        }
        if(!text.toString().isBlank()) attrs.put("text",text.toString());
        var namespace=e.getNamespaceURI();
        var kind=namespace==null||namespace.isBlank()||namespace.equals("http://www.liquibase.org/xml/ns/dbchangelog")?
                e.getLocalName():"extension:"+e.getTagName();
        return new Node(kind,Collections.unmodifiableMap(attrs),List.copyOf(children));
    }
    private List<Node> nodes(Object input) {
        var result=new ArrayList<Node>();
        if(input instanceof List<?> list) list.forEach(v -> result.addAll(nodes(v)));
        else if(input instanceof Map<?,?> map) map.forEach((key,value)-> {
            if(value instanceof Map<?,?> fields) {
                var attrs=new LinkedHashMap<String,Object>(); var children=new ArrayList<Node>();
                fields.forEach((k,v)-> { if(v instanceof Map<?,?> || v instanceof List<?>) children.addAll(nodes(Map.of(k,v)));
                    else attrs.put(k.toString(),v); });
                result.add(new Node(key.toString(),Collections.unmodifiableMap(attrs),List.copyOf(children)));
            } else if(value instanceof List<?> list) result.add(new Node(key.toString(),Map.of(),nodes(list)));
            else result.add(new Node(key.toString(),Map.of("value",value==null?"":value),List.of()));
        });
        return List.copyOf(result);
    }
    public Map<String,Node> flatten(Parsed parsed) {
        var result=new LinkedHashMap<String,Node>(); flatten(parsed.roots(),"",result); return result;
    }
    private void flatten(List<Node> nodes,String parent,Map<String,Node> result) {
        var counts=new HashMap<String,Integer>();
        for(var node:nodes) {
            var identity=node.kind()+":"+List.of("id","name","columnName","indexName","tableName").stream()
                    .filter(node.attributes()::containsKey).map(k->Objects.toString(node.attributes().get(k),""))
                    .findFirst().orElse("");
            var locator=parent+"/"+identity+"#"+counts.merge(identity,1,Integer::sum);
            result.put(locator,node); flatten(node.children(),locator,result);
        }
    }
    public List<String> childOrder(Node node) {
        var counts=new HashMap<String,Integer>();var result=new ArrayList<String>();
        for(var child:node.children()) {
            var key=child.kind()+":"+List.of("id","name","columnName","indexName","tableName").stream()
                    .filter(child.attributes()::containsKey).map(k->Objects.toString(child.attributes().get(k),""))
                    .findFirst().orElse("");
            result.add(key+"#"+counts.merge(key,1,Integer::sum));
        }
        return List.copyOf(result);
    }
}


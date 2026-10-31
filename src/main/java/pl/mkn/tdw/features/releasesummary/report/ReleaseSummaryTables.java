package pl.mkn.tdw.features.releasesummary.report;

import java.util.*;

/** The report contract uses pipe-delimited GFM tables, with escaped literal pipes. */
public final class ReleaseSummaryTables {
    private ReleaseSummaryTables() {}
    public record Table(List<String> headers,List<List<String>> rows) {}
    public static List<Table> parse(String markdown) {
        var result=new ArrayList<Table>(); var lines=Objects.toString(markdown,"").lines().toList();
        boolean fenced=false;
        for(int i=0;i<lines.size();i++) {
            var line=lines.get(i).strip();
            if(line.startsWith("```")||line.startsWith("~~~")) {fenced=!fenced; continue;}
            if(fenced||!line.startsWith("|")||i+1>=lines.size()) continue;
            var headers=cells(line); var separator=cells(lines.get(i+1).strip());
            if(headers.isEmpty()||separator.size()!=headers.size()||separator.stream().anyMatch(v->!v.matches(":?-{3,}:?"))) continue;
            var rows=new ArrayList<List<String>>(); i++;
            while(i+1<lines.size()&&lines.get(i+1).strip().startsWith("|")) {
                var row=cells(lines.get(++i).strip());
                if(row.size()!=headers.size()) throw new IllegalArgumentException("Liczba komórek tabeli jest niezgodna z nagłówkiem.");
                rows.add(row);
            }
            result.add(new Table(List.copyOf(headers),List.copyOf(rows)));
        }
        return List.copyOf(result);
    }
    private static List<String> cells(String line) {
        if(!line.startsWith("|")||!line.endsWith("|")) return List.of();
        var cells=new ArrayList<String>(); var cell=new StringBuilder();
        for(int i=1;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='\\'&&i+1<line.length()&&(line.charAt(i+1)=='|'||line.charAt(i+1)=='\\')) {
                char next=line.charAt(++i); cell.append(next); if(next=='\\') cell.append(next); continue;
            }
            if(c=='|') {cells.add(unquote(cell.toString().strip()));cell.setLength(0);}
            else cell.append(c);
        }
        return cells;
    }
    private static String unquote(String value) {
        return value.length()>1&&value.startsWith("`")&&value.endsWith("`")?value.substring(1,value.length()-1):value;
    }
    public static String cell(String value) { return Objects.toString(value,"").replace("|","\\|").replace("\r","").replace("\n","<br>"); }
    public static String table(List<String> headers,List<List<String>> rows) {
        var out=new StringBuilder("| ").append(String.join(" | ",headers)).append(" |\n| ");
        out.append(String.join(" | ",Collections.nCopies(headers.size(),"---"))).append(" |\n");
        rows.forEach(row->out.append("| ").append(String.join(" | ",row.stream().map(ReleaseSummaryTables::cell).toList())).append(" |\n"));
        return out.toString();
    }
}


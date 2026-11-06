package pe.edu.ucsm.gdi;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/** Codifica los parámetros JSONB; no introduce un framework ni convierte decimales a double. */
final class Json {
    private Json() { }
    static String encode(Object value) {
        if(value==null)return "null";
        if(value instanceof String s)return quote(s);
        if(value instanceof BigDecimal n)return n.toPlainString();
        if(value instanceof Number || value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map)return map.entrySet().stream()
            .map(e->quote(e.getKey().toString())+":"+encode(e.getValue())).collect(Collectors.joining(",","{","}"));
        if(value instanceof Iterable<?> items){
            StringBuilder out=new StringBuilder("[");
            for(Object item:items){if(out.length()>1)out.append(',');out.append(encode(item));}
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Valor no compatible con JSON: "+value.getClass().getSimpleName());
    }
    private static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");
        for(char c:text.toCharArray())switch(c){
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");
            default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
        }
        return out.append('"').toString();
    }
}

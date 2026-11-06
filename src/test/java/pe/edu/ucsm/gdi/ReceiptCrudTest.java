package pe.edu.ucsm.gdi;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRUD de comprobantes observado desde la misma interfaz que usa el editor. */
public final class ReceiptCrudTest {
    private static int checks;
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    @FunctionalInterface interface SqlAction{void run() throws Exception;}
    private static void rejects(String state,SqlAction action) throws Exception {
        try{action.run();throw new AssertionError("Se esperaba "+state);}catch(SQLException e){check(state.equals(e.getSQLState()),"Rechazo "+state);}
    }
    private static Map<String,Object> header(int number){
        Map<String,Object> h=new LinkedHashMap<>();h.put("serie","F001");h.put("numero",number);h.put("fecha_emision","2026-11-05");
        h.put("codigo_moneda","PEN");h.put("op_gravada",new BigDecimal("100.00"));h.put("igv",new BigDecimal("18.00"));
        h.put("importe_total",new BigDecimal("118.00"));h.put("observacion","Prueba \"comillas\"\nsegunda línea \\ exacta");return h;
    }
    private static Map<String,Object> line(String code){
        return Map.of("codigo",code,"cantidad",new BigDecimal("1.00"),"valor_unitario",new BigDecimal("100.0000001"),"importe_venta",new BigDecimal("118.0000001"));
    }
    public static void main(String[] args) throws Exception {
        GdiRepository repo=new GdiRepository(DbConfig.load(Path.of(args[0])));
        int number=99000001;
        Map<String,Object> h=header(number);var lines=List.of(line("349"));
        List<Map<String,Object>> refs=List.of(Map.of("tipo_relacionado","Cotización","numero_relacionado","TEST-CRUD-70"));
        repo.guardarComprobante(h,lines,refs,true);
        check(repo.ficha("F001",number).value(0,"observacion").equals(h.get("observacion")),"Alta completa y JSON preserva comillas, saltos y barras");
        check(new BigDecimal("118.0000001").compareTo((BigDecimal)repo.detalle("F001",number).value(0,"importe_venta"))==0,"Alta conserva siete decimales");
        check(repo.referencias("F001",number).rows().size()==1,"Referencia creada en la misma transacción");
        rejects("23505",()->repo.guardarComprobante(h,lines,refs,true));
        Map<String,Object> bad=header(number+1);
        rejects("23503",()->repo.guardarComprobante(bad,List.of(line("INEXISTENTE")),refs,true));
        check(repo.ficha("F001",number+1).rows().isEmpty(),"Error de línea revierte también la cabecera");
        rejects("23514",()->repo.guardarComprobante(header(number+2),List.of(),List.of(),true));
        rejects("23505",()->repo.guardarComprobante(header(number+3),List.of(line("349"),line("349")),List.of(),true));
        check(repo.ficha("F001",number+3).rows().isEmpty(),"Producto repetido no deja cabecera parcial");
        h.put("version",1);h.put("observacion","EDITADO");
        repo.guardarComprobante(h,List.of(line("349"),line("350")),List.of(),false);
        check(repo.detalle("F001",number).rows().size()==2,"Edición reemplaza detalle de forma atómica");
        check(repo.referencias("F001",number).rows().isEmpty(),"Edición permite quitar referencias");
        check(((Number)repo.ficha("F001",number).value(0,"version")).intValue()==2,"Edición incrementa versión");
        rejects("40001",()->repo.guardarComprobante(h,lines,refs,false));
        h.put("version",2);h.put("observacion","CAMBIO QUE DEBE REVERTIRSE");
        rejects("23503",()->repo.guardarComprobante(h,List.of(line("INEXISTENTE")),refs,false));
        check(repo.ficha("F001",number).value(0,"observacion").equals("EDITADO"),"Fallo al editar conserva cabecera y detalle anteriores");
        repo.bajaComprobante("F001",number,2);
        check(repo.comprobantes("F001-"+number,false).rows().isEmpty(),"Baja excluye del listado operativo");
        check(repo.comprobantes("F001-"+number,true).rows().size()==1,"Baja permanece consultable en historial");
        check(repo.detalle("F001",number).rows().size()==2,"Baja conserva sus líneas");
        rejects("P0002",()->repo.guardarComprobante(h,lines,refs,false));
        var product=new LinkedHashMap<String,Object>();product.put("codigo","TEST-70");product.put("descripcion","Producto de prueba");product.put("unidad_medida","UND");
        repo.guardarCatalogo("producto_servicio","crear",product);
        check(repo.catalogo("producto_servicio","TEST-70",false).rows().size()==1,"Alta de catálogo");
        product.put("descripcion","Producto corregido");repo.guardarCatalogo("producto_servicio","modificar",product);
        check(repo.catalogo("producto_servicio","TEST-70",false).value(0,"descripcion").equals("Producto corregido"),"Edición de catálogo");
        repo.guardarCatalogo("producto_servicio","baja",product);
        check(repo.catalogo("producto_servicio","TEST-70",false).rows().isEmpty(),"Baja lógica de catálogo");
        check(repo.catalogo("producto_servicio","TEST-70",true).rows().size()==1,"Catálogo conserva historial");
        var money=new LinkedHashMap<String,Object>();money.put("codigo_moneda","TST");money.put("nombre_moneda","MONEDA PRUEBA 70");
        repo.guardarCatalogo("moneda","crear",money);check(repo.catalogo("moneda","TST",false).rows().size()==1,"Alta de moneda");
        money.put("nombre_moneda","MONEDA EDITADA 70");repo.guardarCatalogo("moneda","modificar",money);
        check(repo.catalogo("moneda","TST",false).value(0,"nombre_moneda").equals("MONEDA EDITADA 70"),"Edición de moneda");
        repo.guardarCatalogo("moneda","baja",money);check(repo.catalogo("moneda","TST",false).rows().isEmpty(),"Baja de moneda");
        var series=new LinkedHashMap<String,Object>();series.put("serie","T070");series.put("tipo_comprobante","Boleta de venta electrónica");
        repo.guardarCatalogo("serie_comprobante","crear",series);check(repo.catalogo("serie_comprobante","T070",false).rows().size()==1,"Alta de serie");
        series.put("tipo_comprobante","Factura electrónica");repo.guardarCatalogo("serie_comprobante","modificar",series);
        check(repo.catalogo("serie_comprobante","T070",false).value(0,"tipo_comprobante").equals("Factura electrónica"),"Edición de serie sin documentos");
        repo.guardarCatalogo("serie_comprobante","baja",series);check(repo.catalogo("serie_comprobante","T070",false).rows().isEmpty(),"Baja de serie");
        rejects("23503",()->repo.guardarCatalogo("serie_comprobante","modificar",Map.of("serie","F001","tipo_comprobante","Boleta de venta electrónica")));
        var document=new LinkedHashMap<String,Object>();document.put("tipo_relacionado","Cotización");document.put("numero_relacionado","DOC-70-ANTES");
        repo.guardarCatalogo("documento_relacionado","crear",document);check(repo.catalogo("documento_relacionado","DOC-70-ANTES",false).rows().size()==1,"Alta de documento relacionado");
        repo.guardarComprobante(header(number+4),lines,List.of(Map.copyOf(document)),true);
        document.put("anterior_tipo_relacionado","Cotización");document.put("anterior_numero_relacionado","DOC-70-ANTES");document.put("numero_relacionado","DOC-70-DESPUES");
        repo.guardarCatalogo("documento_relacionado","modificar",document);
        check(repo.referencias("F001",number+4).value(0,"numero_relacionado").equals("DOC-70-DESPUES"),"Corrección de documento actualiza asociaciones");
        check(((Number)repo.ficha("F001",number+4).value(0,"version")).intValue()==2,"Corrección de referencia invalida ediciones anteriores");
        repo.guardarCatalogo("documento_relacionado","baja",document);check(repo.catalogo("documento_relacionado","DOC-70-DESPUES",false).rows().isEmpty(),"Baja de documento relacionado");
        Map<String,Object> original=header(number+4);original.put("version",2);
        repo.guardarComprobante(original,lines,List.of(Map.of("tipo_relacionado","Cotización","numero_relacionado","DOC-70-DESPUES")),false);
        check(repo.referencias("F001",number+4).rows().size()==1,"Editar conserva una referencia histórica con baja lógica");
        Map<String,Object> precise=header(number+5);precise.put("importe_total",new BigDecimal("118.001"));
        rejects("23514",()->repo.guardarComprobante(precise,lines,List.of(),true));
        rejects("22023",()->repo.catalogo("cliente; DROP TABLE cliente","",false));
        System.out.println("RESULTADO CRUD: "+checks+" comprobaciones correctas");
    }
}

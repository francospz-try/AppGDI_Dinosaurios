package pe.edu.ucsm.gdi;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Formulario de los cuatro catálogos auxiliares del modelo. */
public final class CatalogEditor extends JPanel {
    private static final long serialVersionUID=1L;
    public enum Kind {
        PRODUCTOS("Productos y servicios","producto_servicio",new String[]{"codigo","descripcion","unidad_medida"}),
        SERIES("Series","serie_comprobante",new String[]{"serie","tipo_comprobante"}),
        MONEDAS("Monedas","moneda",new String[]{"codigo_moneda","nombre_moneda"}),
        DOCUMENTOS("Documentos relacionados","documento_relacionado",new String[]{"tipo_relacionado","numero_relacionado"});
        final String label,table;final String[] fields;
        Kind(String label,String table,String[] fields){this.label=label;this.table=table;this.fields=fields;}
        @Override public String toString(){return label;}
    }
    private final Kind kind;private final Map<String,JComponent> inputs=new LinkedHashMap<>();
    private final Map<String,Object> original;
    public CatalogEditor(Kind kind,Map<String,Object> original){
        super(new GridLayout(kind.fields.length+(kind==Kind.DOCUMENTOS&&original!=null?1:0),2,12,14));
        this.kind=kind;this.original=original;setPreferredSize(new Dimension(700,kind.fields.length*52+30));
        for(String field:kind.fields){
            JComponent input;
            if(field.equals("tipo_comprobante"))input=new JComboBox<>(new String[]{"Boleta de venta electrónica","Factura electrónica"});
            else if(field.equals("tipo_relacionado"))input=new JComboBox<>(new String[]{"Guía de remisión","Cotización"});
            else input=new JTextField();
            if(original!=null){Object value=original.get(field);if(input instanceof JTextField text)text.setText(value==null?"":value.toString());else ((JComboBox<?>)input).setSelectedItem(value);}
            if(original!=null&&kind!=Kind.DOCUMENTOS&&field.equals(kind.fields[0]))input.setEnabled(false);
            inputs.put(field,input);add(new JLabel(field.replace('_',' ')));add(input);
        }
        if(kind==Kind.DOCUMENTOS&&original!=null){add(new JLabel("La corrección actualiza todas sus referencias."));add(new JLabel("Se conserva la identificación anterior con baja lógica."));}
    }
    public Map<String,Object> values(){
        Map<String,Object> result=new LinkedHashMap<>();
        for(var e:inputs.entrySet()){
            String value=e.getValue() instanceof JTextField t?t.getText().strip():((JComboBox<?>)e.getValue()).getSelectedItem().toString();
            if(value.isEmpty())throw new IllegalArgumentException("Todos los campos del catálogo son obligatorios.");
            result.put(e.getKey(),value);
        }
        if(kind==Kind.DOCUMENTOS&&original!=null){result.put("anterior_tipo_relacionado",original.get("tipo_relacionado"));result.put("anterior_numero_relacionado",original.get("numero_relacionado"));}
        return result;
    }
}

package pe.edu.ucsm.gdi;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Editor de un comprobante completo; recopila datos, nunca ejecuta JDBC. */
public final class ReceiptEditor extends JPanel {
    private static final long serialVersionUID=1L;
    public record Input(Map<String,Object> header,TableData detail,TableData references,Map<String,TableData> options) { }
    public record Draft(Map<String,Object> header,List<Map<String,Object>> detail,List<Map<String,Object>> references) { }
    private record Client(String type,String number,String label){@Override public String toString(){return label;}}
    private final JComboBox<String> series=new JComboBox<>(),currency=new JComboBox<>();
    private final JComboBox<Client> client=new JComboBox<>();
    private final JTextField number=new JTextField(),date=new JTextField(),due=new JTextField(),payment=new JTextField(),method=new JTextField();
    private final JTextArea note=new JTextArea(3,30);
    private final Map<String,JTextField> amounts=new LinkedHashMap<>();
    private final DefaultTableModel lineModel=new DefaultTableModel(new String[]{"Producto / servicio","Cantidad","Valor unitario","Precio unitario","Descuento","Importe de línea"},0);
    private final DefaultTableModel referenceModel=new DefaultTableModel(new String[]{"Tipo de documento","Número de referencia"},0);
    private final JTable lines=new JTable(lineModel),references=new JTable(referenceModel);
    private final int version;

    public static Input load(GdiRepository repo,String serie,int numero,boolean creating) throws java.sql.SQLException {
        Map<String,Object> header=creating?new LinkedHashMap<>():row(repo.ficha(serie,numero),0);
        if(!creating && !Boolean.TRUE.equals(header.get("activo")))throw new IllegalArgumentException("El comprobante tiene baja lógica.");
        Map<String,TableData> options=new LinkedHashMap<>();
        for(String kind:List.of("serie_comprobante","moneda","cliente","producto_servicio"))options.put(kind,repo.opciones(kind,serie,numero));
        return new Input(header,creating?new TableData(List.of(),List.of()):repo.detalle(serie,numero),
            creating?new TableData(List.of(),List.of()):repo.referencias(serie,numero),options);
    }
    static Map<String,Object> row(TableData data,int index){
        if(data.rows().isEmpty())throw new IllegalArgumentException("El registro ya no está disponible.");
        Map<String,Object> result=new LinkedHashMap<>();for(String key:data.columns())result.put(key,data.value(index,key));return result;
    }
    private static String text(Object value){return value==null?"":value instanceof BigDecimal n?n.toPlainString():value.toString();}
    public ReceiptEditor(Input input,boolean creating) {
        super(new BorderLayout(8,10));setPreferredSize(new Dimension(1020,570));
        Map<String,Object> h=input.header();version=h.get("version") instanceof Number n?n.intValue():1;
        for(var r:input.options().get("serie_comprobante").rows())series.addItem(r.get(0).toString());
        for(var r:input.options().get("moneda").rows())currency.addItem(r.get(0).toString());
        client.addItem(new Client(null,null,"Sin cliente identificado / CLIENTES VARIOS"));
        for(var r:input.options().get("cliente").rows()){
            Client c=new Client(r.get(0).toString(),r.get(1).toString(),r.get(0)+" "+r.get(1)+" · "+r.get(2));client.addItem(c);
            if(c.type().equals(h.get("tipo_documento"))&&c.number().equals(h.get("numero_documento")))client.setSelectedItem(c);
        }
        if(!creating){series.setSelectedItem(h.get("serie"));currency.setSelectedItem(h.get("codigo_moneda"));}
        series.setEnabled(creating);number.setEditable(creating);
        number.setText(text(h.get("numero")));date.setText(creating?LocalDate.now().toString():text(h.get("fecha_emision")));
        due.setText(text(h.get("fecha_vencimiento")));payment.setText(text(h.get("forma_pago")));method.setText(text(h.get("medio_pago")));note.setText(text(h.get("observacion")));
        note.setLineWrap(true);note.setWrapStyleWord(true);
        JPanel general=new JPanel(new GridLayout(8,2,12,12));general.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        addField(general,"Serie",series);addField(general,"Número ya emitido",number);addField(general,"Fecha de emisión (AAAA-MM-DD)",date);
        addField(general,"Vencimiento (opcional)",due);addField(general,"Moneda",currency);addField(general,"Cliente",client);
        addField(general,"Forma de pago (opcional)",payment);addField(general,"Medio de pago (opcional)",method);
        JPanel data=new JPanel(new BorderLayout(8,8));data.add(general,BorderLayout.CENTER);
        JPanel observations=new JPanel(new BorderLayout());observations.add(new JLabel("Observación (opcional, hasta 250 caracteres)"),BorderLayout.NORTH);observations.add(new JScrollPane(note));data.add(observations,BorderLayout.SOUTH);
        JPanel money=new JPanel(new GridLayout(11,2,12,9));money.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        String[][] fields={{"op_gravada","Operaciones gravadas"},{"op_exonerada","Operaciones exoneradas"},{"op_inafecta","Operaciones inafectas"},{"op_gratuita","Operaciones gratuitas (opcional)"},{"isc","ISC (opcional)"},{"igv","IGV"},{"otros_cargos","Otros cargos (opcional)"},{"otros_tributos","Otros tributos (opcional)"},{"monto_redondeo","Redondeo (opcional; admite negativos)"},{"importe_total","Importe total impreso"},{"percepcion","Percepción (opcional)"}};
        for(String[] f:fields){JTextField value=new JTextField(text(h.get(f[0])));if(creating&&List.of("op_gravada","op_exonerada","op_inafecta","igv","importe_total").contains(f[0]))value.setText("0.00");amounts.put(f[0],value);addField(money,f[1],value);}
        Map<String,String> products=new LinkedHashMap<>();for(var r:input.options().get("producto_servicio").rows())products.put(r.get(0).toString(),r.get(1).toString());
        JComboBox<String> codes=new JComboBox<>(products.keySet().toArray(String[]::new));
        codes.setRenderer(new javax.swing.DefaultListCellRenderer(){
            private static final long serialVersionUID=1L;
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                return super.getListCellRendererComponent(list,value==null?"":value+" · "+products.getOrDefault(value.toString(),""),index,selected,focus);
            }
        });
        lines.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(codes));
        lines.setRowHeight(30);lines.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        for(int r=0;r<input.detail().rows().size();r++){
            var d=row(input.detail(),r);lineModel.addRow(new Object[]{d.get("codigo"),text(d.get("cantidad")),text(d.get("valor_unitario")),text(d.get("precio_unitario")),text(d.get("descuento")),text(d.get("importe_venta"))});
        }
        references.setRowHeight(30);references.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        references.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Guía de remisión","Cotización"})));
        for(var r:input.references().rows())referenceModel.addRow(r.toArray());
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Datos generales",data);tabs.addTab("Importes",money);
        tabs.addTab("Detalle",grid(lines,()->lineModel.addRow(new Object[]{codes.getItemCount()==0?"":codes.getItemAt(0),"1.00","0.00","","0.00","0.00"}),()->remove(lines,lineModel)));
        tabs.addTab("Referencias",grid(references,()->referenceModel.addRow(new Object[]{"Guía de remisión",""}),()->remove(references,referenceModel)));
        add(tabs);add(new JLabel("Transcriba los valores impresos. Campos opcionales vacíos se guardan como ausentes; no se recalculan importes."),BorderLayout.SOUTH);
    }
    private static void addField(JPanel p,String label,java.awt.Component field){p.add(new JLabel(label));p.add(field);}
    private static JPanel grid(JTable table,Runnable add,Runnable remove){JPanel p=new JPanel(new BorderLayout(8,8));JPanel buttons=new JPanel();JButton a=new JButton("Añadir fila"),d=new JButton("Quitar fila");a.addActionListener(e->add.run());d.addActionListener(e->remove.run());buttons.add(a);buttons.add(d);p.add(buttons,BorderLayout.NORTH);JScrollPane scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());p.add(scroll);return p;}
    private static void remove(JTable table,DefaultTableModel model){if(table.isEditing())table.getCellEditor().stopCellEditing();int row=table.getSelectedRow();if(row>=0)model.removeRow(table.convertRowIndexToModel(row));}
    public static BigDecimal decimal(String value,String label,boolean optional,int scale){
        value=value.strip();if(value.isEmpty()&&optional)return null;
        if(!value.matches("-?[0-9]+([.,][0-9]+)?"))throw new IllegalArgumentException(label+": use un número decimal sin separador de miles.");
        BigDecimal n=new BigDecimal(value.replace(',','.'));
        if(n.stripTrailingZeros().scale()>scale)throw new IllegalArgumentException(label+": máximo "+scale+" decimales.");return n;
    }
    public Draft draft(){
        if(lines.isEditing()&&!lines.getCellEditor().stopCellEditing()||references.isEditing()&&!references.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Termine de editar la celda.");
        if(series.getSelectedItem()==null||currency.getSelectedItem()==null)throw new IllegalArgumentException("Registre primero una serie y una moneda activas.");
        if(!number.getText().strip().matches("[0-9]{1,8}")||Integer.parseInt(number.getText().strip())<1)throw new IllegalArgumentException("Número de comprobante: entre 1 y 99999999.");
        try{LocalDate.parse(date.getText().strip());if(!due.getText().isBlank())LocalDate.parse(due.getText().strip());}catch(java.time.DateTimeException e){throw new IllegalArgumentException("Use fechas válidas con formato AAAA-MM-DD.");}
        if(payment.getText().length()>20||method.getText().length()>30||note.getText().length()>250)throw new IllegalArgumentException("Forma de pago: hasta 20 caracteres; medio: 30; observación: 250.");
        Map<String,Object> h=new LinkedHashMap<>();h.put("serie",series.getSelectedItem());h.put("numero",Integer.parseInt(number.getText().strip()));h.put("fecha_emision",date.getText().strip());h.put("fecha_vencimiento",due.getText().isBlank()?null:due.getText().strip());
        h.put("codigo_moneda",currency.getSelectedItem());h.put("forma_pago",blank(payment.getText()));h.put("medio_pago",blank(method.getText()));h.put("observacion",blank(note.getText()));h.put("version",version);
        Client c=(Client)client.getSelectedItem();h.put("tipo_documento",c.type());h.put("numero_documento",c.number());
        for(var e:amounts.entrySet())h.put(e.getKey(),decimal(e.getValue().getText(),e.getKey(),!List.of("op_gravada","op_exonerada","op_inafecta","igv","importe_total").contains(e.getKey()),2));
        List<Map<String,Object>> detail=new ArrayList<>();String[] names={"codigo","cantidad","valor_unitario","precio_unitario","descuento","importe_venta"};
        for(int r=0;r<lineModel.getRowCount();r++){Map<String,Object> item=new LinkedHashMap<>();for(int col=0;col<6;col++){String val=text(lineModel.getValueAt(r,col));item.put(names[col],col==0?val:decimal(val,names[col],col==3,col==1?2:7));}detail.add(item);}
        if(detail.isEmpty())throw new IllegalArgumentException("Añada al menos una línea de detalle.");
        List<Map<String,Object>> refs=new ArrayList<>();
        for(int r=0;r<referenceModel.getRowCount();r++){String num=text(referenceModel.getValueAt(r,1)).strip();if(num.isEmpty()||num.length()>30)throw new IllegalArgumentException("Referencia: número obligatorio, hasta 30 caracteres.");refs.add(Map.of("tipo_relacionado",referenceModel.getValueAt(r,0),"numero_relacionado",num));}
        return new Draft(h,detail,refs);
    }
    private static String blank(String value){return value.isBlank()?null:value;}
}

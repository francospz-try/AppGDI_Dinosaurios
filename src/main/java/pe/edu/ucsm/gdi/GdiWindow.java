package pe.edu.ucsm.gdi;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Vista de escritorio. JDBC solo se ejecuta en trabajadores de fondo. */
public final class GdiWindow extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Color GREEN = new Color(25,91,68);
    private final JTextField host=new JTextField("127.0.0.1"), port=new JTextField("5432"),
            dbName=new JTextField("registro_comprobantes"), user=new JTextField("gdi_app");
    private final JPasswordField password=new JPasswordField();
    private final JLabel status=new JLabel("Sin conexión. Configure PostgreSQL para comenzar.");
    private final JLabel counter=new JLabel("Busque por serie-número, cliente o documento. Máximo 500 resultados.");
    private final JTable receipts=table(), customers=table(), reports=table(),catalog=table();
    private final JCheckBox includeInactive=new JCheckBox("Incluir bajas"),catalogInactive=new JCheckBox("Incluir bajas");
    private final JComboBox<CatalogEditor.Kind> catalogKind=new JComboBox<>(CatalogEditor.Kind.values());
    private final JTextField catalogSearch=new JTextField(16);
    private final JTextField receiptSearch=new JTextField(24), customerSearch=new JTextField(22);
    private final JTabbedPane tabs=new JTabbedPane();
    private final List<JButton> dbButtons=new ArrayList<>();
    private final JButton connect=new JButton("Probar y conectar");
    private GdiRepository repository;
    private TableData receiptData, customerData,catalogData;

    private static final String[] REPORTS={
        "01 · Comprobantes del 25 al 27 de agosto de 2026", "02 · Detalle de F001-005206",
        "03 · Últimos comprobantes del cliente de prueba", "04 · Referencias documentales de ejemplo",
        "05 · Resumen mensual de 2026", "06 · Diez productos con mayor importe",
        "07 · Clientes con al menos veinte comprobantes", "08 · Diferencias en suma de componentes",
        "09 · Diferencias entre cabecera y detalle", "10 · Evolución del producto 349",
        "11 · Continuidad de numeración", "12 · Clientes de 2025 sin compras desde 2026"};

    public GdiWindow(Path configPath) {
        super(new BorderLayout(0,16));
        setBorder(BorderFactory.createEmptyBorder(22,26,18,26));
        setBackground(new Color(244,247,245));
        JPanel heading=new JPanel(new BorderLayout()); heading.setOpaque(false);
        JLabel title=new JLabel("GDI  /  Registro de comprobantes");
        title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,26)); title.setForeground(GREEN);
        heading.add(title,BorderLayout.NORTH);
        heading.add(new JLabel("Boletas y facturas emitidas · Cliente PostgreSQL · Avance al 70 %"),BorderLayout.SOUTH);
        add(heading,BorderLayout.NORTH);
        tabs.addTab("Conexión",connectionPanel());
        tabs.addTab("Comprobantes",receiptsPanel());
        tabs.addTab("Clientes",customersPanel());
        tabs.addTab("Catálogos",catalogPanel());
        tabs.addTab("Reportes",reportsPanel());
        add(tabs,BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(8,0,0,0)); add(status,BorderLayout.SOUTH);
        status.setName("estado"); connect.setName("conectar");
        receiptSearch.setName("buscarComprobante"); receipts.setName("comprobantes");
        customers.setName("clientes");
        catalog.setName("catalogos");
        try {
            DbConfig c=DbConfig.load(configPath);
            host.setText(c.host());port.setText(Integer.toString(c.port()));dbName.setText(c.name());
            user.setText(c.user());password.setText(c.password());
        } catch (Exception e) { status.setText("Configure la conexión aquí o copie config/database.properties.example."); }
        setButtons(false);
        tabs.addChangeListener(e -> {
            if(tabs.getSelectedIndex()==2 && repository!=null && connect.isEnabled())loadCustomers();
            if(tabs.getSelectedIndex()==3 && repository!=null && connect.isEnabled())loadCatalog();
        });
    }

    private JPanel connectionPanel() {
        JPanel body=new JPanel(new BorderLayout(20,20));body.setBorder(BorderFactory.createEmptyBorder(28,28,28,28));
        JPanel form=new JPanel(new GridLayout(6,2,12,18));
        form.add(new JLabel("Servidor"));form.add(host);form.add(new JLabel("Puerto"));form.add(port);
        form.add(new JLabel("Base de datos"));form.add(dbName);form.add(new JLabel("Usuario PostgreSQL"));form.add(user);
        form.add(new JLabel("Contraseña"));form.add(password);form.add(new JLabel(""));form.add(connect);
        JPanel left=new JPanel(new BorderLayout());left.add(form,BorderLayout.NORTH);
        left.setPreferredSize(new Dimension(520,340));body.add(left,BorderLayout.WEST);
        JTextArea help=new JTextArea("ANTES DE CONECTAR\n\n"
            +"1. Inicie PostgreSQL.\n\n2. Prepare la base con los scripts originales.\n\n"
            +"3. Aplique 04, 07, 05 y por último 06, según el README.\n\n"
            +"4. Asigne una contraseña al rol gdi_app siguiendo el README.\n\n"
            +"5. Introduzca las credenciales y pruebe la conexión.\n\n"
            +"El aplicativo no crea ni reinicia la base al abrirse.\n"
            +"La contraseña introducida aquí permanece en memoria; no se guarda en un archivo.");
        help.setEditable(false);help.setLineWrap(true);help.setWrapStyleWord(true);help.setOpaque(false);
        help.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));body.add(help,BorderLayout.CENTER);
        connect.addActionListener(e -> {
            try {
                DbConfig c=new DbConfig(host.getText().strip(),Integer.parseInt(port.getText().strip()),
                    dbName.getText().strip(),user.getText().strip(),new String(password.getPassword()));
                GdiRepository candidate=new GdiRepository(c); repository=null;
                clearTables();
                work(() -> candidate.conexion(), data -> {
                    repository=candidate;
                    status.setText("Conectado · " + data.value(0,"base_datos") + " · PostgreSQL " + data.value(0,"version"));
                    tabs.setSelectedIndex(1);loadReceipts();
                });
            } catch (IllegalArgumentException ex) { showError(ex); }
        });
        return body;
    }

    private JPanel receiptsPanel() {
        JPanel body=panel(); JPanel toolbar=new JPanel();
        JButton search=dbButton("Buscar",this::loadReceipts);search.setName("buscar");
        toolbar.add(receiptSearch);toolbar.add(search);toolbar.add(dbButton("Ver comprobante",this::openReceipt));
        JButton create=dbButton("Nuevo",()->editReceipt(true));create.setName("nuevoComprobante");toolbar.add(create);
        toolbar.add(dbButton("Modificar",()->editReceipt(false)));toolbar.add(dbButton("Dar de baja",this::deleteReceipt));toolbar.add(includeInactive);
        includeInactive.addActionListener(e->{if(repository!=null&&connect.isEnabled())loadReceipts();});
        receiptSearch.addActionListener(e -> { if(repository!=null && connect.isEnabled()) loadReceipts(); });
        JPanel top=new JPanel(new BorderLayout());top.add(toolbar,BorderLayout.NORTH);top.add(counter,BorderLayout.SOUTH);
        body.add(top,BorderLayout.NORTH);body.add(scroll(receipts),BorderLayout.CENTER);
        return body;
    }

    private JPanel customersPanel() {
        JPanel body=panel();JPanel toolbar=new JPanel();
        toolbar.add(customerSearch);toolbar.add(dbButton("Buscar",this::loadCustomers));
        toolbar.add(dbButton("Nuevo cliente",() -> editCustomer(false)));
        toolbar.add(dbButton("Modificar",() -> editCustomer(true)));
        toolbar.add(dbButton("Dar de baja",this::deleteCustomer));
        body.add(toolbar,BorderLayout.NORTH);body.add(scroll(customers),BorderLayout.CENTER);
        body.add(new JLabel("La baja lógica conserva los comprobantes y sus referencias al cliente."),BorderLayout.SOUTH);
        return body;
    }

    private JPanel reportsPanel() {
        JPanel body=panel();JPanel toolbar=new JPanel();JComboBox<String> options=new JComboBox<>(REPORTS);
        toolbar.add(options);toolbar.add(dbButton("Ejecutar reporte",() -> work(
            () -> repository.reporte(options.getSelectedIndex()+1), data -> {
                fill(reports,data);status.setText("Reporte ejecutado · " + data.rows().size() + " filas. Un resultado vacío puede ser correcto.");
            })));
        body.add(toolbar,BorderLayout.NORTH);body.add(scroll(reports),BorderLayout.CENTER);
        body.add(new JLabel("Consultas de demostración basadas en el informe. Periodos y ejemplos fijos según sus títulos."),BorderLayout.SOUTH);
        return body;
    }

    private void loadReceipts() {
        String query=receiptSearch.getText().strip();
        boolean include=includeInactive.isSelected();
        work(() -> repository.comprobantes(query,include), data -> {
            receiptData=data;fill(receipts,data);
            counter.setText(data.rows().size() + " resultados · Máximo 500 por búsqueda · Seleccione una fila para ver el detalle.");
            status.setText("Consulta completada. Registro de comprobantes ya emitidos.");
        });
    }
    private JPanel catalogPanel(){
        JPanel body=panel(),toolbar=new JPanel();toolbar.add(catalogKind);toolbar.add(catalogSearch);
        toolbar.add(dbButton("Buscar",this::loadCatalog));toolbar.add(dbButton("Nuevo",()->editCatalog(false)));
        toolbar.add(dbButton("Modificar",()->editCatalog(true)));toolbar.add(dbButton("Dar de baja",this::deleteCatalog));toolbar.add(catalogInactive);
        body.add(toolbar,BorderLayout.NORTH);body.add(scroll(catalog),BorderLayout.CENTER);
        body.add(new JLabel("La baja impide nuevos usos y conserva las asociaciones históricas."),BorderLayout.SOUTH);
        catalogKind.addActionListener(e->{if(repository!=null&&connect.isEnabled())loadCatalog();});
        catalogInactive.addActionListener(e->{if(repository!=null&&connect.isEnabled())loadCatalog();});return body;
    }
    private void loadCatalog(){
        CatalogEditor.Kind kind=(CatalogEditor.Kind)catalogKind.getSelectedItem();String query=catalogSearch.getText().strip();boolean include=catalogInactive.isSelected();
        work(()->repository.catalogo(kind.table,query,include),data->{catalogData=data;fill(catalog,data);status.setText(data.rows().size()+" registros · Máximo 500 por búsqueda.");});
    }
    private void editCatalog(boolean editing){
        int row=editing?selected(catalog):-1;if(editing&&row<0)return;
        java.util.Map<String,Object> original=editing?ReceiptEditor.row(catalogData,row):null;
        if(editing&&!Boolean.TRUE.equals(original.get("activo"))){status.setText("El registro tiene baja lógica.");return;}
        CatalogEditor.Kind kind=(CatalogEditor.Kind)catalogKind.getSelectedItem();CatalogEditor editor=new CatalogEditor(kind,original);
        showCatalogEditor(editor,kind,editing);
    }
    private void showCatalogEditor(CatalogEditor editor,CatalogEditor.Kind kind,boolean editing){
        while(JOptionPane.showConfirmDialog(this,editor,(editing?"Modificar · ":"Nuevo · ")+kind.label,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{var values=editor.values();work(()->{repository.guardarCatalogo(kind.table,editing?"modificar":"crear",values);return true;},ignored->loadCatalog(),error->showCatalogEditor(editor,kind,editing));return;}
            catch(IllegalArgumentException e){showError(e);}
        }
    }
    private void deleteCatalog(){
        int row=selected(catalog);if(row<0)return;var values=ReceiptEditor.row(catalogData,row);
        if(!Boolean.TRUE.equals(values.get("activo"))){status.setText("El registro ya tiene baja lógica.");return;}
        CatalogEditor.Kind kind=(CatalogEditor.Kind)catalogKind.getSelectedItem();
        if(JOptionPane.showConfirmDialog(this,"¿Dar de baja al registro seleccionado? Se conservará su historial.","Baja lógica",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        work(()->{repository.guardarCatalogo(kind.table,"baja",values);return true;},ignored->loadCatalog());
    }
    private void editReceipt(boolean creating){
        int row=creating?-1:selected(receipts);if(!creating&&row<0)return;
        String serie=creating?"":receiptData.value(row,"serie").toString();int numero=creating?0:((Number)receiptData.value(row,"numero")).intValue();
        work(()->ReceiptEditor.load(repository,serie,numero,creating),input->showReceiptEditor(new ReceiptEditor(input,creating),creating));
    }
    private void showReceiptEditor(ReceiptEditor editor,boolean creating){
        while(JOptionPane.showConfirmDialog(this,editor,creating?"Registrar comprobante emitido":"Modificar comprobante",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{var draft=editor.draft();work(()->{repository.guardarComprobante(draft.header(),draft.detail(),draft.references(),creating);return true;},ignored->loadReceipts(),error->showReceiptEditor(editor,creating));return;}
            catch(IllegalArgumentException e){showError(e);}
        }
    }
    private void deleteReceipt(){
        int row=selected(receipts);if(row<0)return;
        String serie=receiptData.value(row,"serie").toString();int numero=((Number)receiptData.value(row,"numero")).intValue();
        work(()->repository.ficha(serie,numero),data->{
            if(data.rows().isEmpty()||!Boolean.TRUE.equals(data.value(0,"activo"))){status.setText("El comprobante ya no está activo.");return;}
            if(JOptionPane.showConfirmDialog(this,"¿Dar de baja a "+serie+"-"+numero+"? Se conservarán líneas y referencias.","Baja lógica",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            int version=((Number)data.value(0,"version")).intValue();work(()->{repository.bajaComprobante(serie,numero,version);return true;},ignored->loadReceipts());
        });
    }
    private void loadCustomers() {
        String query=customerSearch.getText().strip();
        work(() -> repository.clientes(query), data -> {
            customerData=data;fill(customers,data);status.setText(data.rows().size()+" clientes activos · Máximo 500 por búsqueda.");
        });
    }

    private void openReceipt() {
        int row=selected(receipts);if(row<0)return;
        String serie=(String)receiptData.value(row,"serie");int numero=((Number)receiptData.value(row,"numero")).intValue();
        work(() -> List.of(repository.ficha(serie,numero),repository.detalle(serie,numero),repository.referencias(serie,numero)), data -> {
            if(data.get(0).rows().isEmpty()){status.setText("El comprobante ya no está disponible.");return;}
            JTabbedPane detail=new JTabbedPane();TableData header=data.get(0);
            List<List<Object>> values=new ArrayList<>();
            for(String column:header.columns()) values.add(java.util.Arrays.asList(column,header.value(0,column)));
            JTable fields=table();fill(fields,new TableData(List.of("Campo","Valor"),values));
            detail.addTab("Cabecera",scroll(fields));
            for(int i=1;i<3;i++){JTable t=table();fill(t,data.get(i));detail.addTab(i==1?"Detalle":"Referencias",scroll(t));}
            detail.setPreferredSize(new Dimension(920,480));
            JOptionPane.showMessageDialog(this,detail,"Comprobante " + serie+"-"+numero,JOptionPane.PLAIN_MESSAGE);
        });
    }

    private void editCustomer(boolean editing) {
        int row=editing?selected(customers):-1;if(editing&&row<0)return;
        JComboBox<String> type=new JComboBox<>(new String[]{"DNI","RUC"});
        JTextField doc=new JTextField(),name=new JTextField(),address=new JTextField();
        if(editing) {
            type.setSelectedItem(customerData.value(row,"tipo_documento"));
            doc.setText((String)customerData.value(row,"numero_documento"));
            name.setText((String)customerData.value(row,"nombre_razon_social"));
            Object old=customerData.value(row,"direccion");address.setText(old==null?"":old.toString());
            type.setEnabled(false);doc.setEditable(false);
        }
        JPanel form=new JPanel(new GridLayout(4,2,10,12));
        form.add(new JLabel("Tipo"));form.add(type);form.add(new JLabel("Documento"));form.add(doc);
        form.add(new JLabel("Nombre / razón social"));form.add(name);form.add(new JLabel("Dirección (opcional)"));form.add(address);
        form.setPreferredSize(new Dimension(580,200));
        if(JOptionPane.showConfirmDialog(this,form,editing?"Modificar cliente":"Nuevo cliente",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String kind=(String)type.getSelectedItem(),number=doc.getText().strip(),label=name.getText().strip(),home=address.getText().strip();
        if(!number.matches("DNI".equals(kind)?"[0-9]{8}":"[0-9]{11}")||label.isEmpty()||label.length()>200||home.length()>250) {
            showError(new IllegalArgumentException("Use DNI de 8 dígitos o RUC de 11; nombre de 1–200 caracteres y dirección de hasta 250."));return;
        }
        work(() -> {if(editing)repository.modificarCliente(kind,number,label,home);else repository.crearCliente(kind,number,label,home);return true;},
            ignored -> loadCustomers());
    }
    private void deleteCustomer() {
        int row=selected(customers);if(row<0)return;
        String type=(String)customerData.value(row,"tipo_documento"),doc=(String)customerData.value(row,"numero_documento");
        if(JOptionPane.showConfirmDialog(this,"¿Dar de baja al cliente seleccionado? Se conservará su historial.","Confirmar baja lógica",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        work(() -> {repository.bajaCliente(type,doc);return true;}, ignored -> loadCustomers());
    }

    private int selected(JTable table) {
        int row=table.getSelectedRow();
        if(row<0){status.setText("Seleccione primero una fila.");return -1;}
        return table.convertRowIndexToModel(row);
    }
    private JButton dbButton(String text,Runnable action) {
        JButton b=new JButton(text);b.addActionListener(e -> action.run());dbButtons.add(b);return b;
    }
    private void setButtons(boolean enabled) { for(JButton b:dbButtons)b.setEnabled(enabled);catalogKind.setEnabled(enabled);includeInactive.setEnabled(enabled);catalogInactive.setEnabled(enabled); }
    private void clearTables(){for(JTable t:List.of(receipts,customers,reports,catalog))t.setModel(new DefaultTableModel());receiptData=null;customerData=null;catalogData=null;}
    private <T> void work(Callable<T> task, Consumer<T> success) {
        work(task,success,null);
    }
    private <T> void work(Callable<T> task, Consumer<T> success,Consumer<Throwable> failure) {
        connect.setEnabled(false);setButtons(false);status.setText("Consultando PostgreSQL…");
        new SwingWorker<T,Void>() {
            @Override protected T doInBackground() throws Exception {return task.call();}
            @Override protected void done() {
                connect.setEnabled(true);setButtons(repository!=null);
                try {success.accept(get());}
                catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();showError(cause);if(failure!=null)failure.accept(cause);}
            }
        }.execute();
    }
    private void showError(Throwable e) {
        String message=UserMessages.error(e);status.setText(message);
        JOptionPane.showMessageDialog(this,message,"No se completó la operación",JOptionPane.ERROR_MESSAGE);
    }
    private static JPanel panel(){JPanel p=new JPanel(new BorderLayout(8,12));p.setBorder(BorderFactory.createEmptyBorder(16,12,16,12));return p;}
    private static JScrollPane scroll(JTable table){JScrollPane pane=new JScrollPane(table);pane.setColumnHeaderView(table.getTableHeader());return pane;}
    private static JTable table() {
        JTable t=new JTable();t.setRowHeight(28);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);t.setFillsViewportHeight(true);t.setGridColor(new Color(228,234,230));
        t.setDefaultRenderer(java.sql.Date.class,new javax.swing.table.DefaultTableCellRenderer(){
            private static final long serialVersionUID=1L;
            @Override protected void setValue(Object value){setText(value==null?"":value.toString());}
        });
        javax.swing.table.DefaultTableCellRenderer decimal=new javax.swing.table.DefaultTableCellRenderer(){
            private static final long serialVersionUID=1L;
            @Override protected void setValue(Object value){
                super.setValue(value instanceof java.math.BigDecimal number?number.toPlainString():value);
            }
        };
        decimal.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        t.setDefaultRenderer(java.math.BigDecimal.class,decimal);
        t.setDefaultRenderer(Object.class,new javax.swing.table.DefaultTableCellRenderer(){
            private static final long serialVersionUID=1L;
            @Override protected void setValue(Object value){
                super.setValue(value instanceof java.math.BigDecimal number?number.toPlainString():value);
            }
        });return t;
    }
    private static void fill(JTable table,TableData data) {
        table.setModel(new ResultTableModel(data));
        if(data.columns().contains("descripcion")||data.columns().size()>9)table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        else table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        for(int n=0;n<table.getColumnCount();n++)table.getColumnModel().getColumn(n).setPreferredWidth(
            data.columns().get(n).contains("descripcion")||data.columns().get(n).equals("cliente")?300:145);
    }
}

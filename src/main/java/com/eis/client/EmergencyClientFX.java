package com.eis.client;

import com.eis.server.HospitalServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.animation.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.awt.Toolkit;
import java.awt.datatransfer.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.SerializationFeature;

public class EmergencyClientFX extends Application {

    private static final String BG      = "#090b0e";
    private static final String PANEL   = "#11141a";
    private static final String PANEL2  = "#0d1017";
    private static final String BORDER  = "#1e232d";
    private static final String ACCENT  = "#2563eb";
    private static final String ACCHOV  = "#3b82f6";
    private static final String DANGER  = "#dc2626";
    private static final String DANGDIM = "#991b1b";
    private static final String DANGBG  = "#130202";
    private static final String WARN    = "#f97316";
    private static final String SUCCESS = "#16a34a";
    private static final String TEXT    = "#f8fafc";
    private static final String MUTED   = "#94a3b8";
    private static final String MUTDIM  = "#475569";
    private static final String MONO    = "'JetBrains Mono', 'Courier New', monospace";

    private static final ObjectMapper OM = new ObjectMapper();

    private static final String[][] SYMPTOM_DATA = {
            {"Chest Pain",             "♥"},  {"Arm Numbness",          "⚡"},
            {"Facial Droop",           "◎"},  {"Slurred Speech",         "❝"},
            {"Twitching",              "〜"}, {"Loss of Consciousness",  "☁"},
            {"Wheezing",               "≋"},  {"Throat Swelling",        "⊕"},
            {"Blurred Vision",         "◉"},  {"Severe Headache",        "⚠"},
            {"Lightheaded",            "↻"},  {"Rapid Heartbeat",        "♡"},
            {"Skin Rash",              "⊛"},  {"Fever / Chills",         "☀"}
    };

    private StackPane rootStack;
    private final TextField   nameField      = new TextField();
    private final TextArea    complaintField = new TextArea();
    private final Slider      ageSlider      = new Slider(0, 110, 30);
    private final Label       ageDisplay     = new Label("30");
    private final ToggleGroup genderGroup    = new ToggleGroup();
    private final ToggleGroup onsetGroup     = new ToggleGroup();
    private final List<ToggleButton> symptomBtns = new ArrayList<>();

    private Label statusLabel, clockLabel, dbStatusLabel;
    private TableView<PatientRow> patientTable;
    private final List<PatientRow> allRecords = new ArrayList<>();
    private TextField dbSearchField;
    private ToggleGroup dbFilterGroup;

    public static class PatientRow {
        private final SimpleStringProperty  id, savedAt, name, gender, topDx, urgency, onset;
        private final SimpleIntegerProperty age;
        private final ObjectNode            raw;

        PatientRow(ObjectNode rec) {
            this.raw     = rec;
            this.id      = new SimpleStringProperty(rec.path("id").asText("—"));
            this.savedAt = new SimpleStringProperty(rec.path("savedAt").asText("?"));
            this.name    = new SimpleStringProperty(rec.path("name").asText("?"));
            this.age     = new SimpleIntegerProperty(rec.path("age").asInt(0));
            this.gender  = new SimpleStringProperty(rec.path("gender").asText("?"));
            this.onset   = new SimpleStringProperty(rec.path("onset").asText("?"));
            this.urgency = new SimpleStringProperty(rec.path("urgency").asText("SAFE"));

            String dx = "";
            JsonNode dxArr = rec.path("diagnoses");
            if (dxArr.isArray() && dxArr.size() > 0) { String mn = dxArr.get(0).path("medicalName").asText(""); dx = mn.isEmpty() ? dxArr.get(0).path("name").asText("") : mn; }
            this.topDx = new SimpleStringProperty(dx.isEmpty() ? "No match" : dx);
        }

        public String    getId()      { return id.get(); }
        public String    getSavedAt() { return savedAt.get(); }
        public String    getName()    { return name.get(); }
        public int       getAge()     { return age.get(); }
        public String    getGender()  { return gender.get(); }
        public String    getOnset()   { return onset.get(); }
        public String    getTopDx()   { return topDx.get(); }
        public String    getUrgency() { return urgency.get(); }
        public ObjectNode getRaw()    { return raw; }
    }

    @Override
    public void start(Stage stage) {
        new Thread(() -> { HospitalServer.startServer(); }, "HospitalServerThread").start();

        rootStack = new StackPane();
        rootStack.setStyle("-fx-background-color:" + BG + ";");

        VBox root = new VBox(0);
        root.getChildren().addAll(buildHeader(), buildTabPane());
        rootStack.getChildren().add(root);

        Scene scene = new Scene(rootStack, 920, 590);
        applyCSS(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && rootStack.getChildren().size() > 1) {
                rootStack.getChildren().remove(rootStack.getChildren().size() - 1);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER
                    && rootStack.getChildren().size() == 1
                    && !(scene.getFocusOwner() instanceof TextArea)) {
                handleTransmit();
                e.consume();
            }
        });

        stage.setTitle("Emergency Triage Hub  ·  EIS v5.0");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        startClock();
    }

    private HBox buildHeader() {
        HBox h = new HBox(12);
        h.setPadding(new Insets(12, 22, 12, 22));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-background-color:" + PANEL + ";-fx-border-color:" + BORDER + ";-fx-border-width:0 0 1 0;");

        Circle dot = new Circle(5, Color.web(DANGER));
        dot.setEffect(new Glow(0.9));
        ScaleTransition pulse = new ScaleTransition(Duration.millis(800), dot);
        pulse.setFromX(1); pulse.setToX(1.6); pulse.setFromY(1); pulse.setToY(1.6);
        pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();

        VBox titleBox = new VBox(2);
        titleBox.getChildren().addAll(
                mk("EMERGENCY INTAKE SYSTEM", 13, TEXT, true),
                mk("EIS v5.0  ·  Socket  ·  DDX Engine  ·  JSON Database", 9, ACCENT, false));

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        statusLabel = mk("● READY", 11, SUCCESS, true);
        clockLabel  = mk("--:--:--", 11, MUTED, false);
        h.getChildren().addAll(dot, titleBox, sp, statusLabel, new Label("   "), clockLabel);
        return h;
    }

    private TabPane buildTabPane() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab t1 = new Tab("  ⚕  INTAKE FORM  ");
        Tab t2 = new Tab("  🗄  PATIENT DATABASE  ");
        t1.setContent(buildIntakeForm());
        t2.setContent(buildDatabaseView());
        t2.setOnSelectionChanged(e -> { if (t2.isSelected()) refreshTable(); });
        tp.getTabs().addAll(t1, t2);
        VBox.setVgrow(tp, Priority.ALWAYS);
        return tp;
    }

    private ScrollPane buildIntakeForm() {
        VBox form = new VBox(16);
        form.setPadding(new Insets(20, 24, 24, 24));
        form.setStyle("-fx-background-color:" + BG + ";");

        form.getChildren().add(secLbl("01 /  PATIENT INFORMATION"));
        VBox card = new VBox(14); card.setStyle(cardSty());
        nameField.setPromptText("Patient full name..."); styleField(nameField);
        card.getChildren().add(g2col("Full Name *", nameField, 90, 340));
        complaintField.setPromptText("Chief complaint in patient's own words (optional)");
        complaintField.setPrefRowCount(2); complaintField.setWrapText(true); styleArea(complaintField);
        card.getChildren().add(g2col("Chief Complaint", complaintField, 90, 340));

        ageSlider.setPrefWidth(220);
        ageSlider.setStyle("-fx-accent:" + ACCENT + ";");
        ageDisplay.setStyle("-fx-text-fill:" + ACCENT + ";-fx-font-family:" + MONO + ";-fx-font-size:16px;-fx-font-weight:bold;");
        ageSlider.valueProperty().addListener((o, v, n) -> ageDisplay.setText(String.valueOf(n.intValue())));

        TextField ageEntry = new TextField("30");
        ageEntry.setPrefWidth(48); ageEntry.setPrefHeight(30);
        ageEntry.setStyle("-fx-background-color:" + PANEL + ";-fx-border-color:" + BORDER + ";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-text-fill:" + ACCENT + ";-fx-font-family:" + MONO + ";-fx-font-size:12px;-fx-alignment:center;");
        ageSlider.valueProperty().addListener((o, v, n) -> { String val = String.valueOf(n.intValue()); ageDisplay.setText(val); if (!ageEntry.isFocused()) ageEntry.setText(val); });
        ageEntry.setOnAction(ev -> { try { int v = Integer.parseInt(ageEntry.getText().trim()); if (v<0)v=0; if(v>110)v=110; ageSlider.setValue(v); ageEntry.setText(String.valueOf(v)); } catch(NumberFormatException ex){ageEntry.setText(String.valueOf((int)ageSlider.getValue()));} });
        ageEntry.focusedProperty().addListener((o,wasFocused,isFocused) -> { if(!isFocused){try{int v=Integer.parseInt(ageEntry.getText().trim());if(v<0)v=0;if(v>110)v=110;ageSlider.setValue(v);ageEntry.setText(String.valueOf(v));}catch(NumberFormatException ex){ageEntry.setText(String.valueOf((int)ageSlider.getValue()));}} });

        HBox ageRow = new HBox(10, fLbl("Age  "), ageSlider, ageEntry, mk("yrs", 10, MUTED, false));
        ageRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(ageRow);

        HBox gRow = new HBox(8, fLbl("Gender  "));
        for (String g : new String[]{"♂  Male","♀  Female","⚧  Other"}) {
            ToggleButton tb = ribbonBtn(g, genderGroup);
            if (g.contains("Male")) tb.setSelected(true);
            gRow.getChildren().add(tb);
        }
        gRow.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(gRow);
        form.getChildren().add(card);

        form.getChildren().add(secLbl("02 /  ONSET / DURATION"));
        HBox oRow = new HBox(8, fLbl("Onset  "));
        String[][] onsets = {{"⚡","SUDDEN"},{"🕐","HOURS"},{"📅","DAYS"},{"📆","WEEKS"},{"🗓","MONTHS"}};
        for (String[] o : onsets) {
            ToggleButton tb = new ToggleButton(o[0]+" "+o[1]);
            tb.setPrefWidth(90); tb.setPrefHeight(36); tb.setToggleGroup(onsetGroup);
            onsetSty(tb, false);
            tb.selectedProperty().addListener((obs,ov,nv)->onsetSty(tb,nv));
            oRow.getChildren().add(tb);
        }
        oRow.setAlignment(Pos.CENTER_LEFT);
        form.getChildren().add(oRow);
        form.getChildren().add(div());

        form.getChildren().add(secLbl("03 /  TAP ALL OBSERVED SYMPTOMS"));
        form.getChildren().add(mk("Highlighted = selected", 10, MUTDIM, false));

        GridPane sg = new GridPane(); sg.setHgap(8); sg.setVgap(8);
        for (int i = 0; i < SYMPTOM_DATA.length; i++) {
            ToggleButton tb = symCard(SYMPTOM_DATA[i][0], SYMPTOM_DATA[i][1]);
            symptomBtns.add(tb);
            sg.add(tb, i%4, i/4);
            GridPane.setHgrow(tb, Priority.ALWAYS);
        }
        form.getChildren().addAll(sg, div());

        Button btnTx = new Button("TRANSMIT TO HOSPITAL  [ENTER →]");
        btnTx.setMaxWidth(Double.MAX_VALUE); btnTx.setPrefHeight(42);
        HBox.setHgrow(btnTx, Priority.ALWAYS);
        sBtn(btnTx, ACCENT, ACCHOV);
        btnTx.setOnAction(e -> handleTransmit());

        Button btnClr = new Button("CLEAR ALL");
        btnClr.setPrefWidth(100); btnClr.setPrefHeight(42);
        btnClr.setStyle("-fx-background-color:transparent;-fx-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-cursor:hand;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;");
        btnClr.setOnAction(e -> clearForm());

        HBox btnRow = new HBox(12, btnTx, btnClr);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        form.getChildren().addAll(btnRow, mk("[DISCLAIMER: Educational demo only · Not for clinical use]", 9, MUTDIM, false));

        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:"+BG+";-fx-background:"+BG+";");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private VBox buildDatabaseView() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(18,22,22,22));
        container.setStyle("-fx-background-color:"+BG+";");

        HBox toolbar = new HBox(12); toolbar.setAlignment(Pos.CENTER_LEFT);
        Label title = mk("PATIENT MEDICAL RECORDS", 13, TEXT, true);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        dbSearchField = new TextField();
        TextField search = dbSearchField;
        search.setPromptText("Search by name, diagnosis, symptom, age…");
        search.setPrefWidth(200); search.setPrefHeight(32);
        search.setStyle("-fx-background-color:"+PANEL+";-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-text-fill:"+TEXT+";-fx-prompt-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:11px;");

        dbFilterGroup = new ToggleGroup();
        ToggleGroup filterGrp = dbFilterGroup;
        HBox filters = new HBox(6);
        String[][] fd = {{"ALL",ACCENT},{"EMERGENCY",DANGER},{"SERIOUS",WARN},{"SAFE",SUCCESS}};
        for (String[] f : fd) {
            ToggleButton tb = new ToggleButton(f[0]);
            tb.setToggleGroup(filterGrp); tb.setPrefHeight(32);
            String fc=f[1];
            String off="-fx-background-color:transparent;-fx-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:9px;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:4 9 4 9;";
            String on ="-fx-background-color:derive("+fc+",-80%);-fx-text-fill:"+fc+";-fx-font-family:"+MONO+";-fx-font-size:9px;-fx-font-weight:bold;-fx-border-color:"+fc+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:4 9 4 9;";
            tb.setStyle(off);
            if ("ALL".equals(f[0])){ tb.setSelected(true); tb.setStyle(on); }
            tb.selectedProperty().addListener((o,v,n)->tb.setStyle(n?on:off));
            filters.getChildren().add(tb);
        }
        dbStatusLabel = mk("—", 10, MUTED, false);

        Button btnRefresh = new Button("↻  REFRESH"); btnRefresh.setPrefHeight(32);
        final String rfBase="-fx-background-color:"+PANEL+";-fx-text-fill:"+TEXT+";-fx-font-family:"+MONO+";-fx-font-size:10px;-fx-cursor:hand;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:4 10 4 10;";
        btnRefresh.setStyle(rfBase);
        final Button fBtnRefresh=btnRefresh;
        fBtnRefresh.setOnMouseEntered(e->fBtnRefresh.setStyle(rfBase.replace(PANEL,"derive("+ACCENT+",-70%)")));
        fBtnRefresh.setOnMouseExited(e->fBtnRefresh.setStyle(rfBase));
        fBtnRefresh.setOnAction(e->refreshTable());

        toolbar.getChildren().addAll(title,sp,search,filters,dbStatusLabel,btnRefresh);
        container.getChildren().add(toolbar);

        patientTable = new TableView<>();
        patientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(patientTable, Priority.ALWAYS);
        patientTable.setStyle("-fx-background-color:"+BG+";-fx-border-color:"+BORDER+";");
        patientTable.setPlaceholder(mk("No records yet. Transmit a patient and click 💾 Save to DB.", 12, MUTED, false));

        patientTable.getColumns().addAll(
                tCol("ID","id",80,80), tCol("SAVED AT","savedAt",145,145),
                tCol("NAME","name",120,120), tCol("AGE","age",45,45),
                tCol("GENDER","gender",65,65), tCol("ONSET","onset",72,72),
                tCol("TOP DX","topDx",240,240));

        TableColumn<PatientRow,String> urgCol = new TableColumn<>("URGENCY");
        urgCol.setCellValueFactory(new PropertyValueFactory<>("urgency"));
        urgCol.setMinWidth(85); urgCol.setPrefWidth(85);
        urgCol.setCellFactory(col->new TableCell<>(){
            @Override protected void updateItem(String val,boolean empty){
                super.updateItem(val,empty);
                if(empty||val==null){setText(null);setStyle("");return;}
                String c=switch(val){"EMERGENCY"->DANGER;"SERIOUS"->WARN;default->SUCCESS;};
                setText(val);
                setStyle("-fx-text-fill:"+c+";-fx-font-weight:bold;-fx-font-family:"+MONO+";-fx-font-size:10px;");
            }
        });
        patientTable.getColumns().add(urgCol);
        VBox.setVgrow(patientTable, Priority.ALWAYS);
        container.getChildren().add(patientTable);

        VBox detailPane = new VBox(0);
        detailPane.setPrefHeight(170); detailPane.setMinHeight(170); detailPane.setMaxHeight(170);
        detailPane.setStyle("-fx-background-color:"+PANEL2+";-fx-border-color:"+BORDER+";-fx-border-width:1 0 0 0;");
        Label hint = mk("  ← Click any row to view full record detail", 10, MUTED, false);
        hint.setPadding(new Insets(10,0,0,14));
        detailPane.getChildren().add(hint);
        container.getChildren().add(detailPane);

        patientTable.setOnMouseClicked(e->{ PatientRow sel=patientTable.getSelectionModel().getSelectedItem(); if(sel!=null)fillDetailPane(sel.getRaw(),detailPane); });
        search.textProperty().addListener((o,v,n)->applyFilter());
        filterGrp.selectedToggleProperty().addListener((o,v,n)->applyFilter());
        return container;
    }

    private String sendToServer(String json) throws Exception {
        try (Socket sock = new Socket("127.0.0.1", 9090)) {
            sock.setSoTimeout(6000);
            OutputStream out = sock.getOutputStream();
            out.write((json+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            InputStream in = sock.getInputStream();
            StringBuilder sb = new StringBuilder();
            int depth=0; boolean started=false; int b;
            char open=0,close=0;
            while((b=in.read())!=-1){
                char c=(char)b;
                if(!started){if(c=='{'){open='{';close='}';started=true;depth=1;sb.append(c);}else if(c=='['){open='[';close=']';started=true;depth=1;sb.append(c);}continue;}
                sb.append(c);
                if(c==open)depth++;
                else if(c==close){depth--;if(depth==0)break;}
            }
            return sb.toString().trim();
        }
    }

    private void handleTransmit() {
        String name = nameField.getText()==null?"":nameField.getText().trim();
        if(name.isEmpty()){popup("MISSING INFO","Please enter the patient's name.",WARN);return;}
        List<String> syms=new ArrayList<>();
        for(ToggleButton tb:symptomBtns) if(tb.isSelected()) syms.add(tb.getText().contains("\n")?tb.getText().split("\n")[1].toLowerCase():tb.getText().toLowerCase());
        if(syms.isEmpty()){popup("MISSING INFO","Please select at least one symptom.",WARN);return;}

        String genderRaw=genderGroup.getSelectedToggle()!=null?((ToggleButton)genderGroup.getSelectedToggle()).getText().replaceAll("[^A-Za-z ]","").trim():"Unknown";
        String gender=genderRaw.toLowerCase().contains("female")?"female":genderRaw.toLowerCase().contains("male")?"male":"any";
        int age=(int)ageSlider.getValue();
        String cc=complaintField.getText()==null?"":complaintField.getText().trim();
        String onsetResolved="UNKNOWN";
        if(onsetGroup.getSelectedToggle()!=null){String raw2=((ToggleButton)onsetGroup.getSelectedToggle()).getText();String[]parts=raw2.split("\\s+");for(int pi=parts.length-1;pi>=0;pi--){String part=parts[pi].replaceAll("[^A-Z]","");if(!part.isEmpty()){onsetResolved=part;break;}}}
        final String onset=onsetResolved;
        setStatus("● TRANSMITTING...",WARN);

        new Thread(()->{
            try{
                ObjectNode req=OM.createObjectNode();
                req.put("name",name);req.put("age",age);req.put("gender",gender);req.put("chiefComplaint",cc);req.put("onset",onset);
                ArrayNode sa=req.putArray("symptoms");for(String s:syms)sa.add(s);
                String raw=sendToServer(OM.writeValueAsString(req));
                ObjectNode resp=(ObjectNode)OM.readTree(raw);
                Platform.runLater(()->showResultPopup(resp));
            }catch(Exception ex){Platform.runLater(()->{setStatus("● SERVER OFFLINE",DANGER);popup("SERVER OFFLINE","Could not reach HospitalServer on 127.0.0.1:9090.\nCheck the console for errors.",DANGER);});}
        }).start();
    }

    private static String[] dxProfile(String medName,String urgency){
        String n=medName.toLowerCase();
        if(n.contains("myocardial")||n.contains("infarction"))return new String[]{"♥","CARDIAC · OCCLUSION",null,"CARDIAC"};
        if(n.contains("angina"))return new String[]{"⚡♥","CARDIAC · ISCHAEMIA",null,"CARDIAC"};
        if(n.contains("pericarditis"))return new String[]{"◎","CARDIAC · INFLAMMATION","#7c3aed","CARDIAC"};
        if(n.contains("supraventricular")||n.contains("svt"))return new String[]{"〜♥","CARDIAC · ARRHYTHMIA","#0891b2","CARDIAC"};
        if(n.contains("hypertensive"))return new String[]{"↑↑","CARDIAC · HYPERTENSION",null,"CARDIAC"};
        if(n.contains("aortic"))return new String[]{"⊘","VASCULAR · DISSECTION",null,"CARDIAC"};
        if(n.contains("pots"))return new String[]{"↻♥","CARDIAC · DYSAUTONOMIA","#0891b2","CARDIAC"};
        if(n.contains("stroke")||n.contains("cva"))return new String[]{"◉","NEURO · VASCULAR",null,"NEURO"};
        if(n.contains("transient")||n.contains("tia"))return new String[]{"◌","NEURO · TRANSIENT","#f59e0b","NEURO"};
        if(n.contains("status epilep"))return new String[]{"〰","NEURO · SEIZURE",null,"NEURO"};
        if(n.contains("febrile"))return new String[]{"〰☀","NEURO · FEBRILE SEIZURE","#f59e0b","NEURO"};
        if(n.contains("migraine"))return new String[]{"≋","NEURO · MIGRAINE","#7c3aed","NEURO"};
        if(n.contains("subarachnoid"))return new String[]{"⊛","NEURO · HAEMORRHAGE",null,"NEURO"};
        if(n.contains("tension"))return new String[]{"⊙","NEURO · TENSION","#16a34a","NEURO"};
        if(n.contains("meningitis"))return new String[]{"⊕","NEURO · INFECTION",null,"NEURO"};
        if(n.contains("anaphylaxis"))return new String[]{"⚠⊕","ALLERGIC · SYSTEMIC",null,"RESPIRATORY"};
        if(n.contains("asthma"))return new String[]{"≋","RESPIRATORY · OBSTRUCTION",null,"RESPIRATORY"};
        if(n.contains("embolism"))return new String[]{"⊗","RESPIRATORY · EMBOLIC",null,"RESPIRATORY"};
        if(n.contains("sepsis"))return new String[]{"☣","SYSTEMIC · SEPSIS",null,"SYSTEMIC"};
        if(n.contains("kawasaki"))return new String[]{"☀⊕","PAEDIATRIC · VASCULITIS","#f59e0b","SYSTEMIC"};
        if(n.contains("ectopic"))return new String[]{"◎","OB-GYN · SURGICAL",null,"SYSTEMIC"};
        if(n.contains("vasovagal"))return new String[]{"↓","NEURO · VASOVAGAL","#16a34a","SYSTEMIC"};
        if(n.contains("musculoskeletal"))return new String[]{"⊞","MSK · CHEST WALL","#16a34a","SYSTEMIC"};
        if(n.contains("intussusception"))return new String[]{"◎","PAEDIATRIC · SURGICAL",null,"SYSTEMIC"};
        return new String[]{"◆","SYSTEMIC",null,"SYSTEMIC"};
    }

    private void showResultPopup(ObjectNode rec) {
        String urgency=rec.path("urgency").asText("SAFE");
        JsonNode dxArr0=rec.path("diagnoses");
        String topMedName="",topCategory="SYSTEMIC",topIcon="◆",topCatLabel="",topAccentOverride=null;
        if(dxArr0.isArray()&&dxArr0.size()>0){JsonNode top=dxArr0.get(0);topMedName=top.path("medicalName").asText("");topCategory=top.path("category").asText("SYSTEMIC");String[]prof=dxProfile(topMedName,urgency);topIcon=prof[0];topCatLabel=prof[1];topAccentOverride=prof[2];}
        String ac=switch(urgency){"EMERGENCY"->DANGER;"SERIOUS"->WARN;default->SUCCESS;};
        String modalAccent=topAccentOverride!=null?topAccentOverride:ac;
        String ht=switch(urgency){"EMERGENCY"->"⚠  EMERGENCY — CRITICAL PATTERN DETECTED";"SERIOUS"->"⚡  SERIOUS — URGENT EVALUATION REQUIRED";default->"✓  LOW ACUITY — ROUTINE INTAKE";};
        setStatus(urgency.equals("EMERGENCY")?"● CRITICAL":"● RESPONSE RECEIVED",ac);

        Rectangle overlay=new Rectangle();
        overlay.setFill(Color.color(0,0,0,0.84));
        overlay.widthProperty().bind(rootStack.widthProperty());
        overlay.heightProperty().bind(rootStack.heightProperty());

        VBox modal=new VBox(0);
        modal.prefWidthProperty().bind(rootStack.widthProperty());
        modal.prefHeightProperty().bind(rootStack.heightProperty());
        modal.setMaxWidth(Double.MAX_VALUE); modal.setMaxHeight(Double.MAX_VALUE);
        modal.setStyle("-fx-background-color:"+PANEL+";-fx-border-color:"+modalAccent+";-fx-border-width:0 0 0 5;-fx-background-radius:0;-fx-border-radius:0;-fx-effect:dropshadow(gaussian,black,60,0.8,0,8);");

        HBox hdr=new HBox(14); hdr.setPadding(new Insets(14,20,14,20)); hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setStyle("-fx-background-color:derive("+modalAccent+",-74%);-fx-background-radius:9 9 0 0;");
        Label iconLbl=mk(topIcon,22,modalAccent,true); iconLbl.setMinWidth(36);
        iconLbl.setStyle(iconLbl.getStyle()+"-fx-background-color:derive("+modalAccent+",-82%);-fx-border-color:derive("+modalAccent+",-55%);-fx-border-width:1;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:4 8 4 8;-fx-alignment:center;");
        VBox hdrText=new VBox(3);
        Label catLbl=mk(topCatLabel.isEmpty()?topCategory:topCatLabel,8,modalAccent,true);
        catLbl.setStyle(catLbl.getStyle()+"-fx-letter-spacing:2px;");
        hdrText.getChildren().addAll(catLbl,mk(ht,13,modalAccent,true));
        hdr.getChildren().addAll(iconLbl,hdrText);
        modal.getChildren().add(hdr);

        HBox strip=new HBox(12); strip.setPadding(new Insets(9,20,9,20)); strip.setAlignment(Pos.CENTER_LEFT);
        strip.setStyle("-fx-background-color:#060810;-fx-border-color:"+BORDER+";-fx-border-width:1 0 1 0;");
        strip.getChildren().addAll(mk(rec.path("name").asText("?"),12,TEXT,true),mk(rec.path("age").asText("?")+"y  ·  "+rec.path("gender").asText("?"),10,MUTED,false));
        String onsetVal=rec.path("onset").asText("UNKNOWN");
        if(!onsetVal.isEmpty()&&!"UNKNOWN".equals(onsetVal)){Label ol=mk("⏱  "+onsetVal,9,WARN,false);ol.setStyle(ol.getStyle()+"-fx-background-color:derive("+WARN+",-86%);-fx-border-color:derive("+WARN+",-40%);-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:2 6 2 6;");strip.getChildren().add(ol);}
        modal.getChildren().add(strip);

        String ccVal=rec.path("chiefComplaint").asText("");
        if(!ccVal.isBlank()){HBox ccBand=new HBox();ccBand.setPadding(new Insets(6,20,6,20));ccBand.setStyle("-fx-background-color:#060810;-fx-border-color:"+BORDER+";-fx-border-width:0 0 1 0;");Label ccl=mk("\""+ccVal+"\"",10,MUTED,false);ccl.setStyle(ccl.getStyle()+"-fx-font-style:italic;");ccl.setWrapText(true);ccBand.getChildren().add(ccl);modal.getChildren().add(ccBand);}

        VBox body=new VBox(10); body.setPadding(new Insets(14,20,10,20));
        JsonNode symsNode=rec.path("symptoms"); Set<String>allSyms=new HashSet<>(); if(symsNode.isArray())for(JsonNode s:symsNode)allSyms.add(s.asText());
        JsonNode dxArr=rec.path("diagnoses");
        if(dxArr.isArray()&&dxArr.size()>0){
            HBox dxHeader=new HBox(8);dxHeader.setAlignment(Pos.CENTER_LEFT);
            Label dxTitle=mk("DIFFERENTIAL DIAGNOSIS",9,MUTED,true);dxTitle.setStyle(dxTitle.getStyle()+"-fx-letter-spacing:2px;");
            dxHeader.getChildren().addAll(dxTitle,mk("  ·  matched symptoms highlighted per card",9,MUTDIM,false));
            body.getChildren().add(dxHeader);
            int[]scores=new int[dxArr.size()];for(int ii=0;ii<dxArr.size();ii++)scores[ii]=dxArr.get(ii).path("score").asInt(1);
            int maxScore=scores[0]>0?scores[0]:1;
            String[]rankLabels={"MOST LIKELY","POSSIBLE","CONSIDER"}; int i=0;
            for(JsonNode dx:dxArr){
                String dxU=dx.path("urgency").asText("SAFE");
                String urgCol=switch(dxU){"EMERGENCY"->DANGER;"SERIOUS"->WARN;default->SUCCESS;};
                String medName=dx.path("medicalName").asText("Unknown");String comName=dx.path("commonName").asText("");String action=dx.path("action").asText("");
                int rank=Math.min(i,2);int score=scores[i];boolean isPrimary=(i==0);
                String[]prof=dxProfile(medName,dxU);String dxIcon=prof[0];String dxCatLbl=prof[1];String dxAccent=prof[2]!=null?prof[2]:urgCol;String motif=prof[3];
                Set<String>matched=new HashSet<>();JsonNode mSyms=dx.path("matchedSymptoms");if(mSyms.isArray())for(JsonNode s:mSyms)matched.add(s.asText());

                VBox dxCard=new VBox(0);
                String borderW=isPrimary?"2":"1";
                String cardBg=isPrimary?"derive("+dxAccent+",-88%)":"derive("+dxAccent+",-93%)";
                dxCard.setStyle("-fx-background-color:"+cardBg+";-fx-border-color:derive("+dxAccent+","+(isPrimary?"-35%":"-55%")+");-fx-border-width:"+borderW+";-fx-border-radius:8;-fx-background-radius:8;");

                HBox topStrip=new HBox(10);topStrip.setPadding(new Insets(8,12,8,12));topStrip.setAlignment(Pos.CENTER_LEFT);
                String topBg=isPrimary?"derive("+dxAccent+",-78%)":"derive("+dxAccent+",-86%)";
                topStrip.setStyle("-fx-background-color:"+topBg+";-fx-border-color:derive("+dxAccent+",-52%);-fx-border-width:0 0 1 0;-fx-background-radius:7 7 0 0;");
                Label dxIconLbl=mk(dxIcon,isPrimary?16:13,dxAccent,true);dxIconLbl.setMinWidth(isPrimary?28:22);dxIconLbl.setStyle(dxIconLbl.getStyle()+"-fx-alignment:center;");

                VBox nameBlock=new VBox(1);HBox.setHgrow(nameBlock,Priority.ALWAYS);
                String dispName=comName.isBlank()?medName:comName;String subName=comName.isBlank()?"":medName;
                Label primName=mk(dispName,isPrimary?13:11,TEXT,isPrimary);primName.setWrapText(true);nameBlock.getChildren().add(primName);
                if(!subName.isBlank()){Label subNameL=mk(subName,9,MUTED,false);subNameL.setWrapText(true);nameBlock.getChildren().add(subNameL);}
                Label catTag=mk(dxCatLbl,8,dxAccent,true);catTag.setStyle(catTag.getStyle()+"-fx-letter-spacing:1px;-fx-background-color:derive("+dxAccent+",-82%);-fx-border-color:derive("+dxAccent+",-58%);-fx-border-width:1;-fx-border-radius:2;-fx-background-radius:2;-fx-padding:1 5 1 5;");nameBlock.getChildren().add(catTag);

                VBox badges=new VBox(4);badges.setAlignment(Pos.CENTER_RIGHT);
                Label rankBadge=mk(rankLabels[rank],8,dxAccent,true);rankBadge.setStyle(rankBadge.getStyle()+"-fx-background-color:derive("+dxAccent+",-72%);-fx-border-color:"+dxAccent+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:2 6 2 6;-fx-letter-spacing:1px;");
                Label urgBadge=mk(dxU,8,urgCol,true);urgBadge.setStyle(urgBadge.getStyle()+"-fx-background-color:derive("+urgCol+",-72%);-fx-border-color:derive("+urgCol+",-30%);-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:2 6 2 6;");
                badges.getChildren().addAll(rankBadge,urgBadge);
                topStrip.getChildren().addAll(dxIconLbl,nameBlock,badges);

                VBox cardBody=new VBox(7);cardBody.setPadding(new Insets(9,14,10,14));
                double barRatio=(double)score/maxScore;
                HBox barRow=new HBox(8);barRow.setAlignment(Pos.CENTER_LEFT);
                Label barLbl=mk("MATCH SCORE",7,MUTDIM,true);barLbl.setStyle(barLbl.getStyle()+"-fx-letter-spacing:1px;");barLbl.setMinWidth(72);
                StackPane barBg=new StackPane();barBg.setPrefHeight(5);barBg.setMaxHeight(5);barBg.setStyle("-fx-background-color:"+BORDER+";-fx-background-radius:3;");HBox.setHgrow(barBg,Priority.ALWAYS);
                Region barFill=new Region();barFill.setPrefHeight(5);barFill.setMaxWidth(Double.MAX_VALUE);barFill.prefWidthProperty().bind(barBg.widthProperty().multiply(barRatio));barFill.setStyle("-fx-background-color:"+dxAccent+";-fx-background-radius:3;");StackPane.setAlignment(barFill,Pos.CENTER_LEFT);barBg.getChildren().add(barFill);
                Label scoreLbl=mk(score+"pt",8,dxAccent,true);scoreLbl.setMinWidth(30);
                barRow.getChildren().addAll(barLbl,barBg,scoreLbl);cardBody.getChildren().add(barRow);

                if(!allSyms.isEmpty()){
                    FlowPane symFlow=new FlowPane(4,4);
                    List<String>orderedSyms=new ArrayList<>(matched);
                    if(isPrimary)for(String s:allSyms)if(!matched.contains(s))orderedSyms.add(s);
                    for(String sym:orderedSyms){boolean hit=matched.contains(sym);Label sl=mk((hit?"✦ ":"○ ")+sym,8,hit?dxAccent:MUTDIM,hit);sl.setStyle(sl.getStyle()+"-fx-background-color:"+(hit?"derive("+dxAccent+",-84%)":"transparent")+";-fx-border-color:"+(hit?"derive("+dxAccent+",-55%)":BORDER)+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:2 6 2 6;");symFlow.getChildren().add(sl);}
                    cardBody.getChildren().add(symFlow);
                }
                String actionPrefix=switch(motif){"CARDIAC"->"♥ ";"NEURO"->"◉ ";"RESPIRATORY"->"≋ ";default->"→ ";};
                Label actLabel=mk(actionPrefix+action,isPrimary?10:9,isPrimary?TEXT:MUTED,false);actLabel.setWrapText(true);actLabel.setStyle(actLabel.getStyle()+"-fx-border-color:derive("+dxAccent+",-62%);-fx-border-width:1 0 0 0;-fx-padding:6 0 0 0;");
                cardBody.getChildren().add(actLabel);
                dxCard.getChildren().addAll(topStrip,cardBody);
                body.getChildren().add(dxCard);
                i++;
            }
        } else {
            body.getChildren().addAll(mk("✓",32,SUCCESS,true),mk("No Critical Pattern Detected",13,SUCCESS,true),mk("No high-acuity cluster matched. Standard monitoring.",11,TEXT,false));
        }
        ScrollPane bodyScroll=new ScrollPane(body);bodyScroll.setFitToWidth(true);bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);bodyScroll.setStyle("-fx-background-color:"+BG+";-fx-background:"+BG+";");VBox.setVgrow(bodyScroll,Priority.ALWAYS);
        modal.getChildren().add(bodyScroll);

        VBox footer=new VBox(8);footer.setPadding(new Insets(4,20,18,20));
        Label disc=mk("[DISCLAIMER] Educational decision-support only. Not for clinical use.",9,MUTED,false);disc.setStyle(disc.getStyle()+"-fx-border-color:"+BORDER+";-fx-border-width:1 0 0 0;-fx-padding:8 0 0 0;");
        Button btnClose=new Button("CLOSE  [ESC]");btnClose.setPrefWidth(118);btnClose.setPrefHeight(36);sBtn(btnClose,ac,urgency.equals("EMERGENCY")?DANGDIM:urgency.equals("SERIOUS")?"#c2410c":"#15803d");
        Button btnSave=new Button("💾  SAVE TO DB");btnSave.setPrefWidth(148);btnSave.setPrefHeight(36);
        final String saveSty="-fx-background-color:derive("+SUCCESS+",-74%);-fx-text-fill:"+SUCCESS+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-font-weight:bold;-fx-cursor:hand;-fx-border-color:"+SUCCESS+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;";
        btnSave.setStyle(saveSty);
        final Button fBtnSave=btnSave;
        fBtnSave.setOnAction(e->{new Thread(()->{try{ObjectNode saveReq=OM.createObjectNode();saveReq.put("action","SAVE");saveReq.set("record",rec);sendToServer(OM.writeValueAsString(saveReq));Platform.runLater(()->{fBtnSave.setText("✓  SAVED");fBtnSave.setDisable(true);fBtnSave.setStyle(saveSty.replace(SUCCESS,MUTED));});}catch(Exception ex){Platform.runLater(()->{fBtnSave.setText("✗  FAILED");fBtnSave.setStyle(saveSty.replace(SUCCESS,DANGER));});}}).start();});
        Button btnCopy=new Button("COPY REPORT  📋");btnCopy.setPrefWidth(140);btnCopy.setPrefHeight(36);btnCopy.setStyle("-fx-background-color:transparent;-fx-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:10px;-fx-cursor:hand;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;");
        final Button fBtnCopy=btnCopy;fBtnCopy.setOnAction(e->{copyClip(buildReport(rec));fBtnCopy.setText("✓ COPIED!");});
        HBox fRow=new HBox(10,btnClose,btnSave,btnCopy);fRow.setAlignment(Pos.CENTER_LEFT);
        footer.getChildren().addAll(disc,fRow);modal.getChildren().add(footer);

        modal.setOpacity(0);modal.setScaleX(0.92);modal.setScaleY(0.92);
        FadeTransition fadeIn=new FadeTransition(Duration.millis(180),modal);fadeIn.setToValue(1);fadeIn.play();
        ScaleTransition scaleIn=new ScaleTransition(Duration.millis(180),modal);scaleIn.setToX(1);scaleIn.setToY(1);scaleIn.play();

        StackPane popupPane=new StackPane(overlay,modal);StackPane.setAlignment(modal,Pos.TOP_LEFT);
        rootStack.getChildren().add(popupPane);
        Runnable close=()->{FadeTransition ft=new FadeTransition(Duration.millis(140),popupPane);ft.setToValue(0);ft.setOnFinished(ev->rootStack.getChildren().remove(popupPane));ft.play();};
        btnClose.setOnAction(ev->close.run());overlay.setOnMouseClicked(ev->close.run());
    }

    private void refreshTable() {
        if(dbStatusLabel==null||patientTable==null)return;
        dbStatusLabel.setText("loading...");
        new Thread(()->{
            try{String raw=sendToServer("{\"action\":\"QUERY_ALL\"}");JsonNode arr=OM.readTree(raw);
                Platform.runLater(()->{allRecords.clear();if(arr.isArray())for(JsonNode n:arr)allRecords.add(new PatientRow((ObjectNode)n));applyFilter();});
            }catch(Exception ex){Platform.runLater(()->{dbStatusLabel.setText("SERVER OFFLINE");dbStatusLabel.setStyle("-fx-text-fill:"+DANGER+";-fx-font-family:"+MONO+";-fx-font-size:10px;");});}
        }).start();
    }

    private void applyFilter() {
        String query=dbSearchField!=null?dbSearchField.getText().trim().toLowerCase():"";
        Toggle sel=dbFilterGroup!=null?dbFilterGroup.getSelectedToggle():null;
        String filter="ALL";if(sel instanceof ToggleButton tb2)filter=tb2.getText().trim();
        final String fFilter=filter;
        patientTable.getItems().clear();int shown=0;
        for(PatientRow row:allRecords){
            if(!"ALL".equals(fFilter)&&!fFilter.equals(row.getUrgency()))continue;
            if(!query.isEmpty()){boolean nameMatch=row.getName().toLowerCase().contains(query);boolean dxMatch=row.getTopDx().toLowerCase().contains(query);boolean idMatch=row.getId().toLowerCase().contains(query);boolean ageMatch=String.valueOf(row.getAge()).contains(query);boolean genderMatch=row.getGender().toLowerCase().contains(query);boolean onsetMatch=row.getOnset().toLowerCase().contains(query);boolean symMatch=false;JsonNode syms=row.getRaw().path("symptoms");if(syms.isArray()){for(JsonNode s:syms){if(s.asText().toLowerCase().contains(query)){symMatch=true;break;}}}if(!nameMatch&&!dxMatch&&!idMatch&&!ageMatch&&!genderMatch&&!onsetMatch&&!symMatch)continue;}
            patientTable.getItems().add(row);shown++;
        }
        int total=allRecords.size();String statusTxt=shown==total?total+" record"+(total!=1?"s":""):shown+" of "+total+" records";
        dbStatusLabel.setText(statusTxt);dbStatusLabel.setStyle("-fx-text-fill:"+(total==0?MUTED:SUCCESS)+";-fx-font-family:"+MONO+";-fx-font-size:10px;");
    }

    private void fillDetailPane(ObjectNode rec,VBox pane){
        pane.getChildren().clear();String urg=rec.path("urgency").asText("SAFE");String uc=switch(urg){"EMERGENCY"->DANGER;"SERIOUS"->WARN;default->SUCCESS;};
        HBox dHdr=new HBox(12);dHdr.setPadding(new Insets(7,16,7,16));dHdr.setAlignment(Pos.CENTER_LEFT);dHdr.setStyle("-fx-background-color:derive("+uc+",-88%);-fx-border-color:"+uc+";-fx-border-width:0 0 1 0;");
        dHdr.getChildren().addAll(mk(rec.path("id").asText("—"),10,uc,true),mk(rec.path("name").asText("?"),13,TEXT,true),mk(rec.path("age").asText("?")+"y  ·  "+rec.path("gender").asText("?"),10,MUTED,false),mk("Saved: "+rec.path("savedAt").asText("?"),9,MUTED,false));
        pane.getChildren().add(dHdr);
        HBox cols=new HBox(0);HBox.setHgrow(cols,Priority.ALWAYS);
        VBox left=new VBox(6);left.setPadding(new Insets(8,12,8,16));HBox.setHgrow(left,Priority.ALWAYS);left.getChildren().add(mk("PRESENTATION",9,MUTED,true));
        String cc=rec.path("chiefComplaint").asText("");if(!cc.isBlank()){Label ccl=mk("\""+cc+"\"",10,TEXT,false);ccl.setStyle(ccl.getStyle()+"-fx-font-style:italic;");ccl.setWrapText(true);left.getChildren().add(ccl);}
        left.getChildren().add(mk("Onset: "+rec.path("onset").asText("?"),10,MUTED,false));
        JsonNode syms=rec.path("symptoms");if(syms.isArray()){StringBuilder sb=new StringBuilder();for(JsonNode s:syms){if(sb.length()>0)sb.append("  ·  ");sb.append(s.asText());}Label sl=mk(sb.toString(),10,ACCENT,false);sl.setWrapText(true);left.getChildren().add(sl);}
        VBox right=new VBox(5);right.setPadding(new Insets(8,16,8,14));right.setMinWidth(370);right.setMaxWidth(370);right.setStyle("-fx-border-color:"+BORDER+";-fx-border-width:0 0 0 1;");right.getChildren().add(mk("DIFFERENTIAL DIAGNOSIS",9,MUTED,true));
        JsonNode dxArr=rec.path("diagnoses");String[]ltt={"A","B","C"};int i=0;
        if(dxArr.isArray())for(JsonNode dx:dxArr){String dxU=dx.path("urgency").asText("SAFE");String col=switch(dxU){"EMERGENCY"->DANGER;"SERIOUS"->WARN;default->SUCCESS;};HBox dRow=new HBox(8);dRow.setAlignment(Pos.CENTER_LEFT);dRow.setPadding(new Insets(4,8,4,8));dRow.setStyle("-fx-background-color:derive("+col+",-87%);-fx-border-color:derive("+col+",-54%);-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;");Label lt=mk(i<ltt.length?ltt[i]:"?",11,col,true);lt.setMinWidth(14);VBox dt=new VBox(1);HBox.setHgrow(dt,Priority.ALWAYS);{String mn=dx.path("medicalName").asText(dx.path("name").asText("?"));String cn=dx.path("commonName").asText("");String fn=cn.isBlank()?mn:mn+" ("+cn+")";Label nl=mk(fn,10,TEXT,i==0);nl.setWrapText(true);dt.getChildren().add(nl);}Label al=mk(dx.path("action").asText(""),9,MUTED,false);al.setWrapText(true);dt.getChildren().add(al);dRow.getChildren().addAll(lt,dt);right.getChildren().add(dRow);i++;}
        Label tL=mk("FINAL TRIAGE: "+urg,9,uc,true);tL.setStyle(tL.getStyle()+"-fx-background-color:derive("+uc+",-84%);-fx-border-color:"+uc+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-padding:3 8 3 8;");right.getChildren().add(tL);
        cols.getChildren().addAll(left,right);pane.getChildren().add(cols);
    }

    private String buildReport(ObjectNode rec){
        StringBuilder sb=new StringBuilder();
        sb.append("══════════════════════════════════════════\n  EIS v5.0 · PATIENT REPORT\n══════════════════════════════════════════\n");
        sb.append("ID       : ").append(rec.path("id").asText("—")).append("\n");
        sb.append("Saved At : ").append(rec.path("savedAt").asText("—")).append("\n\nPATIENT\n");
        sb.append("  Name   : ").append(rec.path("name").asText("?")).append("\n");
        sb.append("  Age    : ").append(rec.path("age").asText("?")).append(" yrs\n");
        sb.append("  Gender : ").append(rec.path("gender").asText("?")).append("\n\n");
        if(!rec.path("chiefComplaint").asText("").isBlank())sb.append("CHIEF COMPLAINT\n  ").append(rec.path("chiefComplaint").asText()).append("\n\n");
        sb.append("ONSET    : ").append(rec.path("onset").asText("?")).append("\n");
        sb.append("SYMPTOMS : ");JsonNode s=rec.path("symptoms");if(s.isArray()){List<String>l=new ArrayList<>();for(JsonNode n:s)l.add(n.asText());sb.append(String.join(", ",l));}
        sb.append("\n\nDIFFERENTIAL DIAGNOSIS\n");
        JsonNode dx=rec.path("diagnoses");String[]ltt={"A","B","C"};int i=0;
        if(dx.isArray()&&dx.size()>0)for(JsonNode d:dx){String mn2=d.path("medicalName").asText(d.path("name").asText("?"));String cn2=d.path("commonName").asText("");String fn2=cn2.isBlank()?mn2:mn2+" ("+cn2+")";sb.append("  ").append(i<ltt.length?ltt[i]:"?").append(". [").append(d.path("urgency").asText()).append("] ").append(fn2).append("\n     → ").append(d.path("action").asText()).append("\n");i++;}
        else sb.append("  No critical pattern.\n");
        sb.append("\nFINAL TRIAGE : ").append(rec.path("urgency").asText("SAFE")).append("\n══════════════════════════════════════════\n[DISCLAIMER] Educational demo only · Not for clinical use\n");
        return sb.toString();
    }

    private void copyClip(String text){try{StringSelection sel=new StringSelection(text);Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel,sel);}catch(Exception ignored){}}
    private void clearForm(){nameField.clear();complaintField.clear();ageSlider.setValue(30);if(!genderGroup.getToggles().isEmpty())genderGroup.getToggles().get(0).setSelected(true);if(onsetGroup.getSelectedToggle()!=null)onsetGroup.getSelectedToggle().setSelected(false);for(ToggleButton tb:symptomBtns)tb.setSelected(false);setStatus("● READY",SUCCESS);}
    private void setStatus(String t,String c){Platform.runLater(()->{statusLabel.setText(t);statusLabel.setStyle("-fx-text-fill:"+c+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-font-weight:bold;");});}
    private void popup(String title,String msg,String color){Rectangle overlay=new Rectangle();overlay.setFill(Color.color(0,0,0,0.66));overlay.widthProperty().bind(rootStack.widthProperty());overlay.heightProperty().bind(rootStack.heightProperty());VBox modal=new VBox(12);modal.setPadding(new Insets(20,24,20,24));modal.setMaxWidth(340);modal.setStyle("-fx-background-color:"+PANEL+";-fx-border-color:"+color+";-fx-border-width:1 1 1 4;-fx-background-radius:6;-fx-border-radius:6;-fx-effect:dropshadow(gaussian,black,30,0.5,0,2);");Label ml=mk(msg,11,TEXT,false);ml.setWrapText(true);Button ok=new Button("OK  [ESC]");ok.setPrefWidth(82);ok.setPrefHeight(30);sBtn(ok,color,ACCENT);modal.getChildren().addAll(mk(title,12,color,true),ml,ok);StackPane p=new StackPane(overlay,modal);StackPane.setAlignment(modal,Pos.CENTER);rootStack.getChildren().add(p);Runnable close=()->rootStack.getChildren().remove(p);ok.setOnAction(e->close.run());overlay.setOnMouseClicked(e->close.run());}
    private void startClock(){DateTimeFormatter fmt=DateTimeFormatter.ofPattern("HH:mm:ss");Timeline tl=new Timeline(new KeyFrame(Duration.seconds(1),e->clockLabel.setText(LocalTime.now().format(fmt))));tl.setCycleCount(Animation.INDEFINITE);tl.play();clockLabel.setText(LocalTime.now().format(fmt));}

    private Label mk(String t,int s,String c,boolean b){Label l=new Label(t);l.setWrapText(true);l.setStyle("-fx-text-fill:"+c+";-fx-font-family:"+MONO+";-fx-font-size:"+s+"px;"+(b?"-fx-font-weight:bold;":""));return l;}
    private Label fLbl(String t){return mk(t,11,MUTED,false);}
    private Label secLbl(String t){Label l=mk(t,10,MUTED,true);l.setStyle(l.getStyle()+"-fx-letter-spacing:2px;");return l;}
    private Separator div(){Separator s=new Separator();s.setStyle("-fx-background-color:"+BORDER+";");VBox.setMargin(s,new Insets(2,0,2,0));return s;}
    private String cardSty(){return "-fx-background-color:"+PANEL+";-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-background-radius:6;-fx-border-radius:6;-fx-padding:14;";}
    private void styleField(TextField tf){tf.setStyle("-fx-background-color:"+PANEL+";-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-text-fill:"+TEXT+";-fx-prompt-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:12px;-fx-pref-height:34px;");}
    private void styleArea(TextArea ta){ta.setStyle("-fx-background-color:"+PANEL+";-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:3;-fx-background-radius:3;-fx-text-fill:"+TEXT+";-fx-prompt-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:11px;");}
    private void sBtn(Button b,String bg,String hov){String s="-fx-background-color:"+bg+";-fx-text-fill:white;-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-font-weight:bold;-fx-cursor:hand;-fx-background-radius:4;";b.setStyle(s);b.setOnMouseEntered(e->b.setStyle(s.replace(bg,hov)));b.setOnMouseExited(e->b.setStyle(s));}
    private ToggleButton ribbonBtn(String lbl,ToggleGroup tg){ToggleButton tb=new ToggleButton(lbl);tb.setToggleGroup(tg);tb.setPrefWidth(88);tb.setPrefHeight(30);String off="-fx-background-color:"+PANEL+";-fx-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;-fx-cursor:hand;";String on="-fx-background-color:"+ACCENT+";-fx-text-fill:white;-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-font-weight:bold;-fx-border-color:"+ACCENT+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;-fx-cursor:hand;";tb.setStyle(off);tb.selectedProperty().addListener((o,v,n)->tb.setStyle(n?on:off));return tb;}
    private void onsetSty(ToggleButton tb,boolean on){tb.setStyle(on?"-fx-background-color:derive("+WARN+",-76%);-fx-text-fill:"+WARN+";-fx-font-family:"+MONO+";-fx-font-size:9px;-fx-font-weight:bold;-fx-border-color:"+WARN+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;-fx-cursor:hand;":"-fx-background-color:"+PANEL+";-fx-text-fill:"+MUTED+";-fx-font-family:"+MONO+";-fx-font-size:9px;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-border-radius:4;-fx-background-radius:4;-fx-cursor:hand;");}
    private ToggleButton symCard(String name,String icon){ToggleButton tb=new ToggleButton(icon+"\n"+name);tb.setMaxWidth(Double.MAX_VALUE);tb.setPrefHeight(58);String off="-fx-background-color:"+PANEL+";-fx-text-fill:"+TEXT+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-alignment:center;-fx-background-radius:4;-fx-border-radius:4;-fx-border-color:"+BORDER+";-fx-border-width:1;-fx-cursor:hand;-fx-padding:8 6;";String on="-fx-background-color:#0c1628;-fx-text-fill:"+TEXT+";-fx-font-family:"+MONO+";-fx-font-size:11px;-fx-font-weight:bold;-fx-alignment:center;-fx-background-radius:4;-fx-border-radius:4;-fx-border-color:"+ACCENT+";-fx-border-width:1.5;-fx-cursor:hand;-fx-padding:8 6;";tb.setStyle(off);tb.selectedProperty().addListener((o,v,n)->tb.setStyle(n?on:off));return tb;}
    private GridPane g2col(String lbl,javafx.scene.Node field,double lw,double fw){GridPane g=new GridPane();g.setHgap(10);g.getColumnConstraints().addAll(new ColumnConstraints(lw),new ColumnConstraints(fw));g.add(fLbl(lbl),0,0);g.add(field,1,0);return g;}
    private <T> TableColumn<PatientRow,T> tCol(String title,String prop,int min,int pref){TableColumn<PatientRow,T>c=new TableColumn<>(title);c.setCellValueFactory(new PropertyValueFactory<>(prop));c.setMinWidth(min);c.setPrefWidth(pref);return c;}

    private void applyCSS(Scene scene){
        String raw=".tab-pane .tab-header-area .tab-header-background{-fx-background-color:#090b0e;-fx-border-color:#1e232d;-fx-border-width:0 0 1 0;}"+".tab-pane .tab{-fx-background-color:#090b0e;-fx-border-color:transparent;-fx-padding:7 16;}"+".tab-pane .tab:selected{-fx-background-color:#11141a;-fx-border-color:#2563eb;-fx-border-width:0 0 2 0;}"+".tab-pane .tab .tab-label{-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;}"+".tab-pane .tab:selected .tab-label{-fx-text-fill:#f8fafc;}"+".tab-pane .tab-content-area{-fx-background-color:#090b0e;}"+".table-view{-fx-background-color:#090b0e;-fx-border-color:#1e232d;}"+".table-view .column-header-background{-fx-background-color:#11141a;}"+".table-view .column-header,.table-view .filler{-fx-background-color:#11141a;-fx-border-color:#1e232d;-fx-border-width:0 1 1 0;-fx-size:32px;}"+".table-view .column-header .label{-fx-text-fill:#94a3b8;-fx-font-size:9px;-fx-font-weight:bold;-fx-alignment:CENTER_LEFT;-fx-font-family:'JetBrains Mono','Courier New',monospace;}"+".table-row-cell{-fx-background-color:#090b0e;-fx-border-color:#1e232d;-fx-border-width:0 0 1 0;-fx-cell-size:30px;}"+".table-row-cell:odd{-fx-background-color:#0c0f14;}"+".table-row-cell:selected{-fx-background-color:#111d38;}"+".table-row-cell .table-cell{-fx-text-fill:#f8fafc;-fx-font-size:10px;-fx-padding:0 6;-fx-font-family:'JetBrains Mono','Courier New',monospace;}"+".scroll-bar{-fx-background-color:#090b0e;}"+".scroll-bar .thumb{-fx-background-color:#1e232d;-fx-background-radius:2;}"+".scroll-bar .track,.scroll-bar .increment-button,.scroll-bar .decrement-button{-fx-background-color:#090b0e;}"+".text-field{-fx-prompt-text-fill:#475569;}"+".text-area .content{-fx-background-color:#11141a;}"+".text-area{-fx-background-color:#11141a;-fx-background-radius:3;}";
        String enc=raw.replace("%","%25").replace(" ","%20").replace("#","%23").replace("'","%27").replace("(","%28").replace(")","%29");
        scene.getStylesheets().add("data:text/css,"+enc);
    }

    public static void main(String[] args){launch(args);}
}

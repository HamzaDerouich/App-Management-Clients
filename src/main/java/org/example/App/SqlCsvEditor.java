package org.example.App;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class SqlCsvEditor extends JFrame {
    // Componentes de la interfaz
    private JTable table;
    private DefaultTableModel tableModel;
    private List<String[]> data = new ArrayList<>();
    private String[] headers;
    private File currentFile;
    private JLabel statusLabel;
    private JComboBox<String> delimiterCombo;
    private JComboBox<String> sqlQueryTypeCombo;
    private JTextArea sqlQueryArea;
    private String tableName = "mi_tabla";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new SqlCsvEditor().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public SqlCsvEditor() {
        configureWindow();
        setupToolbar();
        setupTable();
        setupSqlPanel();
        setupStatusBar();
    }

    private void configureWindow() {
        setTitle("Editor CSV con Generador SQL");
        setSize(1300, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void setupToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        // Operaciones de archivo
        toolBar.add(createButton("Abrir", "Abrir archivo CSV", e -> openFile()));
        toolBar.add(createButton("Guardar", "Guardar archivo CSV", e -> saveFile()));
        toolBar.addSeparator();

        // Selector de delimitador
        toolBar.add(new JLabel("Delimitador:"));
        delimiterCombo = new JComboBox<>(new String[]{",", ";", "|", "\t"});
        toolBar.add(delimiterCombo);
        toolBar.addSeparator();

        // Operaciones de datos
        toolBar.add(createButton("+ Fila", "Añadir nueva fila", e -> showAddRowDialog()));
        toolBar.add(createButton("- Fila", "Eliminar fila seleccionada", e -> deleteRow()));
        toolBar.addSeparator();

        // Nombre de tabla SQL
        toolBar.add(new JLabel("Tabla SQL:"));
        JTextField tableNameField = new JTextField(tableName, 10);
        tableNameField.addActionListener(e -> {
            tableName = tableNameField.getText().trim();
            generateSqlQuery();
        });
        toolBar.add(tableNameField);

        add(toolBar, BorderLayout.NORTH);
    }

    private JButton createButton(String text, String tooltip, ActionListener action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.addActionListener(action);
        return button;
    }

    private void setupTable() {
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> generateSqlQuery());

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupSqlPanel() {
        JPanel sqlPanel = new JPanel(new BorderLayout());
        sqlPanel.setBorder(BorderFactory.createTitledBorder("Generador SQL"));
        sqlPanel.setPreferredSize(new Dimension(400, 0));

        // Panel superior - Tipo de consulta
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Consulta:"));
        sqlQueryTypeCombo = new JComboBox<>(new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"});
        sqlQueryTypeCombo.addActionListener(e -> generateSqlQuery());
        topPanel.add(sqlQueryTypeCombo);

        // Botones de acción
        JButton copyBtn = new JButton("Copiar");
        copyBtn.addActionListener(e -> copyToClipboard());
        topPanel.add(copyBtn);

        sqlPanel.add(topPanel, BorderLayout.NORTH);

        // Área de consulta SQL
        sqlQueryArea = new JTextArea();
        sqlQueryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sqlQueryArea.setEditable(false);
        sqlPanel.add(new JScrollPane(sqlQueryArea), BorderLayout.CENTER);

        add(sqlPanel, BorderLayout.EAST);
    }

    private void setupStatusBar() {
        statusLabel = new JLabel("Listo");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            loadCsvFile();
        }
    }

    private void loadCsvFile() {
        String delimiter = delimiterCombo.getSelectedItem().toString();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(currentFile), StandardCharsets.UTF_8))) {

            // Leer cabeceras
            String headerLine = br.readLine();
            if (headerLine == null) {
                showError("Archivo vacío");
                return;
            }

            headers = headerLine.split(delimiter);
            tableModel.setColumnIdentifiers(headers);

            // Leer datos
            data.clear();
            String line;
            while ((line = br.readLine()) != null) {
                String[] row = line.split(delimiter, -1);
                data.add(row);
                tableModel.addRow(row);
            }

            updateStatus("Cargadas " + data.size() + " filas de " + currentFile.getName());

        } catch (Exception ex) {
            showError("Error al leer archivo: " + ex.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveAsFile();
            return;
        }
        saveToCsvFile(currentFile);
    }

    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            currentFile = file;
            saveToCsvFile(file);
        }
    }

    private void saveToCsvFile(File file) {
        String delimiter = delimiterCombo.getSelectedItem().toString();

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // Escribir cabeceras
            bw.write(String.join(delimiter, headers));
            bw.newLine();

            // Escribir datos
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String[] row = new String[tableModel.getColumnCount()];
                for (int j = 0; j < row.length; j++) {
                    Object value = tableModel.getValueAt(i, j);
                    row[j] = (value != null) ? value.toString() : "";
                }
                bw.write(String.join(delimiter, row));
                bw.newLine();
            }

            updateStatus("Datos guardados en " + file.getName());

        } catch (Exception ex) {
            showError("Error al guardar: " + ex.getMessage());
        }
    }

    private void showAddRowDialog() {
        if (headers == null) {
            showError("No hay datos cargados");
            return;
        }

        JPanel panel = new JPanel(new GridLayout(headers.length, 2, 5, 5));
        JTextField[] fields = new JTextField[headers.length];

        for (int i = 0; i < headers.length; i++) {
            panel.add(new JLabel(headers[i]));
            fields[i] = new JTextField();
            panel.add(fields[i]);
        }

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Añadir Nueva Fila", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String[] newRow = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                newRow[i] = fields[i].getText();
            }
            data.add(newRow);
            tableModel.addRow(newRow);
            updateStatus("Fila añadida");
        }
    }

    private void deleteRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showError("Selecciona una fila para eliminar");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        tableModel.removeRow(modelRow);
        data.remove(modelRow);
        updateStatus("Fila eliminada");
    }

    private void generateSqlQuery() {
        if (headers == null || headers.length == 0) {
            sqlQueryArea.setText("-- No hay datos cargados");
            return;
        }

        String queryType = (String) sqlQueryTypeCombo.getSelectedItem();
        int selectedRow = table.getSelectedRow();
        int modelRow = selectedRow >= 0 ? table.convertRowIndexToModel(selectedRow) : -1;

        switch (queryType) {
            case "SELECT":
                sqlQueryArea.setText(generateSelect());
                break;
            case "INSERT":
                sqlQueryArea.setText(generateInsert());
                break;
            case "UPDATE":
                sqlQueryArea.setText(modelRow >= 0 ?
                        generateUpdate(modelRow) : "-- Selecciona una fila para UPDATE");
                break;
            case "DELETE":
                sqlQueryArea.setText(modelRow >= 0 ?
                        generateDelete(modelRow) : "-- Selecciona una fila para DELETE");
                break;
        }
    }

    private String generateSelect() {
        return String.format(
                "SELECT %s\nFROM %s;\n\n-- Ejemplo con WHERE:\nSELECT *\nFROM %s\nWHERE %s = 'valor';",
                String.join(", ", headers), tableName, tableName, headers[0]);
    }

    private String generateInsert() {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(tableName).append(" (")
                .append(String.join(", ", headers)).append(")\nVALUES (");

        for (int i = 0; i < headers.length; i++) {
            sb.append("?");
            if (i < headers.length - 1) sb.append(", ");
        }
        sb.append(");\n\n");

        if (!data.isEmpty()) {
            sb.append("-- Ejemplo con valores:\nINSERT INTO ").append(tableName)
                    .append(" (").append(String.join(", ", headers)).append(")\nVALUES (");

            for (int i = 0; i < data.get(0).length; i++) {
                sb.append("'").append(data.get(0)[i] != null ? data.get(0)[i] : "").append("'");
                if (i < data.get(0).length - 1) sb.append(", ");
            }
            sb.append(");");
        }

        return sb.toString();
    }

    private String generateUpdate(int rowIndex) {
        String[] row = data.get(rowIndex);
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append("\nSET ");

        for (int i = 0; i < headers.length; i++) {
            sb.append(headers[i]).append(" = ?");
            if (i < headers.length - 1) sb.append(",\n    ");
        }

        sb.append("\nWHERE ").append(headers[0]).append(" = '").append(row[0]).append("';\n\n");

        sb.append("-- Ejemplo completo:\nUPDATE ").append(tableName).append("\nSET ");

        for (int i = 0; i < headers.length; i++) {
            sb.append(headers[i]).append(" = '").append(row[i] != null ? row[i] : "").append("'");
            if (i < headers.length - 1) sb.append(",\n    ");
        }

        sb.append("\nWHERE ").append(headers[0]).append(" = '").append(row[0]).append("';");

        return sb.toString();
    }

    private String generateDelete(int rowIndex) {
        String[] row = data.get(rowIndex);
        return String.format(
                "DELETE FROM %s\nWHERE %s = '%s';\n\n-- Versión más segura:\nDELETE FROM %s\nWHERE %s = '%s'\nAND %s = '%s';",
                tableName, headers[0], row[0],
                tableName, headers[0], row[0],
                headers[1], row.length > 1 ? row[1] : "");
    }

    private void copyToClipboard() {
        StringSelection selection = new StringSelection(sqlQueryArea.getText());
        //Clipboard.getSystemClipboard().setContents(selection, null);
        updateStatus("Consulta copiada al portapapeles");
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + message);
    }
}
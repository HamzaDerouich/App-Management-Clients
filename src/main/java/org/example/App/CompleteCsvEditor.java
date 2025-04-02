package org.example.App;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.Font;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class CompleteCsvEditor extends JFrame {
    // UI Components
    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private List<String[]> data = new ArrayList<>();
    private String[] headers;
    private File currentFile;
    private JLabel statusLabel;
    private JComboBox<String> delimiterCombo;
    private JComboBox<String> sqlQueryTypeCombo;
    private JTextArea sqlQueryArea;
    private JList<String> exportFormatList;
    private JTextArea previewArea;
    private String tableName = "my_table";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new CompleteCsvEditor().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public CompleteCsvEditor() {
        configureWindow();
        setupToolbar();
        setupTable();
        setupRightPanel();
        setupStatusBar();
    }

    private void configureWindow() {
        setTitle("Complete CSV Editor");
        setSize(1500, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void setupToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(createButton("Open", "Open CSV file", e -> openFile()));
        toolBar.add(createButton("Save", "Save CSV file", e -> saveFile()));
        toolBar.addSeparator();

        toolBar.add(new JLabel("Delimiter:"));
        delimiterCombo = new JComboBox<>(new String[]{",", ";", "|", "\t"});
        toolBar.add(delimiterCombo);
        toolBar.addSeparator();

        toolBar.add(createButton("Filter", "Apply filters", e -> showFilterDialog()));
        toolBar.add(createButton("Sort", "Sort by column", e -> showSortDialog()));
        toolBar.addSeparator();

        toolBar.add(createButton("+ Row", "Add new row", e -> showAddRowDialog()));
        toolBar.add(createButton("- Row", "Delete selected row", e -> deleteRow()));
        toolBar.addSeparator();

        toolBar.add(new JLabel("SQL Table:"));
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
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        };

        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                generateSqlQuery();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void showFilterDialog() {
        if (headers == null || headers.length == 0) {
            showError("No data loaded to filter");
            return;
        }

        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));

        JComboBox<String> columnCombo = new JComboBox<>(headers);
        JComboBox<String> operatorCombo = new JComboBox<>(new String[]{
                "contains", "equals", "starts with", "ends with", "is empty", "is not empty"
        });
        JTextField valueField = new JTextField();

        panel.add(new JLabel("Column:"));
        panel.add(columnCombo);
        panel.add(new JLabel("Operator:"));
        panel.add(operatorCombo);
        panel.add(new JLabel("Value:"));
        panel.add(valueField);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Filter Data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String column = (String) columnCombo.getSelectedItem();
            String operator = (String) operatorCombo.getSelectedItem();
            String value = valueField.getText().toLowerCase();

            int columnIndex = Arrays.asList(headers).indexOf(column);
            applyFilter(columnIndex, operator, value);
        }
    }

    private void applyFilter(int columnIndex, String operator, String value) {
        RowFilter<DefaultTableModel, Object> filter = new RowFilter<DefaultTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                String cellValue = entry.getStringValue(columnIndex).toLowerCase();

                switch (operator) {
                    case "contains":
                        return cellValue.contains(value);
                    case "equals":
                        return cellValue.equals(value);
                    case "starts with":
                        return cellValue.startsWith(value);
                    case "ends with":
                        return cellValue.endsWith(value);
                    case "is empty":
                        return cellValue.trim().isEmpty();
                    case "is not empty":
                        return !cellValue.trim().isEmpty();
                    default:
                        return true;
                }
            }
        };

        sorter.setRowFilter(filter);
        updateStatus("Filter applied: " + operator + " '" + value + "'");
    }

    private void showSortDialog() {
        if (headers == null || headers.length == 0) {
            showError("No data loaded to sort");
            return;
        }

        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));

        JComboBox<String> columnCombo = new JComboBox<>(headers);
        JComboBox<String> orderCombo = new JComboBox<>(new String[]{"Ascending", "Descending"});

        panel.add(new JLabel("Column:"));
        panel.add(columnCombo);
        panel.add(new JLabel("Order:"));
        panel.add(orderCombo);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Sort Data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String column = (String) columnCombo.getSelectedItem();
            boolean ascending = orderCombo.getSelectedItem().equals("Ascending");
            applySort(column, ascending);
        }
    }

    private void applySort(String column, boolean ascending) {
        int columnIndex = Arrays.asList(headers).indexOf(column);
        if (columnIndex >= 0) {
            List<RowSorter.SortKey> sortKeys = new ArrayList<>();
            sortKeys.add(new RowSorter.SortKey(columnIndex, ascending ? SortOrder.ASCENDING : SortOrder.DESCENDING));
            sorter.setSortKeys(sortKeys);
            sorter.sort();

            updateStatus("Data sorted by " + column + " (" +
                    (ascending ? "ascending" : "descending") + ")");
        }
    }

    private void setupRightPanel() {
        JPanel rightPanel = new JPanel(new GridLayout(3, 1));
        rightPanel.setPreferredSize(new Dimension(450, 0));

        // Export panel
        JPanel exportPanel = new JPanel(new BorderLayout());
        exportPanel.setBorder(BorderFactory.createTitledBorder("Export To"));

        String[] formats = {"JSON", "XML", "SQL", "Excel"};
        exportFormatList = new JList<>(formats);
        exportFormatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton previewButton = new JButton("Preview");
        previewButton.addActionListener(e -> previewSelectedFormat());

        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportSelectedFormat());

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(previewButton);
        buttonPanel.add(exportButton);

        exportPanel.add(new JScrollPane(exportFormatList), BorderLayout.CENTER);
        exportPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Preview panel
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));

        previewArea = new JTextArea();
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setEditable(false);
        previewPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        // SQL panel
        JPanel sqlPanel = new JPanel(new BorderLayout());
        sqlPanel.setBorder(BorderFactory.createTitledBorder("SQL Generator"));

        JPanel sqlTopPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sqlTopPanel.add(new JLabel("Query:"));
        sqlQueryTypeCombo = new JComboBox<>(new String[]{"SELECT", "INSERT", "UPDATE", "DELETE"});
        sqlQueryTypeCombo.addActionListener(e -> generateSqlQuery());
        sqlTopPanel.add(sqlQueryTypeCombo);

        JButton copyButton = new JButton("Copy SQL");
        copyButton.addActionListener(e -> copyToClipboard());
        sqlTopPanel.add(copyButton);

        sqlPanel.add(sqlTopPanel, BorderLayout.NORTH);

        sqlQueryArea = new JTextArea();
        sqlQueryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sqlQueryArea.setEditable(false);
        sqlPanel.add(new JScrollPane(sqlQueryArea), BorderLayout.CENTER);

        rightPanel.add(exportPanel);
        rightPanel.add(previewPanel);
        rightPanel.add(sqlPanel);

        add(rightPanel, BorderLayout.EAST);
    }

    private void previewSelectedFormat() {
        String selectedFormat = exportFormatList.getSelectedValue();
        if (selectedFormat == null) {
            showError("Select an export format");
            return;
        }

        switch (selectedFormat) {
            case "JSON":
                previewArea.setText(generateJson());
                break;
            case "XML":
                previewArea.setText(generateXml());
                break;
            case "SQL":
                previewArea.setText(generateSqlExport());
                break;
            case "Excel":
                previewArea.setText("(Binary) Preview not available for Excel");
                break;
        }
    }

    private void exportSelectedFormat() {
        String selectedFormat = exportFormatList.getSelectedValue();
        if (selectedFormat == null) {
            showError("Select an export format");
            return;
        }

        switch (selectedFormat) {
            case "JSON":
                exportToJson();
                break;
            case "XML":
                exportToXml();
                break;
            case "SQL":
                exportToSql();
                break;
            case "Excel":
                exportToExcel();
                break;
        }
    }

    private String generateJson() {
        if (data.isEmpty()) return "No data to export";

        StringBuilder json = new StringBuilder();
        json.append("[\n");

        for (int i = 0; i < data.size(); i++) {
            json.append("  {\n");
            for (int j = 0; j < headers.length; j++) {
                String value = data.get(i)[j] != null ? escapeJson(data.get(i)[j]) : "";
                json.append(String.format("    \"%s\": \"%s\"", headers[j], value));
                if (j < headers.length - 1) json.append(",");
                json.append("\n");
            }
            json.append("  }");
            if (i < data.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("]");
        return json.toString();
    }

    private String generateXml() {
        if (data.isEmpty()) return "No data to export";

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<data>\n");

        for (String[] row : data) {
            xml.append("  <row>\n");
            for (int j = 0; j < headers.length; j++) {
                String value = row[j] != null ? escapeXml(row[j]) : "";
                xml.append(String.format("    <%s>%s</%s>\n", headers[j], value, headers[j]));
            }
            xml.append("  </row>\n");
        }

        xml.append("</data>");
        return xml.toString();
    }

    private String generateSqlExport() {
        if (data.isEmpty()) return "No data to export";

        StringBuilder sql = new StringBuilder();
        sql.append("-- CREATE TABLE\n");
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
        for (int i = 0; i < headers.length; i++) {
            sql.append("  ").append(headers[i]).append(" VARCHAR(255)");
            if (i < headers.length - 1) sql.append(",");
            sql.append("\n");
        }
        sql.append(");\n\n");

        sql.append("-- INSERT statements\n");
        for (String[] row : data) {
            sql.append("INSERT INTO ").append(tableName).append(" (");
            sql.append(String.join(", ", headers)).append(") VALUES (");

            for (int j = 0; j < headers.length; j++) {
                String value = row[j] != null ? escapeSql(row[j]) : "";
                sql.append("'").append(value).append("'");
                if (j < headers.length - 1) sql.append(", ");
            }

            sql.append(");\n");
        }

        return sql.toString();
    }

    private void exportToJson() {
        if (data.isEmpty()) {
            showError("No data to export");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            @Override public String getDescription() {
                return "JSON Files (*.json)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = ensureFileExtension(fileChooser.getSelectedFile(), "json");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(generateJson());
                updateStatus("Data exported to JSON: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting JSON: " + ex.getMessage());
            }
        }
    }

    private void exportToXml() {
        if (data.isEmpty()) {
            showError("No data to export");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }
            @Override public String getDescription() {
                return "XML Files (*.xml)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = ensureFileExtension(fileChooser.getSelectedFile(), "xml");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(generateXml());
                updateStatus("Data exported to XML: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting XML: " + ex.getMessage());
            }
        }
    }

    private void exportToSql() {
        if (data.isEmpty()) {
            showError("No data to export");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".sql");
            }
            @Override public String getDescription() {
                return "SQL Files (*.sql)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = ensureFileExtension(fileChooser.getSelectedFile(), "sql");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(generateSqlExport());
                updateStatus("Data exported to SQL: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting SQL: " + ex.getMessage());
            }
        }
    }

    private void exportToExcel() {
        if (data.isEmpty()) {
            showError("No data to export");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xlsx");
            }
            @Override public String getDescription() {
                return "Excel Files (*.xlsx)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = ensureFileExtension(fileChooser.getSelectedFile(), "xlsx");

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Data");

                // Create header style
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = (Font) workbook.createFont();
                headerFont.isBold();
                headerStyle.setFont((org.apache.poi.ss.usermodel.Font) headerFont);

                // Write headers
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Write data
                for (int i = 0; i < data.size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < headers.length; j++) {
                        row.createCell(j).setCellValue(data.get(i)[j]);
                    }
                }

                // Auto-size columns
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                // Save file
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                }

                updateStatus("Data exported to Excel: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting Excel: " + ex.getMessage());
            }
        }
    }

    private File ensureFileExtension(File file, String extension) {
        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            return new File(file.getAbsolutePath() + "." + extension);
        }
        return file;
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeXml(String str) {
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String escapeSql(String str) {
        return str.replace("'", "''");
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

            // Read headers
            String headerLine = br.readLine();
            if (headerLine == null) {
                showError("Empty file");
                return;
            }

            headers = headerLine.split(delimiter);
            tableModel.setColumnIdentifiers(headers);

            // Read data
            data.clear();
            String line;
            while ((line = br.readLine()) != null) {
                String[] row = line.split(delimiter, -1); // -1 to keep empty fields
                data.add(row);
                tableModel.addRow(row);
            }

            updateStatus("Loaded " + data.size() + " rows from " + currentFile.getName());

        } catch (Exception ex) {
            showError("Error reading file: " + ex.getMessage());
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

            // Write headers
            bw.write(String.join(delimiter, headers));
            bw.newLine();

            // Write data
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String[] row = new String[tableModel.getColumnCount()];
                for (int j = 0; j < row.length; j++) {
                    Object value = tableModel.getValueAt(i, j);
                    row[j] = (value != null) ? value.toString() : "";
                }
                bw.write(String.join(delimiter, row));
                bw.newLine();
            }

            updateStatus("Data saved to " + file.getName());

        } catch (Exception ex) {
            showError("Error saving: " + ex.getMessage());
        }
    }

    private void showAddRowDialog() {
        if (headers == null) {
            showError("No data loaded");
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
                "Add New Row", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String[] newRow = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                newRow[i] = fields[i].getText();
            }
            data.add(newRow);
            tableModel.addRow(newRow);
            updateStatus("Row added");
        }
    }

    private void deleteRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showError("Select a row to delete");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        tableModel.removeRow(modelRow);
        data.remove(modelRow);
        updateStatus("Row deleted");
    }

    private void generateSqlQuery() {
        if (headers == null || headers.length == 0) {
            sqlQueryArea.setText("-- No data loaded");
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
                        generateUpdate(modelRow) : "-- Select a row for UPDATE");
                break;
            case "DELETE":
                sqlQueryArea.setText(modelRow >= 0 ?
                        generateDelete(modelRow) : "-- Select a row for DELETE");
                break;
        }
    }

    private String generateSelect() {
        return String.format(
                "SELECT %s\nFROM %s;\n\n-- Example with WHERE:\nSELECT *\nFROM %s\nWHERE %s = 'value';",
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
            sb.append("-- Example with values:\nINSERT INTO ").append(tableName)
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

        sb.append("-- Complete example:\nUPDATE ").append(tableName).append("\nSET ");

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
                "DELETE FROM %s\nWHERE %s = '%s';\n\n-- Safer version:\nDELETE FROM %s\nWHERE %s = '%s'\nAND %s = '%s';",
                tableName, headers[0], row[0],
                tableName, headers[0], row[0],
                headers[1], row.length > 1 ? row[1] : "");
    }

    private void copyToClipboard() {
        try {
            String textToCopy = sqlQueryArea.getText();
            if (textToCopy == null || textToCopy.trim().isEmpty()) {
                updateStatus("No content to copy");
                return;
            }

            // Create StringSelection with the text
            StringSelection stringSelection = new StringSelection(textToCopy);

            // Get system clipboard
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            // Check if clipboard is available
            if (clipboard == null) {
                updateStatus("Error: Cannot access system clipboard");
                return;
            }

            // Set clipboard contents
            clipboard.setContents(stringSelection, null);

            // Additional check for Linux systems
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                // Try using xclip if available
                try {
                    String[] cmd = {"bash", "-c", "echo -n \"" + textToCopy.replace("\"", "\\\"") + "\" | xclip -selection clipboard"};
                    Runtime.getRuntime().exec(cmd);
                } catch (IOException e) {
                    // Fallback to normal clipboard
                }
            }

            updateStatus("SQL query copied to clipboard");

            // Show visual confirmation
            JOptionPane.showMessageDialog(this,
                    "Text has been copied to clipboard",
                    "Copy successful",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            updateStatus("Error copying to clipboard: " + e.getMessage());

            // Show detailed error message
            JOptionPane.showMessageDialog(this,
                    "Could not copy to clipboard:\n" + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupStatusBar() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + message);
    }
}
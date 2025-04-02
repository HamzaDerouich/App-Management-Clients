package org.example.App;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class UniversalCsvEditor extends JFrame {
    private JTable table;
    private DefaultTableModel tableModel;
    private List<String[]> data = new ArrayList<>();
    private String[] headers;
    private File currentFile;
    private JLabel statusLabel;
    private JComboBox<String> delimiterCombo;

    public UniversalCsvEditor() {
        initializeUI();
        setupToolbar();
        setupTable();
        setupStatusBar();
    }

    private void initializeUI() {
        setTitle("Universal CSV Editor");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void setupToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        // File operations
        toolBar.add(createToolButton("Open", "Open CSV file", e -> openFile()));
        toolBar.add(createToolButton("Save", "Save CSV file", e -> saveFile()));
        toolBar.add(createToolButton("Save As", "Save as new CSV file", e -> saveAsFile()));
        toolBar.addSeparator();

        // Delimiter selection
        toolBar.add(new JLabel("Delimiter:"));
        delimiterCombo = new JComboBox<>(new String[]{",", ";", "|", "\t", "Custom"});
        delimiterCombo.addActionListener(e -> updateDelimiter());
        toolBar.add(delimiterCombo);
        toolBar.addSeparator();

        // Data operations
        toolBar.add(createToolButton("Add Row", "Add new row", e -> addRow()));
        toolBar.add(createToolButton("Delete Row", "Delete selected row", e -> deleteRow()));
        toolBar.addSeparator();

        // Tools
        toolBar.add(createToolButton("Filter", "Filter data", e -> filterData()));
        toolBar.add(createToolButton("Sort", "Sort by column", e -> sortData()));
        toolBar.addSeparator();

        add(toolBar, BorderLayout.NORTH);
    }

    private void updateDelimiter() {
        if (delimiterCombo.getSelectedItem().equals("Custom")) {
            String customDelim = JOptionPane.showInputDialog(this, "Enter custom delimiter:");
            if (customDelim != null && !customDelim.isEmpty()) {
                delimiterCombo.addItem(customDelim);
                delimiterCombo.setSelectedItem(customDelim);
            }
        }
    }

    private void setupTable() {
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true; // Allow editing all cells
            }
        };

        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("CSV Data"));
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupStatusBar() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JButton createToolButton(String text, String tooltip, ActionListener listener) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.addActionListener(listener);
        return button;
    }

    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open CSV File");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            loadCSV();
        }
    }

    private void loadCSV() {
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
                String[] row = parseCsvLine(line, delimiter);
                data.add(row);
                tableModel.addRow(row);
            }

            updateStatus("Loaded " + data.size() + " rows from " + currentFile.getName());

        } catch (IOException ex) {
            showError("Error reading file: " + ex.getMessage());
        }
    }

    private String[] parseCsvLine(String line, String delimiter) {
        // Simple CSV parsing - can be enhanced for quoted values
        return line.split(delimiter, -1); // Keep trailing empty fields
    }

    private void saveFile() {
        if (currentFile == null) {
            saveAsFile();
            return;
        }

        saveToFile(currentFile);
    }

    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save CSV File");

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            currentFile = file;
            saveToFile(file);
        }
    }

    private void saveToFile(File file) {
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

            updateStatus("Saved " + tableModel.getRowCount() + " rows to " + file.getName());

        } catch (IOException ex) {
            showError("Error saving file: " + ex.getMessage());
        }
    }

    private void addRow() {
        String[] newRow = new String[tableModel.getColumnCount()];
        Arrays.fill(newRow, "");
        tableModel.addRow(newRow);
        data.add(newRow);
        updateStatus("Added new row");
    }

    private void deleteRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Please select a row to delete");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        tableModel.removeRow(modelRow);
        data.remove(modelRow);
        updateStatus("Deleted row " + (selectedRow + 1));
    }

    private void filterData() {
        if (headers == null || headers.length == 0) {
            showWarning("No data loaded");
            return;
        }

        JPanel filterPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        JComboBox<String> columnCombo = new JComboBox<>(headers);
        JComboBox<String> operatorCombo = new JComboBox<>(new String[]{
                "contains", "equals", "starts with", "ends with", "empty", "not empty"
        });
        JTextField valueField = new JTextField();

        filterPanel.add(new JLabel("Column:"));
        filterPanel.add(columnCombo);
        filterPanel.add(new JLabel("Operator:"));
        filterPanel.add(operatorCombo);
        filterPanel.add(new JLabel("Value:"));
        filterPanel.add(valueField);

        int result = JOptionPane.showConfirmDialog(
                this, filterPanel, "Filter Data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String column = (String) columnCombo.getSelectedItem();
            String operator = (String) operatorCombo.getSelectedItem();
            String value = valueField.getText().toLowerCase();

            int columnIndex = Arrays.asList(headers).indexOf(column);

            tableModel.setRowCount(0); // Clear table
            for (String[] row : data) {
                if (matchesFilter(row, columnIndex, operator, value)) {
                    tableModel.addRow(row);
                }
            }

            updateStatus("Filter applied. Showing " + tableModel.getRowCount() + " rows");
        }
    }

    private boolean matchesFilter(String[] row, int columnIndex, String operator, String value) {
        if (columnIndex < 0 || columnIndex >= row.length) return false;

        String cellValue = (row[columnIndex] != null) ? row[columnIndex].toLowerCase() : "";

        switch (operator) {
            case "contains": return cellValue.contains(value);
            case "equals": return cellValue.equals(value);
            case "starts with": return cellValue.startsWith(value);
            case "ends with": return cellValue.endsWith(value);
            case "empty": return cellValue.isEmpty();
            case "not empty": return !cellValue.isEmpty();
            default: return true;
        }
    }

    private void sortData() {
        if (headers == null || headers.length == 0) {
            showWarning("No data loaded");
            return;
        }

        String column = (String) JOptionPane.showInputDialog(
                this, "Select column to sort by:", "Sort Data",
                JOptionPane.QUESTION_MESSAGE, null, headers, headers[0]);

        if (column != null) {
            int columnIndex = Arrays.asList(headers).indexOf(column);
            table.getRowSorter().toggleSortOrder(columnIndex);
            updateStatus("Sorted by " + column);
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + message);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
        updateStatus("Warning: " + message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new UniversalCsvEditor().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
package org.example.App;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


import javafx.scene.text.Font;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.google.gson.*;

import static javafx.scene.text.Font.*;

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

    // JSON Editor Components
    private JTree jsonTree;
    private DefaultTreeModel jsonTreeModel;
    private JPanel jsonEditorPanel;
    private JTabbedPane dataViewTabs;
    private JPopupMenu jsonPopupMenu;
    private JsonElement currentJsonRoot;

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
        setTitle("Complete CSV/JSON Editor");
        setSize(1500, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Create tabs for data views
        dataViewTabs = new JTabbedPane();
        add(dataViewTabs, BorderLayout.CENTER);
    }

    private void setupToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(createButton("Open CSV", "Open CSV file", e -> openFile()));
        toolBar.add(createButton("Open JSON", "Open JSON file", e -> openJsonFile()));
        toolBar.add(createButton("Editor XML", "Open Editor XML file", e -> openXMLFile()));
        toolBar.add(createButton("Save", "Save file", e -> saveFile()));
        toolBar.addSeparator();

        toolBar.add(new JLabel("Delimiter:"));
        delimiterCombo = new JComboBox<>(new String[]{",", ";", "|", "\t"});
        toolBar.add(delimiterCombo);
        toolBar.addSeparator();

        toolBar.add(createButton("Filter", "Apply filters", e -> showFilterDialog()));
        toolBar.add(createButton("Sort", "Sort by column", e -> showSortDialog()));
        toolBar.add(createButton("Batch Update", "Batch update values", e -> showBatchUpdateDialog()));
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

    private void openXMLFile()
    {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new CorrectorXML().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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

        JScrollPane tableScrollPane = new JScrollPane(table);
        dataViewTabs.addTab("Table View", tableScrollPane);

        // Setup JSON editor
        setupJsonEditor();
    }

    private void setupJsonEditor() {
        jsonEditorPanel = new JPanel(new BorderLayout());

        // JSON Tree
        jsonTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("JSON Data"));
        jsonTree = new JTree(jsonTreeModel);
        jsonTree.setEditable(true);
        jsonTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        jsonTree.setCellRenderer(new JsonTreeCellRenderer());

        // Make JSON tree editable with proper value handling
        jsonTree.setCellEditor(new DefaultTreeCellEditor(jsonTree, new DefaultTreeCellRenderer()) {
            @Override
            public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected,
                                                        boolean expanded, boolean leaf, int row) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.getUserObject() instanceof JsonElementWrapper) {
                    JsonElementWrapper wrapper = (JsonElementWrapper) node.getUserObject();
                    if (wrapper.isPrimitive()) {
                        return super.getTreeCellEditorComponent(tree, wrapper.getValueString(), isSelected,
                                expanded, leaf, row);
                    }
                }
                return super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
            }
        });

        jsonTree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = jsonTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        jsonTree.setSelectionPath(path);
                        showJsonPopupMenu(e.getX(), e.getY());
                    }
                }
            }
        });

        // JSON Toolbar
        JToolBar jsonToolBar = new JToolBar();
        JButton expandAllButton = new JButton("Expand All");
        expandAllButton.addActionListener(e -> expandAllNodes(jsonTree, 0, jsonTree.getRowCount()));
        JButton collapseAllButton = new JButton("Collapse All");
        collapseAllButton.addActionListener(e -> {
            for (int i = 0; i < jsonTree.getRowCount(); i++) {
                jsonTree.collapseRow(i);
            }
        });

        jsonToolBar.add(expandAllButton);
        jsonToolBar.add(collapseAllButton);

        jsonEditorPanel.add(jsonToolBar, BorderLayout.NORTH);
        jsonEditorPanel.add(new JScrollPane(jsonTree), BorderLayout.CENTER);

        // Setup JSON popup menu
        setupJsonPopupMenu();

        dataViewTabs.addTab("JSON View", jsonEditorPanel);
    }

    private void setupJsonPopupMenu() {
        jsonPopupMenu = new JPopupMenu();

        JMenuItem addKeyItem = new JMenuItem("Add Key/Value");
        addKeyItem.addActionListener(e -> addJsonKeyValue());
        jsonPopupMenu.add(addKeyItem);

        JMenuItem addObjectItem = new JMenuItem("Add Object");
        addObjectItem.addActionListener(e -> addJsonObject());
        jsonPopupMenu.add(addObjectItem);

        JMenuItem addArrayItem = new JMenuItem("Add Array");
        addArrayItem.addActionListener(e -> addJsonArray());
        jsonPopupMenu.add(addArrayItem);

        jsonPopupMenu.addSeparator();

        JMenuItem editItem = new JMenuItem("Edit Value");
        editItem.addActionListener(e -> editJsonValue());
        jsonPopupMenu.add(editItem);

        jsonPopupMenu.addSeparator();

        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> removeJsonNode());
        jsonPopupMenu.add(removeItem);
    }

    private void showJsonPopupMenu(int x, int y) {
        TreePath path = jsonTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        JsonElementWrapper wrapper = (JsonElementWrapper) node.getUserObject();

        // Enable/disable menu items based on selection
        for (Component comp : jsonPopupMenu.getComponents()) {
            if (comp instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) comp;
                String text = item.getText();

                if (text.equals("Edit Value")) {
                    item.setEnabled(wrapper.isPrimitive());
                } else if (text.equals("Remove")) {
                    item.setEnabled(node.getParent() != null); // Can't remove root
                }
            }
        }

        jsonPopupMenu.show(jsonTree, x, y);
    }

    private static class JsonTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof JsonElementWrapper) {
                JsonElementWrapper wrapper = (JsonElementWrapper) node.getUserObject();
                setText(wrapper.getDisplayText());
                setIcon(wrapper.getIcon());
            }

            return this;
        }
    }

    private static class JsonElementWrapper {
        private String key;
        private JsonElement element;

        public JsonElementWrapper(String key, JsonElement element) {
            this.key = key;
            this.element = element;
        }

        public String getKey() {
            return key;
        }

        public JsonElement getElement() {
            return element;
        }

        public String getValueString() {
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            return getType();
        }

        public String getDisplayText() {
            if (element.isJsonPrimitive()) {
                return key + ": " + element.getAsString();
            }
            return key + " (" + getType() + ")";
        }

        public String getType() {
            if (element.isJsonObject()) return "Object";
            if (element.isJsonArray()) return "Array[" + element.getAsJsonArray().size() + "]";
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) return "Boolean";
                if (primitive.isNumber()) return "Number";
                return "String";
            }
            return "Null";
        }

        public boolean isPrimitive() {
            return element.isJsonPrimitive();
        }

        public Icon getIcon() {
            String type = getType();
            switch (type) {
                case "Object": return UIManager.getIcon("FileView.directoryIcon");
                case "Array": return UIManager.getIcon("FileView.hardDriveIcon");
                default: return UIManager.getIcon("FileView.fileIcon");
            }
        }
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }

        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    private void addJsonKeyValue() {
        TreePath selectedPath = jsonTree.getSelectionPath();
        if (selectedPath == null) {
            showError("Select a node to add key/value");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        JsonElementWrapper selectedWrapper = (JsonElementWrapper) selectedNode.getUserObject();

        if (!selectedWrapper.getElement().isJsonObject() && !selectedWrapper.getElement().isJsonArray()) {
            showError("Can only add to Objects or Arrays");
            return;
        }

        String key = JOptionPane.showInputDialog(this, "Enter key name (leave empty for array item):");
        if (key == null) return; // cancelled

        String value = JOptionPane.showInputDialog(this, "Enter value:");
        if (value == null) return; // cancelled

        JsonElement newElement = new JsonPrimitive(value);

        if (selectedWrapper.getElement().isJsonObject()) {
            if (key.trim().isEmpty()) {
                showError("Key cannot be empty for Object");
                return;
            }
            selectedWrapper.getElement().getAsJsonObject().add(key, newElement);

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(key, newElement));
            selectedNode.add(newNode);
        }
        else if (selectedWrapper.getElement().isJsonArray()) {
            selectedWrapper.getElement().getAsJsonArray().add(newElement);
            String arrayKey = "[" + (selectedWrapper.getElement().getAsJsonArray().size() - 1) + "]";

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(arrayKey, newElement));
            selectedNode.add(newNode);
        }

        jsonTreeModel.reload(selectedNode);
        jsonTree.expandPath(selectedPath);
        updateJsonDataFromTree();
        refreshDataFromJson();
    }

    private void addJsonObject() {
        TreePath selectedPath = jsonTree.getSelectionPath();
        if (selectedPath == null) {
            showError("Select a node to add object");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        JsonElementWrapper selectedWrapper = (JsonElementWrapper) selectedNode.getUserObject();

        if (!selectedWrapper.getElement().isJsonObject() && !selectedWrapper.getElement().isJsonArray()) {
            showError("Can only add to Objects or Arrays");
            return;
        }

        String key = JOptionPane.showInputDialog(this, "Enter key name (leave empty for array item):");
        if (key == null) return; // cancelled

        JsonObject newObject = new JsonObject();

        if (selectedWrapper.getElement().isJsonObject()) {
            if (key.trim().isEmpty()) {
                showError("Key cannot be empty for Object");
                return;
            }
            selectedWrapper.getElement().getAsJsonObject().add(key, newObject);

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(key, newObject));
            selectedNode.add(newNode);
        }
        else if (selectedWrapper.getElement().isJsonArray()) {
            selectedWrapper.getElement().getAsJsonArray().add(newObject);
            String arrayKey = "[" + (selectedWrapper.getElement().getAsJsonArray().size() - 1) + "]";

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(arrayKey, newObject));
            selectedNode.add(newNode);
        }

        jsonTreeModel.reload(selectedNode);
        jsonTree.expandPath(selectedPath);
        updateJsonDataFromTree();
        refreshDataFromJson();
    }

    private void addJsonArray() {
        TreePath selectedPath = jsonTree.getSelectionPath();
        if (selectedPath == null) {
            showError("Select a node to add array");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        JsonElementWrapper selectedWrapper = (JsonElementWrapper) selectedNode.getUserObject();

        if (!selectedWrapper.getElement().isJsonObject() && !selectedWrapper.getElement().isJsonArray()) {
            showError("Can only add to Objects or Arrays");
            return;
        }

        String key = JOptionPane.showInputDialog(this, "Enter key name (leave empty for array item):");
        if (key == null) return; // cancelled

        JsonArray newArray = new JsonArray();

        if (selectedWrapper.getElement().isJsonObject()) {
            if (key.trim().isEmpty()) {
                showError("Key cannot be empty for Object");
                return;
            }
            selectedWrapper.getElement().getAsJsonObject().add(key, newArray);

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(key, newArray));
            selectedNode.add(newNode);
        }
        else if (selectedWrapper.getElement().isJsonArray()) {
            selectedWrapper.getElement().getAsJsonArray().add(newArray);
            String arrayKey = "[" + (selectedWrapper.getElement().getAsJsonArray().size() - 1) + "]";

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new JsonElementWrapper(arrayKey, newArray));
            selectedNode.add(newNode);
        }

        jsonTreeModel.reload(selectedNode);
        jsonTree.expandPath(selectedPath);
        updateJsonDataFromTree();
        refreshDataFromJson();
    }

    private void editJsonValue() {
        TreePath selectedPath = jsonTree.getSelectionPath();
        if (selectedPath == null) {
            showError("Select a value to edit");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        JsonElementWrapper selectedWrapper = (JsonElementWrapper) selectedNode.getUserObject();

        if (!selectedWrapper.isPrimitive()) {
            showError("Can only edit primitive values");
            return;
        }

        String currentValue = selectedWrapper.getValueString();
        String newValue = JOptionPane.showInputDialog(this, "Edit value:", currentValue);
        if (newValue == null || newValue.equals(currentValue)) return;

        // Find the parent and update the actual JSON element
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode == null) return;

        JsonElementWrapper parentWrapper = (JsonElementWrapper) parentNode.getUserObject();

        if (parentWrapper.getElement().isJsonObject()) {
            parentWrapper.getElement().getAsJsonObject().addProperty(selectedWrapper.getKey(), newValue);
        }
        else if (parentWrapper.getElement().isJsonArray()) {
            int index = Integer.parseInt(selectedWrapper.getKey().replaceAll("[\\[\\]]", ""));
            JsonPrimitive primitive = selectedWrapper.getElement().getAsJsonPrimitive();

            if (primitive.isBoolean()) {
                parentWrapper.getElement().getAsJsonArray().set(index, new JsonPrimitive(Boolean.parseBoolean(newValue)));
            }
            else if (primitive.isNumber()) {
                try {
                    parentWrapper.getElement().getAsJsonArray().set(index, new JsonPrimitive(Double.parseDouble(newValue)));
                } catch (NumberFormatException e) {
                    showError("Invalid number format");
                    return;
                }
            }
            else {
                parentWrapper.getElement().getAsJsonArray().set(index, new JsonPrimitive(newValue));
            }
        }

        // Update the tree node
        selectedNode.setUserObject(new JsonElementWrapper(selectedWrapper.getKey(),
                parentWrapper.getElement().isJsonObject() ?
                        parentWrapper.getElement().getAsJsonObject().get(selectedWrapper.getKey()) :
                        parentWrapper.getElement().getAsJsonArray().get(
                                Integer.parseInt(selectedWrapper.getKey().replaceAll("[\\[\\]]", "")))));

        jsonTreeModel.reload(selectedNode);
        updateJsonDataFromTree();
        refreshDataFromJson();
    }

    private void removeJsonNode() {
        TreePath selectedPath = jsonTree.getSelectionPath();
        if (selectedPath == null) {
            showError("Select a node to remove");
            return;
        }

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        JsonElementWrapper selectedWrapper = (JsonElementWrapper) selectedNode.getUserObject();

        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        if (parentNode == null) {
            showError("Cannot remove root node");
            return;
        }

        JsonElementWrapper parentWrapper = (JsonElementWrapper) parentNode.getUserObject();

        if (parentWrapper.getElement().isJsonObject()) {
            parentWrapper.getElement().getAsJsonObject().remove(selectedWrapper.getKey());
        }
        else if (parentWrapper.getElement().isJsonArray()) {
            int index = Integer.parseInt(selectedWrapper.getKey().replaceAll("[\\[\\]]", ""));
            parentWrapper.getElement().getAsJsonArray().remove(index);

            // Update keys for remaining array items
            for (int i = 0; i < parentNode.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parentNode.getChildAt(i);
                JsonElementWrapper childWrapper = (JsonElementWrapper) child.getUserObject();
                if (childWrapper.getKey().startsWith("[")) {
                    child.setUserObject(new JsonElementWrapper("[" + i + "]", childWrapper.getElement()));
                }
            }
        }

        jsonTreeModel.removeNodeFromParent(selectedNode);
        updateJsonDataFromTree();
        refreshDataFromJson();
    }

    private void refreshDataFromJson() {
        if (currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")) {
            // Clear current data
            data.clear();
            tableModel.setRowCount(0);

            // Reload from JSON
            try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
                JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);

                if (jsonElement.isJsonArray()) {
                    JsonArray jsonArray = jsonElement.getAsJsonArray();
                    if (jsonArray.size() > 0 && jsonArray.get(0).isJsonObject()) {
                        JsonObject firstObj = jsonArray.get(0).getAsJsonObject();
                        headers = firstObj.keySet().toArray(new String[0]);
                        tableModel.setColumnIdentifiers(headers);

                        for (JsonElement element : jsonArray) {
                            if (element.isJsonObject()) {
                                JsonObject obj = element.getAsJsonObject();
                                String[] row = new String[headers.length];
                                for (int i = 0; i < headers.length; i++) {
                                    JsonElement value = obj.get(headers[i]);
                                    row[i] = (value != null && !value.isJsonNull()) ? value.getAsString() : "";
                                }
                                data.add(row);
                                tableModel.addRow(row);
                            }
                        }
                    }
                }
                updateJsonTree();
                generateSqlQuery(); // Refresh SQL preview
            } catch (Exception ex) {
                showError("Error refreshing JSON data: " + ex.getMessage());
            }
        }
    }

    private void updateJsonTree() {
        if (currentFile == null || !currentFile.getName().toLowerCase().endsWith(".json")) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
            JsonElement jsonElement = new Gson().fromJson(reader, JsonElement.class);
            currentJsonRoot = jsonElement;
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new JsonElementWrapper("root", jsonElement));
            buildTree(jsonElement, rootNode);
            jsonTreeModel.setRoot(rootNode);
            jsonTree.setModel(jsonTreeModel); // Explicitly set model to ensure refresh
            expandAllNodes(jsonTree, 0, jsonTree.getRowCount());
        } catch (Exception ex) {
            showError("Error updating JSON tree: " + ex.getMessage());
        }
    }

    private void buildTree(JsonElement element, DefaultMutableTreeNode parentNode) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(
                        new JsonElementWrapper(entry.getKey(), entry.getValue()));
                parentNode.add(childNode);
                buildTree(entry.getValue(), childNode);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(
                        new JsonElementWrapper("[" + i + "]", array.get(i)));
                parentNode.add(childNode);
                buildTree(array.get(i), childNode);
            }
        }
    }

    private void updateJsonDataFromTree() {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) jsonTreeModel.getRoot();
        if (rootNode == null) return;

        JsonElementWrapper rootWrapper = (JsonElementWrapper) rootNode.getUserObject();
        JsonElement rootElement = rootWrapper.getElement();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile))) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(rootElement));
            updateStatus("JSON data updated");
        } catch (Exception ex) {
            showError("Error saving JSON data: " + ex.getMessage());
        }
    }

    private void showBatchUpdateDialog() {
        if (headers == null || headers.length == 0) {
            showError("No data loaded");
            return;
        }

        JPanel panel = new JPanel(new BorderLayout());

        // Input panel
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JComboBox<String> columnCombo = new JComboBox<>(headers);
        JTextField searchValueField = new JTextField();
        JTextField replaceValueField = new JTextField();

        inputPanel.add(new JLabel("Column:"));
        inputPanel.add(columnCombo);
        inputPanel.add(new JLabel("Search for:"));
        inputPanel.add(searchValueField);
        inputPanel.add(new JLabel("Replace with:"));
        inputPanel.add(replaceValueField);

        panel.add(inputPanel, BorderLayout.NORTH);

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox caseSensitiveCheck = new JCheckBox("Case sensitive");
        JCheckBox regexCheck = new JCheckBox("Use regular expressions");
        optionsPanel.add(caseSensitiveCheck);
        optionsPanel.add(regexCheck);

        panel.add(optionsPanel, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Batch Update", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String column = (String) columnCombo.getSelectedItem();
            String searchValue = searchValueField.getText();
            String replaceValue = replaceValueField.getText();
            boolean caseSensitive = caseSensitiveCheck.isSelected();
            boolean useRegex = regexCheck.isSelected();

            performBatchUpdate(column, searchValue, replaceValue, caseSensitive, useRegex);
        }
    }

    private void performBatchUpdate(String column, String searchValue, String replaceValue,
                                    boolean caseSensitive, boolean useRegex) {
        int columnIndex = Arrays.asList(headers).indexOf(column);
        if (columnIndex == -1) return;

        int updatedCount = 0;

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String currentValue = (String) tableModel.getValueAt(i, columnIndex);
            if (currentValue == null) continue;

            String newValue;
            if (useRegex) {
                try {
                    String pattern = caseSensitive ? searchValue : "(?i)" + searchValue;
                    newValue = currentValue.replaceAll(pattern, replaceValue);
                } catch (Exception e) {
                    showError("Invalid regular expression: " + e.getMessage());
                    return;
                }
            } else {
                if (caseSensitive) {
                    newValue = currentValue.replace(searchValue, replaceValue);
                } else {
                    newValue = currentValue.replaceAll("(?i)" + Pattern.quote(searchValue), replaceValue);
                }
            }

            if (!newValue.equals(currentValue)) {
                tableModel.setValueAt(newValue, i, columnIndex);
                data.get(i)[columnIndex] = newValue;
                updatedCount++;
            }
        }

        updateStatus("Batch update completed. " + updatedCount + " rows updated.");
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
        previewArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
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
        sqlQueryArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
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
                previewArea.setText(currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")
                        ? generateJsonFromTree()
                        : generateJson());
                break;
            case "XML":
                previewArea.setText(currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")
                        ? generateXmlFromJson()
                        : generateXml());
                break;
            case "SQL":
                previewArea.setText(currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")
                        ? generateSqlFromJson()
                        : generateSqlExport());
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
                if (currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")) {
                    exportJsonFromTree();
                } else {
                    exportToJson();
                }
                break;
            case "XML":
                if (currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")) {
                    exportXmlFromJson();
                } else {
                    exportToXml();
                }
                break;
            case "SQL":
                exportToSql(); // Simplified - let exportToSql() handle the logic
                break;
            case "Excel":
                if (currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")) {
                    exportExcelFromJson();
                } else {
                    exportToExcel();
                }
                break;
        }
    }

    private String generateXmlFromJson() {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) jsonTreeModel.getRoot();
        if (rootNode == null) return "No JSON data to convert to XML";

        JsonElementWrapper rootWrapper = (JsonElementWrapper) rootNode.getUserObject();
        return convertJsonToXml(rootWrapper.getElement(), "root");
    }

    private String convertJsonToXml(JsonElement element, String name) {
        StringBuilder xml = new StringBuilder();

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            xml.append("<").append(name).append(">\n");
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                xml.append(convertJsonToXml(entry.getValue(), entry.getKey()));
            }
            xml.append("</").append(name).append(">\n");
        }
        else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            xml.append("<").append(name).append(">\n");
            for (int i = 0; i < array.size(); i++) {
                xml.append(convertJsonToXml(array.get(i), "item"));
            }
            xml.append("</").append(name).append(">\n");
        }
        else if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            xml.append("<").append(name).append(">")
                    .append(escapeXml(value))
                    .append("</").append(name).append(">\n");
        }
        else { // JsonNull
            xml.append("<").append(name).append("/>\n");
        }

        return xml.toString();
    }

    private String generateSqlFromJson() {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) jsonTreeModel.getRoot();
        if (rootNode == null) {
            return "-- No JSON data available for SQL export";
        }

        JsonElementWrapper rootWrapper = (JsonElementWrapper) rootNode.getUserObject();
        JsonElement rootElement = rootWrapper.getElement();

        StringBuilder sql = new StringBuilder();
        sql.append("-- SQL Export from JSON - Generated on ").append(new Date()).append("\n\n");

        if (rootElement.isJsonArray()) {
            JsonArray array = rootElement.getAsJsonArray();
            if (array.size() > 0 && array.get(0).isJsonObject()) {
                JsonObject firstObj = array.get(0).getAsJsonObject();

                // CREATE TABLE statement
                sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
                sql.append("  id INT AUTO_INCREMENT PRIMARY KEY,\n");

                for (Map.Entry<String, JsonElement> entry : firstObj.entrySet()) {
                    sql.append("  ").append(entry.getKey()).append(" VARCHAR(255),\n");
                }
                sql.append(");\n\n");

                // INSERT statements
                sql.append("-- INSERT statements\n");
                for (JsonElement element : array) {
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        sql.append("INSERT INTO ").append(tableName).append(" (")
                                .append(String.join(", ", obj.keySet())).append(")\nVALUES (");

                        boolean first = true;
                        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                            if (!first) sql.append(", ");
                            String value = entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString();
                            sql.append("'").append(escapeSql(value)).append("'");
                            first = false;
                        }
                        sql.append(");\n");
                    }
                }
            }
        } else if (rootElement.isJsonObject()) {
            JsonObject obj = rootElement.getAsJsonObject();

            // CREATE TABLE statement
            sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
            sql.append("  id INT AUTO_INCREMENT PRIMARY KEY,\n");

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                sql.append("  ").append(entry.getKey()).append(" VARCHAR(255),\n");
            }
            sql.append(");\n\n");

            // INSERT statement
            sql.append("INSERT INTO ").append(tableName).append(" (")
                    .append(String.join(", ", obj.keySet())).append(")\nVALUES (");

            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!first) sql.append(", ");
                String value = entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString();
                sql.append("'").append(escapeSql(value)).append("'");
                first = false;
            }
            sql.append(");\n");
        }

        return sql.toString();
    }

    private void exportJsonFromTree() {
        if (jsonTreeModel.getRoot() == null) {
            showError("No JSON data to export");
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
                writer.write(generateJsonFromTree());
                updateStatus("JSON data exported to: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting JSON: " + ex.getMessage());
            }
        }
    }

    private void exportXmlFromJson() {
        if (jsonTreeModel.getRoot() == null) {
            showError("No JSON data to export");
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
                writer.write(generateXmlFromJson());
                updateStatus("XML data exported to: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting XML: " + ex.getMessage());
            }
        }
    }

    private void exportSqlFromJson() {
        if (jsonTreeModel.getRoot() == null) {
            showError("No JSON data to export");
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
                writer.write(generateSqlFromJson());
                updateStatus("SQL data exported to: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting SQL: " + ex.getMessage());
            }
        }
    }

    private void exportExcelFromJson() {
        if (jsonTreeModel.getRoot() == null) {
            showError("No JSON data to export");
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
                Sheet sheet = workbook.createSheet("JSON Data");

                // Get JSON data
                DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) jsonTreeModel.getRoot();
                JsonElementWrapper rootWrapper = (JsonElementWrapper) rootNode.getUserObject();
                JsonElement rootElement = rootWrapper.getElement();

                // Create header style
                CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                if (rootElement.isJsonArray()) {
                    JsonArray array = rootElement.getAsJsonArray();
                    if (array.size() > 0 && array.get(0).isJsonObject()) {
                        // Write headers from first object
                        JsonObject firstObj = array.get(0).getAsJsonObject();
                        Row headerRow = sheet.createRow(0);
                        int col = 0;
                        for (String key : firstObj.keySet()) {
                            Cell cell = headerRow.createCell(col++);
                            cell.setCellValue(key);
                            cell.setCellStyle(headerStyle);
                        }

                        // Write data
                        int rowNum = 1;
                        for (JsonElement element : array) {
                            if (element.isJsonObject()) {
                                JsonObject obj = element.getAsJsonObject();
                                Row row = sheet.createRow(rowNum++);
                                int cellNum = 0;
                                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                                    row.createCell(cellNum++).setCellValue(entry.getValue().getAsString());
                                }
                            }
                        }
                    }
                } else if (rootElement.isJsonObject()) {
                    // For single object, create two-column layout (key, value)
                    JsonObject obj = rootElement.getAsJsonObject();

                    // Write headers
                    Row headerRow = sheet.createRow(0);
                    headerRow.createCell(0).setCellValue("Key");
                    headerRow.createCell(1).setCellValue("Value");
                    headerRow.getCell(0).setCellStyle(headerStyle);
                    headerRow.getCell(1).setCellStyle(headerStyle);

                    // Write data
                    int rowNum = 1;
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(entry.getKey());
                        row.createCell(1).setCellValue(entry.getValue().getAsString());
                    }
                }

                // Auto-size columns
                for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
                    sheet.autoSizeColumn(i);
                }

                // Save file
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                }

                updateStatus("JSON data exported to Excel: " + file.getName());
            } catch (Exception ex) {
                showError("Error exporting Excel: " + ex.getMessage());
            }
        }
    }

    private String generateJson() {
        if (data.isEmpty()) return "No data to export";

        JsonArray jsonArray = new JsonArray();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        for (String[] row : data) {
            JsonObject jsonObject = new JsonObject();
            for (int j = 0; j < headers.length; j++) {
                jsonObject.addProperty(headers[j], row[j] != null ? row[j] : "");
            }
            jsonArray.add(jsonObject);
        }

        return gson.toJson(jsonArray);
    }

    private String generateJsonFromTree() {
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) jsonTreeModel.getRoot();
        if (rootNode == null) return "No JSON data";

        JsonElementWrapper rootWrapper = (JsonElementWrapper) rootNode.getUserObject();
        return new GsonBuilder().setPrettyPrinting().create().toJson(rootWrapper.getElement());
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
        if (data.isEmpty() || headers == null || headers.length == 0) {
            return "-- No data available for SQL export";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- SQL Export - Generated on ").append(new Date()).append("\n\n");

        // CREATE TABLE statement
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
        sql.append("  id INT AUTO_INCREMENT PRIMARY KEY,\n");

        for (int i = 0; i < headers.length; i++) {
            sql.append("  ").append(headers[i]).append(" VARCHAR(255)");
            if (i < headers.length - 1) sql.append(",");
            sql.append("\n");
        }
        sql.append(");\n\n");

        // INSERT statements
        sql.append("-- INSERT statements\n");
        for (String[] row : data) {
            sql.append("INSERT INTO ").append(tableName).append(" (")
                    .append(String.join(", ", headers)).append(")\nVALUES (");

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
                // Generate appropriate SQL based on file type
                String sqlContent = currentFile != null && currentFile.getName().toLowerCase().endsWith(".json")
                        ? generateSqlFromJson()
                        : generateSqlExport();

                writer.write(sqlContent);
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
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

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
        if (str == null) return "";
        return str.replace("'", "''")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void openFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }
            @Override public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            loadCsvFile();
        }
    }

    private void openJsonFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            @Override public String getDescription() {
                return "JSON Files (*.json)";
            }
        });

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            loadJsonFile();
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

    private void loadJsonFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
            Gson gson = new Gson();
            JsonElement jsonElement = gson.fromJson(reader, JsonElement.class);

            JsonArray jsonArray = null;

            if (jsonElement.isJsonArray()) {
                jsonArray = jsonElement.getAsJsonArray();
            }
            else if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        jsonArray = entry.getValue().getAsJsonArray();
                        break;
                    }
                }
            }

            if (jsonArray == null) {
                showError("Formato JSON no compatible. Se esperaba un array o un objeto con una propiedad que contenga un array.");
                return;
            }

            if (jsonArray.size() == 0) {
                showError("El array JSON está vacío");
                return;
            }

            if (!jsonArray.get(0).isJsonObject()) {
                showError("El array debe contener objetos JSON");
                return;
            }

            JsonObject firstObj = jsonArray.get(0).getAsJsonObject();
            Set<String> headerSet = firstObj.keySet();
            headers = headerSet.toArray(new String[0]);
            tableModel.setColumnIdentifiers(headers);

            data.clear();
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                String[] row = new String[headers.length];

                for (int i = 0; i < headers.length; i++) {
                    JsonElement value = obj.get(headers[i]);
                    row[i] = (value != null && !value.isJsonNull()) ? value.getAsString() : "";
                }

                data.add(row);
                tableModel.addRow(row);
            }

            updateStatus("Cargados " + data.size() + " registros desde: " + currentFile.getName());
            updateJsonTree();

        } catch (Exception ex) {
            showError("Error al leer JSON: " + ex.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveAsFile();
            return;
        }

        if (currentFile.getName().toLowerCase().endsWith(".json")) {
            saveToJsonFile(currentFile);
        } else {
            saveToCsvFile(currentFile);
        }
    }

    private void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser();

        // Add file filters for both CSV and JSON
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }
            @Override public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });

        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }
            @Override public String getDescription() {
                return "JSON Files (*.json)";
            }
        });

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            javax.swing.filechooser.FileFilter selectedFilter = fileChooser.getFileFilter();
            String extension = selectedFilter.getDescription().contains("JSON") ? "json" : "csv";

            File file = ensureFileExtension(fileChooser.getSelectedFile(), extension);
            currentFile = file;

            if (extension.equals("json")) {
                saveToJsonFile(file);
            } else {
                saveToCsvFile(file);
            }
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

    private void saveToJsonFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(generateJson());
            updateStatus("Data saved to JSON: " + file.getName());
        } catch (Exception ex) {
            showError("Error saving JSON: " + ex.getMessage());
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
            generateSqlQuery();
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

            StringSelection stringSelection = new StringSelection(textToCopy);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

            if (clipboard == null) {
                updateStatus("Error: Cannot access system clipboard");
                return;
            }

            clipboard.setContents(stringSelection, null);

            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                try {
                    String[] cmd = {"bash", "-c", "echo -n \"" + textToCopy.replace("\"", "\\\"") + "\" | xclip -selection clipboard"};
                    Runtime.getRuntime().exec(cmd);
                } catch (IOException e) {
                    // Fallback to normal clipboard
                }
            }

            updateStatus("SQL query copied to clipboard");

            JOptionPane.showMessageDialog(this,
                    "Text has been copied to clipboard",
                    "Copy successful",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            updateStatus("Error copying to clipboard: " + e.getMessage());

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
        String fileType = currentFile != null ?
                (currentFile.getName().toLowerCase().endsWith(".json") ? "JSON" : "CSV") : "";
        statusLabel.setText(message + (fileType.isEmpty() ? "" : " (" + fileType + ")"));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + message);
    }
}
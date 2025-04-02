package org.example.latestVersions;

import org.example.Models.Client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CSVCRUD extends JFrame {
    private static final String[] COLUMN_HEADERS = {
            "ID", "First Name", "Last Name", "Company Name", "Email",
            "Address 1", "Country", "Phone Number", "Client Group ID",
            "Creation Date", "Notes"
    };

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private JTable table;
    private DefaultTableModel tableModel;
    private List<Client> clients = new ArrayList<>();
    private File currentFile;
    private JLabel statusLabel;

    public CSVCRUD() {
        initializeUI();
        setupTable();
        setupToolbar();
        setupStatusBar();
    }

    private void initializeUI() {
        setTitle("CSV Client Manager");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
    }

    private void setupTable() {
        tableModel = new DefaultTableModel(COLUMN_HEADERS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table cells non-editable directly
            }
        };

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        // Add right-click context menu
        JPopupMenu contextMenu = createContextMenu();
        table.setComponentPopupMenu(contextMenu);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Client Data"));
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem editItem = new JMenuItem("Edit Client");
        editItem.addActionListener(e -> updateRow());
        menu.add(editItem);

        JMenuItem deleteItem = new JMenuItem("Delete Client");
        deleteItem.addActionListener(e -> deleteRow());
        menu.add(deleteItem);

        JMenuItem viewItem = new JMenuItem("View Details");
        viewItem.addActionListener(e -> viewClientDetails());
        menu.add(viewItem);

        return menu;
    }

    private void setupToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(createToolButton("Open", "Load CSV file", e -> loadCSV()));
        toolBar.addSeparator();

        toolBar.add(createToolButton("Add", "Add new client", e -> insertRow()));
        toolBar.add(createToolButton("Edit", "Edit selected client", e -> updateRow()));
        toolBar.add(createToolButton("Delete", "Delete selected client", e -> deleteRow()));
        toolBar.addSeparator();

        toolBar.add(createToolButton("Save", "Save to CSV", e -> saveCSV()));
        toolBar.add(createToolButton("Save As", "Save to new CSV file", e -> exportCSV()));
        toolBar.addSeparator();

        toolBar.add(createToolButton("Filter", "Filter clients", e -> filterTableData()));
        toolBar.add(createToolButton("Clear", "Clear filters", e -> clearFilters()));
        toolBar.addSeparator();

        toolBar.add(createToolButton("Stats", "Show statistics", e -> showStatistics()));
        toolBar.add(createToolButton("About", "About this application", e -> showAbout()));

        add(toolBar, BorderLayout.NORTH);
    }

    private JButton createToolButton(String text, String tooltip, ActionListener listener) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.addActionListener(listener);
        return button;
    }

    private void setupStatusBar() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void loadCSV() {
        JFileChooser fileChooser = createFileChooser("Open CSV File");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            try (BufferedReader br = new BufferedReader(new FileReader(currentFile))) {
                clearTable();

                // Skip header if exists
                br.readLine();

                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1); // Handle quoted commas
                    if (values.length == COLUMN_HEADERS.length) {
                        try {
                            Client client = parseClient(values);
                            clients.add(client);
                            tableModel.addRow(values);
                        } catch (NumberFormatException | ParseException e) {
                            updateStatus("Error parsing line: " + line);
                        }
                    }
                }
                updateStatus("Loaded " + clients.size() + " clients from " + currentFile.getName());
            } catch (IOException ex) {
                showError("Error reading file: " + ex.getMessage());
            }
        }
    }

    private Client parseClient(String[] values) throws NumberFormatException, ParseException {
        return new Client(
                parseIntSafe(values[0].trim()),        // id
                cleanValue(values[1]),                // firstName
                cleanValue(values[2]),                 // lastName
                cleanValue(values[3]),                 // companyName
                cleanValue(values[4]),                 // email
                cleanValue(values[5]),                 // address1
                cleanValue(values[6]),                 // country
                cleanValue(values[7]),                 // phoneNumber
                parseIntSafe(values[8].trim()),        // clientGroupId
                cleanValue(values[9]),                 // creationDate
                cleanValue(values[10])                 // notes
        );
    }

    private String cleanValue(String value) {
        return value.replaceAll("^\"|\"$", "").trim();
    }

    private int parseIntSafe(String value) {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void saveCSV() {
        if (currentFile == null) {
            exportCSV();
            return;
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentFile))) {
            writeCSVData(bw);
            updateStatus("Data saved to " + currentFile.getName());
        } catch (IOException ex) {
            showError("Error saving file: " + ex.getMessage());
        }
    }

    private void exportCSV() {
        JFileChooser fileChooser = createFileChooser("Save CSV File");
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                writeCSVData(bw);
                currentFile = file;
                updateStatus("Data exported to " + file.getName());
            } catch (IOException ex) {
                showError("Error exporting to CSV: " + ex.getMessage());
            }
        }
    }

    private void writeCSVData(BufferedWriter bw) throws IOException {
        bw.write(String.join(",", COLUMN_HEADERS));
        bw.newLine();

        for (Client client : clients) {
            String[] row = {
                    String.valueOf(client.getId()),
                    quoteIfNeeded(client.getFirstName()),
                    quoteIfNeeded(client.getLastName()),
                    quoteIfNeeded(client.getCompanyName()),
                    quoteIfNeeded(client.getEmail()),
                    quoteIfNeeded(client.getAddress1()),
                    quoteIfNeeded(client.getCountry()),
                    quoteIfNeeded(client.getPhoneNumber()),
                    String.valueOf(client.getClientGroupId()),
                    quoteIfNeeded(client.getCreationDate()),
                    quoteIfNeeded(client.getNotes())
            };
            bw.write(String.join(",", row));
            bw.newLine();
        }
    }

    private String quoteIfNeeded(String value) {
        return value.contains(",") ? "\"" + value + "\"" : value;
    }

    private JFileChooser createFileChooser(String title) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
            }

            @Override
            public String getDescription() {
                return "CSV Files (*.csv)";
            }
        });
        return fileChooser;
    }

    private void insertRow() {
        ClientForm form = new ClientForm(this, "Add New Client", null);
        if (form.showForm()) {
            Client newClient = form.getClient();
            clients.add(newClient);
            tableModel.addRow(createRowData(newClient));
            updateStatus("Added new client: " + newClient.getFirstName());
        }
    }

    private void updateRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Please select a client to edit");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        Client clientToEdit = clients.get(modelRow);

        ClientForm form = new ClientForm(this, "Edit Client", clientToEdit);
        if (form.showForm()) {
            Client updatedClient = form.getClient();
            clients.set(modelRow, updatedClient);

            for (int i = 0; i < COLUMN_HEADERS.length; i++) {
                tableModel.setValueAt(createRowData(updatedClient)[i], modelRow, i);
            }

            updateStatus("Updated client: " + updatedClient.getFirstName());
        }
    }

    private String[] createRowData(Client client) {
        return new String[]{
                String.valueOf(client.getId()),
                client.getFirstName(),
                client.getLastName(),
                client.getCompanyName(),
                client.getEmail(),
                client.getAddress1(),
                client.getCountry(),
                client.getPhoneNumber(),
                String.valueOf(client.getClientGroupId()),
                client.getCreationDate(),
                client.getNotes()
        };
    }

    private void deleteRow() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Please select a client to delete");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        Client clientToDelete = clients.get(modelRow);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete client: " + clientToDelete.getFirstName() + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            clients.remove(modelRow);
            tableModel.removeRow(modelRow);
            updateStatus("Deleted client: " + clientToDelete.getFirstName());
        }
    }

    private void viewClientDetails() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            showWarning("Please select a client to view");
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        Client client = clients.get(modelRow);

        StringBuilder details = new StringBuilder();
        details.append("<html><b>Client Details:</b><br><br>");
        details.append("<b>ID:</b> ").append(client.getId()).append("<br>");
        details.append("<b>Name:</b> ").append(client.getFirstName()).append("<br>");
        details.append("<b>Company:</b> ").append(client.getCompanyName()).append("<br>");
        details.append("<b>Email:</b> ").append(client.getEmail()).append("<br>");
        details.append("<b>Address:</b> ").append(client.getAddress1()).append("<br>");
        details.append("<b>Country:</b> ").append(client.getCountry()).append("<br>");
        details.append("<b>Phone:</b> ").append(client.getPhoneNumber()).append("<br>");
        details.append("<b>Client Group ID:</b> ").append(client.getClientGroupId()).append("<br>");
        details.append("<b>Created:</b> ").append(client.getCreationDate()).append("<br>");
        details.append("<b>Notes:</b> ").append(client.getNotes()).append("<br>");
        details.append("</html>");

        JOptionPane.showMessageDialog(
                this,
                details.toString(),
                "Client Details: " + client.getFirstName(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void filterTableData() {
        JPanel filterPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        JComboBox<String> columnCombo = new JComboBox<>(COLUMN_HEADERS);
        JComboBox<String> operatorCombo = new JComboBox<>(new String[]{"contains", "equals", "starts with", "ends with"});
        JTextField valueField = new JTextField();

        filterPanel.add(new JLabel("Column:"));
        filterPanel.add(columnCombo);
        filterPanel.add(new JLabel("Operator:"));
        filterPanel.add(operatorCombo);
        filterPanel.add(new JLabel("Value:"));
        filterPanel.add(valueField);

        int result = JOptionPane.showConfirmDialog(
                this,
                filterPanel,
                "Filter Clients",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String column = (String) columnCombo.getSelectedItem();
            String operator = (String) operatorCombo.getSelectedItem();
            String value = valueField.getText().toLowerCase();

            int columnIndex = Arrays.asList(COLUMN_HEADERS).indexOf(column);

            List<Client> filteredClients = clients.stream()
                    .filter(client -> matchesFilter(client, columnIndex, operator, value))
                    .collect(Collectors.toList());

            updateTableWithFilteredData(filteredClients);
            updateStatus("Filtered: " + filteredClients.size() + " clients match criteria");
        }
    }

    private boolean matchesFilter(Client client, int columnIndex, String operator, String value) {
        String cellValue = getClientFieldValue(client, columnIndex).toLowerCase();

        switch (operator) {
            case "contains":
                return cellValue.contains(value);
            case "equals":
                return cellValue.equals(value);
            case "starts with":
                return cellValue.startsWith(value);
            case "ends with":
                return cellValue.endsWith(value);
            default:
                return false;
        }
    }

    private String getClientFieldValue(Client client, int columnIndex) {
        switch (columnIndex) {
            case 0: return String.valueOf(client.getId());
            case 1: return client.getFirstName();
            case 2: return client.getLastName();
            case 3: return client.getCompanyName();
            case 4: return client.getEmail();
            case 5: return client.getAddress1();
            case 6: return client.getCountry();
            case 7: return client.getPhoneNumber();
            case 8: return String.valueOf(client.getClientGroupId());
            case 9: return client.getCreationDate();
            case 10: return client.getNotes();
            default: return "";
        }
    }

    private void updateTableWithFilteredData(List<Client> filteredClients) {
        tableModel.setRowCount(0);
        for (Client client : filteredClients) {
            tableModel.addRow(createRowData(client));
        }
    }

    private void clearFilters() {
        updateTableWithFilteredData(clients);
        updateStatus("Filters cleared. Showing all " + clients.size() + " clients");
    }

    private void showStatistics() {
        if (clients.isEmpty()) {
            showInformation("No client data available for statistics");
            return;
        }

        // Basic statistics
        long totalClients = clients.size();
        long uniqueCountries = clients.stream().map(Client::getCountry).distinct().count();

        // Group statistics
        Map<Integer, Long> clientsByGroup = clients.stream()
                .collect(Collectors.groupingBy(Client::getClientGroupId, Collectors.counting()));

        // Country distribution
        Map<String, Long> countryDistribution = clients.stream()
                .collect(Collectors.groupingBy(Client::getCountry, Collectors.counting()));

        StringBuilder stats = new StringBuilder("<html><b>Client Statistics:</b><br><br>");
        stats.append("<b>Total Clients:</b> ").append(totalClients).append("<br>");
        stats.append("<b>Unique Countries:</b> ").append(uniqueCountries).append("<br><br>");

        stats.append("<b>Clients by Group:</b><br>");
        clientsByGroup.forEach((groupId, count) ->
                stats.append("Group ").append(groupId).append(": ").append(count).append("<br>"));

        stats.append("<br><b>Country Distribution:</b><br>");
        countryDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry ->
                        stats.append(entry.getKey()).append(": ").append(entry.getValue()).append("<br>"));

        stats.append("</html>");

        JOptionPane.showMessageDialog(
                this,
                stats.toString(),
                "Client Statistics",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void clearTable() {
        tableModel.setRowCount(0);
        clients.clear();
        updateStatus("Table cleared");
    }

    private void showAbout() {
        String about = "<html><center><b>CSV Client Manager</b><br>"
                + "Version 1.0<br><br>"
                + "A simple application for managing client data in CSV files.<br>"
                + "Supports CRUD operations, filtering, and basic statistics.<br><br>"
                + "© 2025 4Property - Hamza - Derouich</center></html>";

        JOptionPane.showMessageDialog(
                this,
                about,
                "About",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        updateStatus("Error: " + message);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
        updateStatus("Warning: " + message);
    }

    private void showInformation(String message) {
        JOptionPane.showMessageDialog(this, message, "Information", JOptionPane.INFORMATION_MESSAGE);
        updateStatus(message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            CSVCRUD frame = new CSVCRUD();
            frame.setVisible(true);
        });
    }
}

class ClientForm {
    private JDialog dialog;
    private JTextField idField;
    private JTextField firstNameField;
    private JTextField lastNameField;
    private JTextField companyNameField;
    private JTextField emailField;
    private JTextField address1Field;
    private JTextField countryField;
    private JTextField phoneNumberField;
    private JTextField clientGroupIdField;
    private JTextField creationDateField;
    private JTextArea notesArea;
    private boolean confirmed = false;

    public ClientForm(JFrame parent, String title, Client client) {
        dialog = new JDialog(parent, title, true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 500);
        dialog.setLocationRelativeTo(parent);

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        idField = new JTextField();
        firstNameField = new JTextField();
        lastNameField = new JTextField();
        companyNameField = new JTextField();
        emailField = new JTextField();
        address1Field = new JTextField();
        countryField = new JTextField();
        phoneNumberField = new JTextField();
        clientGroupIdField = new JTextField();
        creationDateField = new JTextField();
        notesArea = new JTextArea(3, 20);

        // Set current date as default for creation date
        creationDateField.setText(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

        if (client != null) {
            idField.setText(String.valueOf(client.getId()));
            firstNameField.setText(client.getFirstName());
            lastNameField.setText(client.getLastName());
            companyNameField.setText(client.getCompanyName());
            emailField.setText(client.getEmail());
            address1Field.setText(client.getAddress1());
            countryField.setText(client.getCountry());
            phoneNumberField.setText(client.getPhoneNumber());
            clientGroupIdField.setText(String.valueOf(client.getClientGroupId()));
            creationDateField.setText(client.getCreationDate());
            notesArea.setText(client.getNotes());
        }

        formPanel.add(new JLabel("ID:"));
        formPanel.add(idField);
        formPanel.add(new JLabel("First Name: *"));
        formPanel.add(firstNameField);
        formPanel.add(new JLabel("Last Name: *"));
        formPanel.add(lastNameField);
        formPanel.add(new JLabel("Company Name:"));
        formPanel.add(companyNameField);
        formPanel.add(new JLabel("Email: *"));
        formPanel.add(emailField);
        formPanel.add(new JLabel("Address:"));
        formPanel.add(address1Field);
        formPanel.add(new JLabel("Country:"));
        formPanel.add(countryField);
        formPanel.add(new JLabel("Phone:"));
        formPanel.add(phoneNumberField);
        formPanel.add(new JLabel("Client Group ID:"));
        formPanel.add(clientGroupIdField);
        formPanel.add(new JLabel("Creation Date:"));
        formPanel.add(creationDateField);
        formPanel.add(new JLabel("Notes:"));
        formPanel.add(new JScrollPane(notesArea));

        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            if (validateForm()) {
                confirmed = true;
                dialog.dispose();
            }
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
    }

    public boolean showForm() {
        dialog.setVisible(true);
        return confirmed;
    }

    public Client getClient() {
        try {
            return new Client(
                    Integer.parseInt(idField.getText()),
                    firstNameField.getText(),
                    lastNameField.getText(),
                    companyNameField.getText(),
                    emailField.getText(),
                    address1Field.getText(),
                    countryField.getText(),
                    phoneNumberField.getText(),
                    clientGroupIdField.getText().isEmpty() ? 0 : Integer.parseInt(clientGroupIdField.getText()),
                    creationDateField.getText(),
                    notesArea.getText()
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean validateForm() {
        if (firstNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "First name is required", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (lastNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Last name is required", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (emailField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(dialog, "Email is required", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (!emailField.getText().contains("@")) {
            JOptionPane.showMessageDialog(dialog, "Please enter a valid email address", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            if (!idField.getText().isEmpty()) {
                Integer.parseInt(idField.getText());
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(dialog, "ID must be a number", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            if (!clientGroupIdField.getText().isEmpty()) {
                Integer.parseInt(clientGroupIdField.getText());
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(dialog, "Client Group ID must be a number", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }
}
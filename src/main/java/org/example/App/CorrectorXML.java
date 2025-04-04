package org.example.App;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class CorrectorXML extends JFrame {
    private Document doc;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTree xmlTree;
    private DefaultTreeModel treeModel;
    private JTabbedPane tabbedPane;
    private File currentFile;
    private JTextArea previewArea;

    public static void main(String[] args) {

    }

    public CorrectorXML() {
        setTitle("XML Editor/Corrector");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        createMenu();
    }

    private void initUI() {
        // Main panel with tabbed interface
        tabbedPane = new JTabbedPane();

        // Tree View
        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("XML Structure"));
        xmlTree = new JTree(treeModel);
        xmlTree.setCellRenderer(new XmlTreeCellRenderer());
        xmlTree.addTreeSelectionListener(e -> updateTableFromSelectedNode());

        JScrollPane treeScroll = new JScrollPane(xmlTree);
        tabbedPane.addTab("Tree View", treeScroll);

        // Table View
        tableModel = new DefaultTableModel(new Object[]{"Node", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        table = new JTable(tableModel);
        table.getModel().addTableModelListener(e -> updateXmlFromTable());

        JScrollPane tableScroll = new JScrollPane(table);
        tabbedPane.addTab("Table View", tableScroll);

        // Preview Panel
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewPanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        JButton refreshPreviewBtn = new JButton("Refresh Preview");
        refreshPreviewBtn.addActionListener(e -> updatePreview());
        previewPanel.add(refreshPreviewBtn, BorderLayout.SOUTH);

        tabbedPane.addTab("XML Preview", previewPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Status bar
        JLabel statusBar = new JLabel("Ready");
        add(statusBar, BorderLayout.SOUTH);
    }

    private void createMenu() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open XML");
        openItem.addActionListener(e -> openXmlFile());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveXmlFile());

        JMenuItem exportItem = new JMenuItem("Export to...");
        exportItem.addActionListener(e -> showExportOptions());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(exportItem);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");

        JMenuItem cleanItem = new JMenuItem("Clean XML");
        cleanItem.addActionListener(e -> cleanCurrentXml());

        editMenu.add(cleanItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);

        setJMenuBar(menuBar);
    }

    private void openXmlFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            loadXmlFile(currentFile);
        }
    }

    private void loadXmlFile(File file) {
        try {
            // Clean the file first
            String cleanedPath = file.getParent() + "/" + file.getName().replace(".xml", "_cleaned.xml");
            limpiarArchivoXML(file.getAbsolutePath(), cleanedPath);

            // Parse the cleaned file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new File(cleanedPath));
            doc.getDocumentElement().normalize();

            // Build the tree view
            buildXmlTree(doc);

            // Update table view with root elements
            updateTableFromNode(doc.getDocumentElement());

            // Update preview
            updatePreview();

            JOptionPane.showMessageDialog(this, "XML loaded successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading XML: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void buildXmlTree(Document doc) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new XmlNodeWrapper(doc.getDocumentElement()));
        buildTreeNodes(doc.getDocumentElement(), root);
        treeModel.setRoot(root);
        expandAllNodes(xmlTree);
    }

    private void expandAllNodes(JTree tree) {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    private void buildTreeNodes(Node xmlNode, DefaultMutableTreeNode treeNode) {
        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(new XmlNodeWrapper(child));
                treeNode.add(childTreeNode);
                buildTreeNodes(child, childTreeNode);
            }
        }
    }

    private void updateTableFromSelectedNode() {
        TreePath path = xmlTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            XmlNodeWrapper wrapper = (XmlNodeWrapper) node.getUserObject();
            updateTableFromNode(wrapper.getNode());
        }
    }

    private void updateTableFromNode(Node xmlNode) {
        tableModel.setRowCount(0);

        if (xmlNode == doc.getDocumentElement()) {
            tableModel.addRow(new Object[]{"Document", xmlNode.getNodeName()});
        }

        NamedNodeMap attributes = xmlNode.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                tableModel.addRow(new Object[]{
                        "@" + attr.getNodeName(),
                        attr.getNodeValue()
                });
            }
        }

        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                tableModel.addRow(new Object[]{
                        child.getNodeName(),
                        getFullElementContent(child)
                });

                if (child.hasAttributes()) {
                    NamedNodeMap childAttrs = child.getAttributes();
                    for (int j = 0; j < childAttrs.getLength(); j++) {
                        Node attr = childAttrs.item(j);
                        tableModel.addRow(new Object[]{
                                child.getNodeName() + "/@" + attr.getNodeName(),
                                attr.getNodeValue()
                        });
                    }
                }
            }
            else if (child.getNodeType() == Node.TEXT_NODE &&
                    !child.getTextContent().trim().isEmpty()) {
                tableModel.addRow(new Object[]{
                        "#text",
                        child.getTextContent().trim()
                });
            }
            else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                tableModel.addRow(new Object[]{
                        "#cdata",
                        child.getTextContent()
                });
            }
            else if (child.getNodeType() == Node.COMMENT_NODE) {
                tableModel.addRow(new Object[]{
                        "#comment",
                        child.getTextContent()
                });
            }
        }
    }

    private String getFullElementContent(Node element) {
        StringBuilder content = new StringBuilder();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                content.append(child.getTextContent().trim());
            }
            else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                content.append(child.getTextContent());
            }
        }

        return content.toString();
    }

    private String getElementText(Node element) {
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                text.append(child.getTextContent());
            }
        }
        return text.toString();
    }

    private void updateXmlFromTable() {
        TreePath path = xmlTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        XmlNodeWrapper wrapper = (XmlNodeWrapper) treeNode.getUserObject();
        Node xmlNode = wrapper.getNode();

        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String nodeName = tableModel.getValueAt(row, 0).toString();
            String value = tableModel.getValueAt(row, 1).toString();

            if (nodeName.startsWith("@")) {
                // Update attribute
                String attrName = nodeName.substring(1);
                ((Element) xmlNode).setAttribute(attrName, value);
            } else if (nodeName.equals("#text")) {
                // Update text content
                NodeList children = xmlNode.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.TEXT_NODE) {
                        child.setTextContent(value);
                        break;
                    }
                }
            } else {
                // Update element text content
                NodeList children = xmlNode.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE &&
                            child.getNodeName().equals(nodeName)) {
                        Node textNode = findTextNode(child);
                        if (textNode != null) {
                            textNode.setTextContent(value);
                        } else {
                            child.appendChild(doc.createTextNode(value));
                        }
                        break;
                    }
                }
            }
        }

        updatePreview();
    }

    private Node findTextNode(Node parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                return children.item(i);
            }
        }
        return null;
    }

    private void updatePreview() {
        if (doc == null) return;

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            previewArea.setText(writer.toString());
        } catch (Exception e) {
            previewArea.setText("Error generating preview: " + e.getMessage());
        }
    }

    private void saveXmlFile() {
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "No XML document loaded", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(currentFile);
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".xml")) {
                file = new File(file.getAbsolutePath() + ".xml");
            }

            try {
                guardarXML(doc, file);
                currentFile = file;
                JOptionPane.showMessageDialog(this, "XML saved successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error saving XML: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showExportOptions() {
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "No XML document loaded", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] options = {"JSON", "CSV", "HTML"};
        String choice = (String) JOptionPane.showInputDialog(
                this,
                "Select export format:",
                "Export Options",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

        if (choice != null) {
            switch (choice) {
                case "JSON":
                    exportToJson();
                    break;
                case "CSV":
                    exportToCsv();
                    break;
                case "HTML":
                    exportToHtml();
                    break;
            }
        }
    }

    private void exportToJson() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(currentFile.getAbsolutePath().replace(".xml", ".json")));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String json = convertXmlToJson(doc.getDocumentElement());
                Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
                JOptionPane.showMessageDialog(this, "Exported to JSON successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error exporting to JSON: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String convertXmlToJson(Node node) {
        StringBuilder json = new StringBuilder();
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            json.append("{");

            // Nombre del nodo
            json.append("\"nodeName\":\"").append(node.getNodeName()).append("\",");

            // Atributos
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null && attributes.getLength() > 0) {
                json.append("\"attributes\": {");
                for (int i = 0; i < attributes.getLength(); i++) {
                    if (i > 0) json.append(",");
                    Node attr = attributes.item(i);
                    json.append("\"").append(attr.getNodeName()).append("\":\"")
                            .append(escapeJson(attr.getNodeValue())).append("\"");
                }
                json.append("},");
            }

            // Contenido
            String content = getFullElementContent(node);
            if (!content.isEmpty()) {
                json.append("\"content\":\"").append(escapeJson(content)).append("\",");
            }

            // Hijos
            NodeList children = node.getChildNodes();
            List<Node> elementChildren = new ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    elementChildren.add(child);
                }
            }

            if (!elementChildren.isEmpty()) {
                json.append("\"children\": [");
                for (int i = 0; i < elementChildren.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(convertXmlToJson(elementChildren.get(i)));
                }
                json.append("]");
            } else {
                // Eliminar la última coma si no hay hijos
                if (json.charAt(json.length()-1) == ',') {
                    json.deleteCharAt(json.length()-1);
                }
            }

            json.append("}");
        }
        return json.toString();
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

    private void exportToCsv() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(currentFile.getAbsolutePath().replace(".xml", ".csv")));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                // Write headers
                writer.println("Node,Value");

                // Write data from current table view
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    writer.println("\"" + tableModel.getValueAt(row, 0) + "\",\"" +
                            tableModel.getValueAt(row, 1) + "\"");
                }

                JOptionPane.showMessageDialog(this, "Exported to CSV successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error exporting to CSV: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToHtml() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(currentFile.getAbsolutePath().replace(".xml", ".html")));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                StringBuilder html = new StringBuilder();
                html.append("<!DOCTYPE html>\n")
                        .append("<html lang='es'>\n")
                        .append("<head>\n")
                        .append("    <meta charset='UTF-8'>\n")
                        .append("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n")
                        .append("    <title>XML Export - ").append(escapeHtml(currentFile.getName())).append("</title>\n")
                        .append("    <link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css'>\n")
                        .append("    <link href='https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css' rel='stylesheet'>\n")
                        .append("    <link rel='stylesheet' href='https://cdn.datatables.net/1.11.5/css/dataTables.bootstrap5.min.css'>\n")
                        .append("    <style>\n")
                        .append("        body { padding: 20px; background-color: #f8f9fa; }\n")
                        .append("        .card { border-radius: 10px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }\n")
                        .append("        .card-header { background-color: #0d6efd; color: white; border-radius: 10px 10px 0 0 !important; }\n")
                        .append("        .attribute { color: #0d6efd; font-weight: bold; }\n")
                        .append("        .text-node { color: #198754; font-style: italic; }\n")
                        .append("        .cdata-node { color: #6c757d; }\n")
                        .append("        .comment-node { color: #6c757d; font-style: italic; }\n")
                        .append("        .badge-type { background-color: #6c757d; }\n")
                        .append("        #searchBox { margin-bottom: 15px; }\n")
                        .append("        .dataTables_filter, .dataTables_length { margin-bottom: 15px; }\n")
                        .append("        table.dataTable thead th { border-bottom: 2px solid #dee2e6; }\n")
                        .append("    </style>\n")
                        .append("</head>\n")
                        .append("<body>\n")
                        .append("    <div class='container-fluid'>\n")
                        .append("        <div class='card'>\n")
                        .append("            <div class='card-header d-flex justify-content-between align-items-center'>\n")
                        .append("                <h5 class='mb-0'><i class='fas fa-file-code me-2'></i>XML Explorer: ").append(escapeHtml(currentFile.getName())).append("</h5>\n")
                        .append("                <div class='badge bg-light text-dark'><i class='fas fa-calendar-alt me-1'></i>").append(new Date()).append("</div>\n")
                        .append("            </div>\n")
                        .append("            <div class='card-body'>\n")
                        .append("                <div class='row mb-3'>\n")
                        .append("                    <div class='col-md-6'>\n")
                        .append("                        <div class='input-group'>\n")
                        .append("                            <span class='input-group-text'><i class='fas fa-search'></i></span>\n")
                        .append("                            <input type='text' id='searchBox' class='form-control' placeholder='Buscar en todos los datos...'>\n")
                        .append("                        </div>\n")
                        .append("                    </div>\n")
                        .append("                    <div class='col-md-6 text-end'>\n")
                        .append("                        <div class='btn-group'>\n")
                        .append("                            <button class='btn btn-sm btn-outline-secondary' onclick='filterTable(\"all\")'>\n")
                        .append("                                <i class='fas fa-list'></i> Todos\n")
                        .append("                            </button>\n")
                        .append("                            <button class='btn btn-sm btn-outline-primary' onclick='filterTable(\"attribute\")'>\n")
                        .append("                                <i class='fas fa-tag'></i> Atributos\n")
                        .append("                            </button>\n")
                        .append("                            <button class='btn btn-sm btn-outline-success' onclick='filterTable(\"element\")'>\n")
                        .append("                                <i class='fas fa-code'></i> Elementos\n")
                        .append("                            </button>\n")
                        .append("                            <button class='btn btn-sm btn-outline-info' onclick='filterTable(\"text\")'>\n")
                        .append("                                <i class='fas fa-font'></i> Texto\n")
                        .append("                            </button>\n")
                        .append("                        </div>\n")
                        .append("                    </div>\n")
                        .append("                </div>\n")
                        .append("                <div class='table-responsive'>\n")
                        .append("                    <table id='xmlTable' class='table table-striped table-hover table-bordered w-100'>\n")
                        .append("                        <thead class='table-light'>\n")
                        .append("                            <tr>\n")
                        .append("                                <th>Tipo</th>\n")
                        .append("                                <th>Nodo</th>\n")
                        .append("                                <th>Valor</th>\n")
                        .append("                                <th>Ruta</th>\n")
                        .append("                            </tr>\n")
                        .append("                        </thead>\n")
                        .append("                        <tbody>\n");

                // Generar filas de la tabla
                generateHtmlTableRows(html, doc.getDocumentElement(), "");

                html.append("                        </tbody>\n")
                        .append("                    </table>\n")
                        .append("                </div>\n")
                        .append("            </div>\n")
                        .append("            <div class='card-footer text-muted small'>\n")
                        .append("                <div class='d-flex justify-content-between'>\n")
                        .append("                    <div>Total nodos: <span id='totalNodes'></span></div>\n")
                        .append("                    <div>Exportado con XML Editor - ").append(new Date()).append("</div>\n")
                        .append("                </div>\n")
                        .append("            </div>\n")
                        .append("        </div>\n")
                        .append("    </div>\n\n")
                        .append("    <script src='https://code.jquery.com/jquery-3.6.0.min.js'></script>\n")
                        .append("    <script src='https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js'></script>\n")
                        .append("    <script src='https://cdn.datatables.net/1.11.5/js/jquery.dataTables.min.js'></script>\n")
                        .append("    <script src='https://cdn.datatables.net/1.11.5/js/dataTables.bootstrap5.min.js'></script>\n")
                        .append("    <script>\n")
                        .append("        $(document).ready(function() {\n")
                        .append("            var table = $('#xmlTable').DataTable({\n")
                        .append("                dom: '<\"top\"lf>rt<\"bottom\"ip>',\n")
                        .append("                pageLength: 25,\n")
                        .append("                language: {\n")
                        .append("                    url: 'https://cdn.datatables.net/plug-ins/1.11.5/i18n/es-ES.json'\n")
                        .append("                },\n")
                        .append("                initComplete: function() {\n")
                        .append("                    $('#totalNodes').text(this.api().data().length);\n")
                        .append("                }\n")
                        .append("            });\n\n")
                        .append("            $('#searchBox').keyup(function() {\n")
                        .append("                table.search(this.value).draw();\n")
                        .append("            });\n")
                        .append("        });\n\n")
                        .append("        function filterTable(type) {\n")
                        .append("            var table = $('#xmlTable').DataTable();\n")
                        .append("            if (type === 'all') {\n")
                        .append("                table.columns(0).search('').draw();\n")
                        .append("            } else {\n")
                        .append("                table.columns(0).search(type).draw();\n")
                        .append("            }\n")
                        .append("        }\n")
                        .append("    </script>\n")
                        .append("</body>\n")
                        .append("</html>");

                Files.write(file.toPath(), html.toString().getBytes(StandardCharsets.UTF_8));
                JOptionPane.showMessageDialog(this, "HTML exportado exitosamente", "Éxito", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error al exportar HTML: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateHtmlTableRows(StringBuilder html, Node node, String path) {
        String nodeType = "";
        String nodeClass = "";
        String icon = "";

        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                nodeType = "Elemento";
                nodeClass = "";
                icon = "<i class='fas fa-code me-1'></i>";
                String currentPath = path + "/" + node.getNodeName();

                // Mostrar el elemento
                html.append("<tr>")
                        .append("<td><span class='badge bg-primary'>").append(icon).append(nodeType).append("</span></td>")
                        .append("<td><strong>").append(escapeHtml(node.getNodeName())).append("</strong></td>")
                        .append("<td>").append(escapeHtml(getFullElementContent(node))).append("</td>")
                        .append("<td>").append(escapeHtml(currentPath)).append("</td>")
                        .append("</tr>\n");

                // Mostrar atributos
                NamedNodeMap attributes = node.getAttributes();
                if (attributes != null) {
                    for (int i = 0; i < attributes.getLength(); i++) {
                        Node attr = attributes.item(i);
                        html.append("<tr>")
                                .append("<td><span class='badge bg-info'><i class='fas fa-tag me-1'></i>Atributo</span></td>")
                                .append("<td class='attribute'>@").append(escapeHtml(attr.getNodeName())).append("</td>")
                                .append("<td>").append(escapeHtml(attr.getNodeValue())).append("</td>")
                                .append("<td>").append(escapeHtml(currentPath)).append("</td>")
                                .append("</tr>\n");
                    }
                }

                // Procesar hijos
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    generateHtmlTableRows(html, children.item(i), currentPath);
                }
                break;

            case Node.TEXT_NODE:
                if (!node.getTextContent().trim().isEmpty()) {
                    nodeType = "Texto";
                    nodeClass = "text-node";
                    icon = "<i class='fas fa-font me-1'></i>";
                    html.append("<tr>")
                            .append("<td><span class='badge bg-success'>").append(icon).append(nodeType).append("</span></td>")
                            .append("<td class='").append(nodeClass).append("'>#text</td>")
                            .append("<td class='").append(nodeClass).append("'>").append(escapeHtml(node.getTextContent().trim())).append("</td>")
                            .append("<td>").append(escapeHtml(path)).append("</td>")
                            .append("</tr>\n");
                }
                break;

            case Node.CDATA_SECTION_NODE:
                nodeType = "CDATA";
                nodeClass = "cdata-node";
                icon = "<i class='fas fa-file-code me-1'></i>";
                html.append("<tr>")
                        .append("<td><span class='badge bg-secondary'>").append(icon).append(nodeType).append("</span></td>")
                        .append("<td class='").append(nodeClass).append("'>#cdata</td>")
                        .append("<td class='").append(nodeClass).append("'>").append(escapeHtml(node.getTextContent())).append("</td>")
                        .append("<td>").append(escapeHtml(path)).append("</td>")
                        .append("</tr>\n");
                break;

            case Node.COMMENT_NODE:
                nodeType = "Comentario";
                nodeClass = "comment-node";
                icon = "<i class='fas fa-comment me-1'></i>";
                html.append("<tr>")
                        .append("<td><span class='badge bg-warning text-dark'>").append(icon).append(nodeType).append("</span></td>")
                        .append("<td class='").append(nodeClass).append("'>#comment</td>")
                        .append("<td class='").append(nodeClass).append("'>").append(escapeHtml(node.getTextContent())).append("</td>")
                        .append("<td>").append(escapeHtml(path)).append("</td>")
                        .append("</tr>\n");
                break;
        }
    }

    private String escapeHtml(String str) {
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void cleanCurrentXml() {
        if (doc == null) {
            JOptionPane.showMessageDialog(this, "No XML document loaded", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        corregirTexto(doc);
        updatePreview();
        JOptionPane.showMessageDialog(this, "XML cleaned successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // Your original methods with slight modifications
    private static void limpiarArchivoXML(String inputPath, String outputPath) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(inputPath)), StandardCharsets.UTF_8);
            content = content.replaceAll("&(?!amp;|lt;|gt;|quot;|apos;)", "&amp;");
            Files.write(Paths.get(outputPath), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error cleaning XML file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void corregirTexto(Document doc) {
        NodeList elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Node node = elements.item(i);
            NodeList children = node.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    String text = child.getTextContent();
                    text = text.replaceAll("[“”]", "\"");
                    text = text.replaceAll("[‘’]", "'");
                    text = text.replaceAll("\u00A0", " ");
                    child.setTextContent(text);
                }
            }
        }
    }

    private static void guardarXML(Document doc, File outputFile) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(outputFile);
        transformer.transform(source, result);
    }

    // Helper class for XML tree nodes
    private static class XmlNodeWrapper {
        private Node node;

        public XmlNodeWrapper(Node node) {
            this.node = node;
        }

        public Node getNode() {
            return node;
        }

        @Override
        public String toString() {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return node.getNodeName();
            }
            return node.getNodeValue();
        }
    }

    // Custom tree cell renderer
    private static class XmlTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof XmlNodeWrapper) {
                XmlNodeWrapper wrapper = (XmlNodeWrapper) node.getUserObject();
                if (wrapper.getNode().getNodeType() == Node.ELEMENT_NODE) {
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                } else {
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                }
            }
            return this;
        }
    }
}
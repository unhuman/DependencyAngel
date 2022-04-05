package com.unhuman.dependencyresolver.pom;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

public class PomManipulator {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\r?\\n\\s+");
    private static final String COMMENT_ADD_DEPENDENCY_START = "Forced Transitive Dependency Start";
    private static final String COMMENT_ADD_DEPENDENCY_END = "Forced Transitive Dependency End";
    private String filename;
    private Document document;
    private boolean changed;
    public PomManipulator(String filename) throws ParserConfigurationException, IOException, SAXException {
        this.filename = filename;
        changed = false;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.parse(new File(filename));
        document.getDocumentElement().normalize();
    }

    public void stripExclusions() {
        stripNodes("exclusions");
    }

    public void stripForcedTransitiveDependencies() {
        NodeList dependenciesNodes = document.getElementsByTagName("dependencies");

        for (int i = 0; i < dependenciesNodes.getLength(); i++ ) {
            NodeList dependencies = dependenciesNodes.item(i).getChildNodes();
            boolean deleting = false;

            for (int j = 0; j < dependencies.getLength(); j++) {
                Node node = dependencies.item(j);

                while (node != null) {
                    if (Node.COMMENT_NODE == node.getNodeType()) {
                        if (COMMENT_ADD_DEPENDENCY_START.equals(node.getTextContent().trim())) {
                            if (deleting) {
                                throw new RuntimeException("Invalid Transitive Dependency Start comment tag");
                            }
                            // this will be deleted below
                            deleting = true;
                        } else if (COMMENT_ADD_DEPENDENCY_END.equals(node.getTextContent().trim())) {
                            if (!deleting) {
                                throw new RuntimeException("Invalid Transitive Dependency End comment tag");
                            }
                            // we have to delete this here, because we're going to turn deleting off
                            deleteNode(node, true);
                            deleting = false;
                        }
                    }

                    Node nextNode = node.getNextSibling();
                    // Delete anything left over, otherwise we're done
                    if (deleting) {
                        deleteNode(node, true);
                    }
                    node = nextNode;
                }
            }

            if (deleting) {
                throw new RuntimeException("Missing Transitive Dependency End comment tag");
            }
        }
    }

    protected void stripNodes(String nodeId) {
        NodeList exclusionNodes = document.getElementsByTagName(nodeId);
        for (int i = 0; i < exclusionNodes.getLength(); i++) {
            deleteNode(exclusionNodes.item(i), true);
        }
    }

    protected void deleteNode(Node deleteNode, boolean cleanPriorWhitespace) {
        // clean up any indentation
        if (cleanPriorWhitespace) {
            Node indentationNode = deleteNode.getPreviousSibling();
            if (indentationNode != null && Node.TEXT_NODE == indentationNode.getNodeType()
                && WHITESPACE_PATTERN.matcher(indentationNode.getTextContent()).matches()) {
                indentationNode.getParentNode().removeChild(indentationNode);
            }
        }

        // delete the desired node
        deleteNode.getParentNode().removeChild(deleteNode);

        changed = true;
    }

    public boolean isChanged() {
        return changed;
    }

    public void saveFile() throws TransformerException, FileNotFoundException {
        // only save if something changed
        if (!changed) {
            return;
        }

        FileOutputStream output = new FileOutputStream(filename);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(output);

        transformer.transform(source, result);
    }
}

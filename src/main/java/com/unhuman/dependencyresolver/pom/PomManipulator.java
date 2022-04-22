package com.unhuman.dependencyresolver.pom;

import com.unhuman.dependencyresolver.versioning.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
    private static final String DEPENDENCIES_TAG = "dependencies";
    private static final String DEPENDENCY_TAG = "dependency";
    private static final String GROUP_ID_TAG = "groupId";
    private static final String ARTIFACT_TAG = "artifact";
    private static final String VERSION_TAG = "version";
    private static final String SCOPE_TAG = "scope";
    private static final String EXCLUSIONS_TAG = "exclusions";

    private static final String NEW_LINE = "\n";

    private static final String COMMENT_ADD_DEPENDENCY_START = "Forced Transitive Dependency Start";
    private static final String COMMENT_ADD_DEPENDENCY_END = "Forced Transitive Dependency End";
    private String filename;

    private Document document;
    private Node dependenciesNode;

    private boolean changed;

    private String dependenciesIndentation;
    private String dependencyIndentation;
    private String dependencyContentIndentation;

    public PomManipulator(String filename) throws ParserConfigurationException, IOException, SAXException {
        this.filename = filename;
        changed = false;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.parse(new File(filename));
        document.getDocumentElement().normalize();

        // determine indentations
        NodeList dependenciesNodes = document.getElementsByTagName(DEPENDENCIES_TAG);
        if (dependenciesNodes.getLength() != 1) {
            throw new RuntimeException("Expected a single <dependencies> element");
        }
        dependenciesNode = dependenciesNodes.item(0);
        dependenciesIndentation = findNodeIndentation(dependenciesNode);

        // Find indentations we need to use for child nodes
        Node dependencyNode = findChildNode(dependenciesNode, Node.ELEMENT_NODE, DEPENDENCY_TAG);
        Node groupIdNode = null;
        if (dependencyNode != null) {
            if (dependencyIndentation == null) {
                dependencyIndentation = findNodeIndentation(dependencyNode);
            }
            groupIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, GROUP_ID_TAG);
        }
        if (groupIdNode != null) {
            if (dependencyContentIndentation == null) {
                dependencyContentIndentation = findNodeIndentation(groupIdNode);
            }
        }
    }

    Node findChildNode(Node parentNode, short nodeType, String nodeName) {
        NodeList childNodeList = parentNode.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); i++) {
            Node childNodeCheck = childNodeList.item(i);
            if (childNodeCheck.getNodeType() == nodeType && childNodeCheck.getNodeName().equals(nodeName)) {
                return childNodeCheck;
            }
        }
        return null;
    }

    String findNodeIndentation(Node node) {
        Node indentationNode = node.getPreviousSibling();
        if (Node.TEXT_NODE == indentationNode.getNodeType() && indentationNode.getTextContent().isBlank()) {
            return indentationNode.getTextContent();
        }
        return null;
    }

    // TODO move this
    public void addForcedTransitiveDependencyNode(String groupId, String artifactId, Version version, String scope) {
        changed = true;

        dependenciesNode.appendChild(document.createTextNode(dependencyIndentation));
        dependenciesNode.appendChild(document.createComment(COMMENT_ADD_DEPENDENCY_START));

        Node newDependency = document.createElement(DEPENDENCY_TAG);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node groupIdNode = document.createElement(GROUP_ID_TAG);
        groupIdNode.setTextContent(groupId);
        newDependency.appendChild(groupIdNode);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node artifactNode = document.createElement(ARTIFACT_TAG);
        artifactNode.setTextContent(artifactId);
        newDependency.appendChild(artifactNode);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node versionNode = document.createElement(VERSION_TAG);
        versionNode.setTextContent(version.toString());
        newDependency.appendChild(versionNode);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node scopeNode = document.createElement(SCOPE_TAG);
        scopeNode.setTextContent(scope);
        newDependency.appendChild(scopeNode);

        // add an indentation to the end of the last element so the closing element looks correct
        newDependency.appendChild(document.createTextNode(dependencyIndentation));

        dependenciesNode.appendChild(document.createTextNode(dependencyIndentation));
        dependenciesNode.appendChild(newDependency);

        dependenciesNode.appendChild(document.createTextNode(dependencyIndentation));
        dependenciesNode.appendChild(document.createComment(COMMENT_ADD_DEPENDENCY_END));
        dependenciesNode.appendChild(document.createTextNode(dependenciesIndentation));
    }

    public void stripExclusions() {
        stripNodes(EXCLUSIONS_TAG);
    }

    public void stripForcedTransitiveDependencies() {
        NodeList dependencies = dependenciesNode.getChildNodes();
        boolean deleting = false;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);

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

                // Track where we are and delete if necessary
                Node nextNode = node.getNextSibling();
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

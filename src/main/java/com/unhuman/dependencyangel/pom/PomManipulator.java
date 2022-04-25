package com.unhuman.dependencyangel.pom;

import com.unhuman.dependencyangel.versioning.Version;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PomManipulator {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\r?\\n\\s+");
    private static final String PROPERTIES_TAG = "properties";
    private static final String DEPENDENCY_MANAGEMENT_TAG = "dependencyManagement";
    private static final String DEPENDENCIES_TAG = "dependencies";
    private static final String DEPENDENCY_TAG = "dependency";
    private static final String GROUP_ID_TAG = "groupId";
    private static final String ARTIFACT_ID_TAG = "artifactId";
    private static final String VERSION_TAG = "version";
    private static final String SCOPE_TAG = "scope";
    private static final String EXCLUSIONS_TAG = "exclusions";
    private static final String EXCLUSION_TAG = "exclusion";

    private static final String NEW_LINE = "\n";

    private static final String COMMENT_ADD_DEPENDENCY_START = "Forced Dependency Start";
    private static final String COMMENT_ADD_DEPENDENCY_END = "Forced Dependency End";
    private String filename;

    private Document document;
    private Node dependenciesNode;
    private Node propertiesNode;

    private boolean changed;

    private String dependenciesIndentation;
    private String dependencyIndentation;
    private String nestedIndentation = "    ";
    private String dependencyContentIndentation;

    public PomManipulator(String filename) throws ParserConfigurationException, IOException, SAXException {
        this.filename = filename;
        changed = false;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        document = builder.parse(new File(filename));
        document.getDocumentElement().normalize();

        Node projectNode = document.getFirstChild();
        if (!projectNode.getNodeName().equals("project")) {
            throw new RuntimeException("Could not find project node");
        }
        // determine indentations
        propertiesNode = findDesiredNode(document.getElementsByTagName(PROPERTIES_TAG), projectNode, projectNode);
        Node dependencyManagementList = findDesiredNode(
                document.getElementsByTagName(DEPENDENCY_MANAGEMENT_TAG), projectNode, projectNode);
        dependenciesNode = findDesiredNode(document.getElementsByTagName(DEPENDENCIES_TAG),
                dependencyManagementList, projectNode);

        dependenciesIndentation = findNodeIndentation(dependenciesNode);

        // Find indentations we need to use for child nodes
        List<Node> dependencyNodes = findChildNodes(dependenciesNode, Node.ELEMENT_NODE, DEPENDENCY_TAG);
        Node groupIdNode = null;
        if (dependencyNodes.size() > 0) {
            Node dependencyNode = dependencyNodes.get(0);
            if (dependencyIndentation == null) {
                dependencyIndentation = findNodeIndentation(dependencyNode);
            }
            groupIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, GROUP_ID_TAG);
        }
        if (groupIdNode != null) {
            if (dependencyContentIndentation == null) {
                dependencyContentIndentation = findNodeIndentation(groupIdNode);
                if (dependencyContentIndentation.length() > dependencyIndentation.length()) {
                    nestedIndentation = dependencyContentIndentation.substring(dependencyIndentation.length());
                }
            }
        }
    }

    Node findDesiredNode(NodeList nodeList, Node preferredParent, Node acceptableParent) {
        Node acceptableNode = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (preferredParent != null && nodeList.item(i).getParentNode() == preferredParent) {
                return nodeList.item(i);
            }
            if (nodeList.item(i).getParentNode() == acceptableParent) {
                acceptableNode = nodeList.item(i);
            }
        }
        return acceptableNode;
    }

    List<Node> findChildNodes(Node parentNode, short nodeType, String nodeName) {
        List<Node> nodes = new ArrayList<>();
        NodeList childNodeList = parentNode.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); i++) {
            Node childNodeCheck = childNodeList.item(i);
            if (childNodeCheck.getNodeType() == nodeType && childNodeCheck.getNodeName().equals(nodeName)) {
                nodes.add(childNodeCheck);
            }
        }
        return nodes;
    }

    Node findChildNode(Node parentNode, short nodeType, String nodeName) {
        List<Node> nodes = findChildNodes(parentNode, nodeType, nodeName);
        switch (nodes.size()) {
            case 0:
                return null;
            case 1:
                return nodes.get(0);
            default:
                throw new RuntimeException(
                        "Expected 0 or 1 node named: " + nodeName + " but found " + nodes.size());
        }
    }

    String findNodeIndentation(Node node) {
        Node indentationNode = node.getPreviousSibling();
        if (Node.TEXT_NODE == indentationNode.getNodeType() && indentationNode.getTextContent().isBlank()) {
            return indentationNode.getTextContent();
        }
        return null;
    }

    public void addExclusion(String parentGroupId, String parentArtifactId,
                             String exclusionGroupId, String exclusionArtifactId) {
        changed = true;
        List<Node> dependencyNodes = findChildNodes(dependenciesNode, Node.ELEMENT_NODE, DEPENDENCY_TAG);
        for (Node dependencyNode: dependencyNodes) {
            Node groupIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, GROUP_ID_TAG);
            Node artifactIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, ARTIFACT_ID_TAG);

            String exclusionsIndent = findNodeIndentation(groupIdNode);

            if (parentGroupId.equals(groupIdNode.getTextContent())
                    && parentArtifactId.equals(artifactIdNode.getTextContent())) {
                // either find or create an <exclusions> node
                Node exclusionsNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, EXCLUSIONS_TAG);
                if (exclusionsNode == null) {
                    exclusionsNode = document.createElement(EXCLUSIONS_TAG);
                    dependencyNode.appendChild(document.createTextNode(exclusionsIndent));
                    dependencyNode.appendChild(exclusionsNode);
                    dependencyNode.appendChild(document.createTextNode(dependencyIndentation));
                }

                // add a new <exclusion>
                Node newExclusion = document.createElement(EXCLUSION_TAG);

                newExclusion.appendChild(document.createTextNode(
                        exclusionsIndent + nestedIndentation + nestedIndentation));
                Node excludeGroupIdNode = document.createElement(GROUP_ID_TAG);
                excludeGroupIdNode.setTextContent(exclusionGroupId);
                newExclusion.appendChild(excludeGroupIdNode);

                newExclusion.appendChild(document.createTextNode(
                        dependencyContentIndentation + nestedIndentation + nestedIndentation));
                Node excludeArtifactIdNode = document.createElement(ARTIFACT_ID_TAG);
                excludeArtifactIdNode.setTextContent(exclusionArtifactId);
                newExclusion.appendChild(excludeArtifactIdNode);

                // add an indentation to the end of the last element so the closing element looks correct
                newExclusion.appendChild(document.createTextNode(dependencyContentIndentation + nestedIndentation));

                exclusionsNode.appendChild(document.createTextNode(dependencyContentIndentation + nestedIndentation));

                exclusionsNode.appendChild(newExclusion);

                exclusionsNode.appendChild(document.createTextNode(dependencyContentIndentation));
            }
        }
    }

    public void updateExplicitVersion(String groupId, String artifactId, Version version, String scope) {
        changed = true;
        List<Node> dependencyNodes = findChildNodes(dependenciesNode, Node.ELEMENT_NODE, DEPENDENCY_TAG);
        for (Node dependencyNode: dependencyNodes) {
            Node groupIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, GROUP_ID_TAG);
            Node artifactIdNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, ARTIFACT_ID_TAG);

            if (groupId.equals(groupIdNode.getTextContent())
                    && artifactId.equals(artifactIdNode.getTextContent())) {
                Node versionNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, VERSION_TAG);
                if (versionNode != null) {
                    // TODO: Handle version in properties
                    versionNode.setTextContent(version.toString());
                }
                // TODO: Handle missing version

                Node scopeNode = findChildNode(dependencyNode, Node.ELEMENT_NODE, SCOPE_TAG);
                if (scopeNode != null) {
                    scopeNode.setTextContent(scope);
                }
                // if scope doesn't exist, that's fine - default is "compile"
            }
        }

    }

    public void addForcedDependencyNode(String groupId, String artifactId, Version version, String scope) {
        changed = true;

        dependenciesNode.appendChild(document.createTextNode(dependencyIndentation));
        dependenciesNode.appendChild(document.createComment(COMMENT_ADD_DEPENDENCY_START));

        Node newDependency = document.createElement(DEPENDENCY_TAG);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node groupIdNode = document.createElement(GROUP_ID_TAG);
        groupIdNode.setTextContent(groupId);
        newDependency.appendChild(groupIdNode);

        newDependency.appendChild(document.createTextNode(dependencyContentIndentation));
        Node artifactNode = document.createElement(ARTIFACT_ID_TAG);
        artifactNode.setTextContent(artifactId);
        newDependency.appendChild(artifactNode);

        // TODO: Handle version in properties

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
        // only strip exclusions whose parent node is a dependency
        stripNodes(EXCLUSIONS_TAG, DEPENDENCY_TAG);
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
                            throw new RuntimeException("Invalid Forced Dependency Start comment tag");
                        }
                        // this will be deleted below
                        deleting = true;
                    } else if (COMMENT_ADD_DEPENDENCY_END.equals(node.getTextContent().trim())) {
                        if (!deleting) {
                            throw new RuntimeException("Invalid Forced Dependency End comment tag");
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
            throw new RuntimeException("Missing Forced Dependency End comment tag");
        }
    }

    protected void stripNodes(String nodeId, String validateParentNodeName) {
        NodeList exclusionNodes = document.getElementsByTagName(nodeId);
        for (int i = 0; i < exclusionNodes.getLength(); i++) {
            if (validateParentNodeName == null ||
                    exclusionNodes.item(i).getParentNode().getNodeName().equals(validateParentNodeName)) {
                deleteNode(exclusionNodes.item(i), true);
            }
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

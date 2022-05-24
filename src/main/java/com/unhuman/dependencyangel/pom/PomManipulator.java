package com.unhuman.dependencyangel.pom;

import com.unhuman.dependencyangel.DependencyAngelConfig;
import com.unhuman.dependencyangel.dependency.Dependency;
import com.unhuman.dependencyangel.versioning.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PomManipulator {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\r?\\n\\s+");
    private static final Pattern WHITESPACE_SINGLE_NEWLINE_PATTERN = Pattern.compile("(?:\\r?\\n)*(\\r?\\n\\s+)");
    public static final Pattern PROPERTIES_VERSION = Pattern.compile("\\$\\{(.*)\\}");
    private static final String COMMENT_DEPENDENCY_ANGEL_START = "DependencyAngel Start";
    private static final String COMMENT_DEPENDENCY_ANGEL_END = "DependencyAngel End";
    public static final String PROPERTIES_TAG = "properties";
    public static final String DEPENDENCY_MANAGEMENT_TAG = "dependencyManagement";
    public static final String DEPENDENCIES_TAG = "dependencies";
    public static final String DEPENDENCY_TAG = "dependency";
    public static final String GROUP_ID_TAG = "groupId";
    public static final String ARTIFACT_ID_TAG = "artifactId";
    public static final String CLASSIFIER_TAG = "classifier";
    public static final String SCOPE_TAG = "scope";
    public static final String TYPE_TAG = "type";
    public static final String VERSION_TAG = "version";
    public static final String EXCLUSIONS_TAG = "exclusions";
    public static final String EXCLUSION_TAG = "exclusion";

    private String filename;
    private Document document;
    private Node dependencyManagementNode;
    private Node dependenciesNode;
    private Node propertiesNode;

    private boolean dirty;

    private String dependenciesIndentation;
    private String dependencyIndentation;
    private String nestedIndentation = "    ";
    private String dependencyContentIndentation;

    public PomManipulator(String filename) {
        try {
            this.filename = filename;
            dirty = false;

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
            dependencyManagementNode = findSingleElement(DEPENDENCY_MANAGEMENT_TAG, false);
            dependenciesNode = findDesiredNode(document.getElementsByTagName(DEPENDENCIES_TAG),
                    dependencyManagementNode, projectNode);

            if (dependenciesNode != null) {
                dependenciesIndentation = findNodeIndentation(dependenciesNode);

                // Find indentations we need to use for child nodes
                List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
                Node groupIdNode = null;
                if (dependencyNodes.size() > 0) {
                    Node dependencyNode = dependencyNodes.get(0);
                    if (dependencyIndentation == null) {
                        dependencyIndentation = findNodeIndentation(dependencyNode);
                    }
                    groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
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
        } catch (Exception e) {
            throw new RuntimeException("Problem processing pom file: " + filename, e);
        }
    }

    public boolean hasDependencyManagement() {
        return (dependencyManagementNode != null);
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

    public List<Node> findChildElements(Node parentNode, String nodeName) {
        List<Node> nodes = new ArrayList<>();
        NodeList childNodeList = parentNode.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); i++) {
            Node childNodeCheck = childNodeList.item(i);
            if (childNodeCheck.getNodeType() == Node.ELEMENT_NODE && childNodeCheck.getNodeName().equals(nodeName)) {
                nodes.add(childNodeCheck);
            }
        }
        return nodes;
    }

    Node findChildElement(Node parentNode, String nodeName) {
        List<Node> nodes = findChildElements(parentNode, nodeName);
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

    public Node findSingleElement(String elementName, boolean required) {
        NodeList nodeList = document.getElementsByTagName(elementName);
        switch (nodeList.getLength()) {
            case 0:
                if (required) {
                    throw new RuntimeException("Could not find expected element: " + elementName);
                }
                return null;
            case 1:
                return nodeList.item(0);
            default:
                throw new RuntimeException(String.format("Found too many (%d) elements: %s",
                        nodeList.getLength(), elementName));
        }
    }

    public Node getSingleNodeElement(Node parentNode, String itemDesired, boolean required) {
        List<Node> foundNodes = findChildElements(parentNode, itemDesired);
        if (foundNodes.size() == 0) {
            if (required) {
                throw new RuntimeException("Could not find expected required element: " + itemDesired);
            }
            return null;
        }
        if (foundNodes.size() > 1) {
            throw new RuntimeException(
                    String.format("Found too many (%d) element: %s", foundNodes.size(), itemDesired));
        }

        return foundNodes.get(0);
    }

    String findNodeIndentation(Node node) {
        Node indentationNode = node.getPreviousSibling();
        if (Node.TEXT_NODE == indentationNode.getNodeType() && indentationNode.getTextContent().isBlank()) {
            String indentation = indentationNode.getTextContent();
            Matcher indendationMatcher = WHITESPACE_SINGLE_NEWLINE_PATTERN.matcher(indentation);
            if (indendationMatcher.matches()) {
                indentation = indendationMatcher.group(1);
            }
            return indentation;
        }
        return null;
    }

    public void addExclusion(String parentGroupId, String parentArtifactId,
                             String exclusionGroupId, String exclusionArtifactId) {
        dirty = true;

        List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
        for (Node dependencyNode: dependencyNodes) {
            Node groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
            Node artifactIdNode = findChildElement(dependencyNode, ARTIFACT_ID_TAG);

            String exclusionsIndent = findNodeIndentation(groupIdNode);

            if (parentGroupId.equals(groupIdNode.getTextContent())
                    && parentArtifactId.equals(artifactIdNode.getTextContent())) {
                // either find or create an <exclusions> node
                Node exclusionsNode = findChildElement(dependencyNode, EXCLUSIONS_TAG);
                if (exclusionsNode == null) {
                    exclusionsNode = document.createElement(EXCLUSIONS_TAG);

                    addLastChild(dependencyNode, document.createTextNode(exclusionsIndent), dependencyIndentation);
                    addLastChild(dependencyNode, exclusionsNode, dependencyIndentation);
                }

                // add a new <exclusion>
                String exclusionIndent = exclusionsIndent + nestedIndentation;
                String exclusionContentIndent = exclusionIndent + nestedIndentation;
                Node newExclusion = document.createElement(EXCLUSION_TAG);

                addLastChild(newExclusion, document.createTextNode(exclusionContentIndent), exclusionIndent);
                Node excludeGroupIdNode = document.createElement(GROUP_ID_TAG);
                excludeGroupIdNode.setTextContent(exclusionGroupId);
                addLastChild(newExclusion, excludeGroupIdNode, exclusionIndent);

                addLastChild(newExclusion, document.createTextNode(exclusionContentIndent), exclusionIndent);
                Node excludeArtifactIdNode = document.createElement(ARTIFACT_ID_TAG);
                excludeArtifactIdNode.setTextContent(exclusionArtifactId);
                addLastChild(newExclusion, excludeArtifactIdNode, exclusionIndent);

                addLastChild(exclusionsNode, document.createTextNode(exclusionIndent), dependencyContentIndentation);
                addLastChild(exclusionsNode, newExclusion, dependencyContentIndentation);
            }
        }
    }

    protected void addLastChild(Node parentNode, Node addNode, String parentNodeIndentation) {
        dirty = true;

        Node lastChild = parentNode.getLastChild();
        Node appendPoint = (lastChild != null
                && lastChild.getNodeType() == Node.TEXT_NODE
                && lastChild.getTextContent().isBlank()
                && lastChild.getPreviousSibling() != null)
                ? lastChild.getPreviousSibling() : null;
        if (appendPoint != null) {
            lastChild = parentNode.removeChild(lastChild);
            parentNode.appendChild(addNode);
            parentNode.appendChild(lastChild);
        } else {
            parentNode.appendChild(addNode);
            parentNode.appendChild(document.createTextNode(parentNodeIndentation));
        }
    }

    /**
     *
     * @param dependency
     * @return
     */
    public boolean updateExplicitVersion(Dependency dependency) {
        return updateExplicitVersion(dependency.getGroup(), dependency.getArtifact(), dependency.getType(),
                dependency.getVersion(), dependency.getScope(), dependency.getClassifier(),
                dependency.getExclusions());
    }

    /**
     * Updates an existing node
     * @param groupId
     * @param artifactId
     * @param type
     * @param version
     * @param scope
     * @param classifier
     * @param exclusions
     * @return true if an existing node was found (not necessarily updated)
     */
    public boolean updateExplicitVersion(String groupId, String artifactId, String type,
                                         Version version, String scope, String classifier,
                                         List<Dependency> exclusions) {
        List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);

        // Don't allow a value of a version to be a lookup (probably of itself)
        boolean skipVersion = (version != null) ? PROPERTIES_VERSION.matcher(version.toString()).matches() : true;

        boolean foundExistingNode = false;
        for (Node dependencyNode: dependencyNodes) {
            Node groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
            Node artifactIdNode = findChildElement(dependencyNode, ARTIFACT_ID_TAG);

            // TODO: When we overwrite a version explicitly, let's choose latest version

            if (groupId.equals(groupIdNode.getTextContent())
                    && artifactId.equals(artifactIdNode.getTextContent())) {
                foundExistingNode = true;
                if (!skipVersion) {
                    Node versionNode = findChildElement(dependencyNode, VERSION_TAG);
                    if (versionNode != null) {
                        String priorVersion = versionNode.getTextContent();
                        Matcher matcher = PROPERTIES_VERSION.matcher(priorVersion);
                        if (matcher.matches()) {
                            String key = matcher.group(1);
                            NodeList versionElements = document.getElementsByTagName(key);
                            if (versionElements.getLength() == 1) {
                                versionElements.item(0).setTextContent(version.toString());
                            } else {
                                throw new RuntimeException("Couldn't find property: " + key);
                            }
                        } else {
                            versionNode.setTextContent(version.toString());
                        }
                    }
                }

                // TODO: Handle missing version - shouldn't be an issue

                {
                    Node typeNode = findChildElement(dependencyNode, TYPE_TAG);
                    if (typeNode != null) {
                        if (type != null) {
                            typeNode.setTextContent(type);
                        } else {
                            // delete the scope
                            deleteNode(typeNode, true);
                        }
                    }
                }
                {
                    Node scopeNode = findChildElement(dependencyNode, SCOPE_TAG);
                    if (scopeNode != null) {
                        if (scope != null) {
                            scopeNode.setTextContent(scope);
                        } else {
                            // delete the scope
                            deleteNode(scopeNode, true);
                        }
                    }
                }
                {
                    Node classifierNode = findChildElement(dependencyNode, CLASSIFIER_TAG);
                    if (classifierNode != null) {
                        if (classifier != null) {
                            classifierNode.setTextContent(classifier);
                        } else {
                            // delete the classifier
                            deleteNode(classifierNode, true);
                        }
                    }
                }

                // Add / Update exclusions!!!
                dependencyIndentation = findNodeIndentation(dependencyNode);
                ensureExclusions(dependencyNode, dependencyIndentation, exclusions);
            }
        }
        dirty = (foundExistingNode) ? foundExistingNode : dirty;

        return foundExistingNode;
    }

    public void addDependencyNode(Dependency dependency) {
        addDependencyNode(dependency.getGroup(), dependency.getArtifact(), dependency.getType(),
                dependency.getVersion(), dependency.getScope(), dependency.getClassifier(), dependency.getExclusions());
    }

    public void addDependencyNode(String groupId, String artifactId, String type, Version version, String scope,
                                  String classifier, List<Dependency> exclusions) {
        dirty = true;
        addDependencyNode(groupId, artifactId, type, version, scope, classifier, exclusions, false);
    }

    public void addForcedDependencyNode(Dependency dependency) {
        addForcedDependencyNode(dependency.getGroup(), dependency.getArtifact(), dependency.getType(),
                dependency.getVersion(), dependency.getScope(), dependency.getClassifier(), dependency.getExclusions());
    }

    public void addForcedDependencyNode(String groupId, String artifactId, String type, Version version, String scope,
                                        String classifier, List<Dependency> exclusions) {
        dirty = true;

        addLastChild(dependenciesNode, document.createTextNode(dependencyIndentation),
                dependencyIndentation);
        addLastChild(dependenciesNode, document.createComment(COMMENT_DEPENDENCY_ANGEL_START),
                dependencyIndentation);

        addDependencyNode(groupId, artifactId, type, version, scope, classifier, exclusions, true);

        addLastChild(dependenciesNode, document.createTextNode(dependencyIndentation), dependenciesIndentation);
        addLastChild(dependenciesNode, document.createComment(COMMENT_DEPENDENCY_ANGEL_END), dependenciesIndentation);
    }

    private void addDependencyNode(String groupId, String artifactId, String type, Version version,
                                   String scope, String classifier, List<Dependency> exclusions,
                                   boolean needAngelComment) {
        dirty = true;

        Node newDependency = createDependencyNode(groupId, artifactId, type, version, scope, classifier,
                dependencyIndentation, exclusions, needAngelComment);
        addLastChild(dependenciesNode, document.createTextNode(dependencyIndentation), dependenciesIndentation);
        addLastChild(dependenciesNode, newDependency, dependenciesIndentation);
    }

    private Node createDependencyNode(String groupId, String artifactId, String type, Version version,
                                      String scope, String classifier, String indentation, List<Dependency> exclusions,
                                      boolean needAngelComment) {
        Node newDependency = document.createElement(DEPENDENCY_TAG);

        String contentIndentation = indentation + nestedIndentation;

        newDependency.appendChild(document.createTextNode(contentIndentation));
        Node groupIdNode = document.createElement(GROUP_ID_TAG);
        groupIdNode.setTextContent(groupId);
        newDependency.appendChild(groupIdNode);

        newDependency.appendChild(document.createTextNode(contentIndentation));
        Node artifactNode = document.createElement(ARTIFACT_ID_TAG);
        artifactNode.setTextContent(artifactId);
        newDependency.appendChild(artifactNode);

        if (type != null && !type.isBlank()) {
            newDependency.appendChild(document.createTextNode(contentIndentation));
            Node typeNode = document.createElement(TYPE_TAG);
            typeNode.setTextContent(type);
            newDependency.appendChild(typeNode);
        }

        if (version != null) {
            newDependency.appendChild(document.createTextNode(contentIndentation));
            Node versionNode = document.createElement(VERSION_TAG);
            String versionInfo = storeVersionInProperties(groupId, artifactId, version.toString(), needAngelComment);
            versionNode.setTextContent(versionInfo);
            newDependency.appendChild(versionNode);
        }

        if (scope != null && !scope.isBlank()) {
            newDependency.appendChild(document.createTextNode(contentIndentation));
            Node scopeNode = document.createElement(SCOPE_TAG);
            scopeNode.setTextContent(scope);
            newDependency.appendChild(scopeNode);
        }

        if (classifier != null && !classifier.isBlank()) {
            newDependency.appendChild(document.createTextNode(contentIndentation));
            Node classifierNode = document.createElement(CLASSIFIER_TAG);
            classifierNode.setTextContent(classifier);
            newDependency.appendChild(classifierNode);
        }

        // Add exclusions
        ensureExclusions(newDependency, indentation, exclusions);

        // add an indentation to the end of the last element so the closing element looks correct
        newDependency.appendChild(document.createTextNode(indentation));
        return newDependency;
    }

    private void ensureExclusions(Node dependencyNode, String parentIndentation, List<Dependency> exclusions) {
        if (exclusions == null || exclusions.size() == 0) {
            return;
        }

        String exclusionsIndentation = parentIndentation + nestedIndentation;

        boolean addedExclusionsNode = false;
        Node exclusionsNode = findChildElement(dependencyNode, EXCLUSIONS_TAG);
        if (exclusionsNode == null) {
            dependencyNode.appendChild(document.createTextNode(exclusionsIndentation));
            exclusionsNode = document.createElement(EXCLUSIONS_TAG);
            dependencyNode.appendChild(exclusionsNode);
            addedExclusionsNode = true;
        }

        for (Dependency exclusion : exclusions) {
            boolean foundExclusion = false;
            List<Node> exclusionNodes = findChildElements(exclusionsNode, EXCLUSION_TAG);
            for (Node existingExclusionNode: exclusionNodes) {
                Node groupIdNode = findChildElement(existingExclusionNode, GROUP_ID_TAG);
                Node artifactIdNode = findChildElement(existingExclusionNode, ARTIFACT_ID_TAG);

                if (exclusion.getGroup().equals(groupIdNode.getTextContent())
                        && exclusion.getArtifact().equals(artifactIdNode.getTextContent())) {
                    foundExclusion = true;
                    break;
                }
            }

            if (!foundExclusion) {
                String exclusionIndentation = exclusionsIndentation + nestedIndentation;

                exclusionsNode.appendChild(document.createTextNode(exclusionIndentation));
                Node newExclusionNode = document.createElement(EXCLUSION_TAG);
                exclusionsNode.appendChild(newExclusionNode);

                String dataIndentation = exclusionIndentation + nestedIndentation;
                newExclusionNode.appendChild(document.createTextNode(dataIndentation));
                Node newGroupIdNode = document.createElement(GROUP_ID_TAG);
                newGroupIdNode.setTextContent(exclusion.getGroup());
                newExclusionNode.appendChild(newGroupIdNode);
                newExclusionNode.appendChild(document.createTextNode(dataIndentation));
                Node newArtifactIdNode = document.createElement(ARTIFACT_ID_TAG);
                newArtifactIdNode.setTextContent(exclusion.getArtifact());
                newExclusionNode.appendChild(newArtifactIdNode);

                // white space before the closing exclusion tag
                newExclusionNode.appendChild(document.createTextNode(exclusionIndentation));
            }
        }

        // white space before the closing exclusion tag
        if (addedExclusionsNode) {
            exclusionsNode.appendChild(document.createTextNode(exclusionsIndentation));
            dependencyNode.appendChild(document.createTextNode(parentIndentation));
        }
    }

    public Node getDependenciesNode() {
        return dependenciesNode;
    }

    public boolean hasDependency(String groupId, String artifactId) {
        if (dependenciesNode != null) {
            List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
            for (Node dependencyNode: dependencyNodes) {
                Node groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
                Node artifactIdNode = findChildElement(dependencyNode, ARTIFACT_ID_TAG);
                if (groupIdNode != null && artifactIdNode != null
                        && groupId.equals(groupIdNode.getTextContent().trim())
                        && artifactId.equals(artifactIdNode.getTextContent().trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stripExclusions(DependencyAngelConfig config) {
        // preserved exclusions and banned dependencies are both treated the same (skip existing exclusions)
        // TODO: this is duplicated in DependencyAngel
        Set<String> preserveExclusions = new HashSet<>(
                config.getBannedDependencies().size() + config.getPreserveExclusions().size());
        preserveExclusions.addAll(config.getBannedDependencies());
        preserveExclusions.addAll(config.getPreserveExclusions());

        // only strip exclusions whose parent node is a dependency
        List<Node> exclusionsNodes = convertNodeListToList(document.getElementsByTagName(EXCLUSIONS_TAG));

        for (Node exclusionsNode: exclusionsNodes) {
            boolean deleteExclusionsNode = true;

            if (exclusionsNode.getParentNode().getNodeName().equals(DEPENDENCY_TAG)) {
                List<Node> exclusionNodes = findChildElements(exclusionsNode, EXCLUSION_TAG);
                for (Node exclusionNode: exclusionNodes) {
                    String groupId = getSingleNodeElement(exclusionNode, GROUP_ID_TAG, true)
                            .getTextContent();
                    String artifactId = getSingleNodeElement(exclusionNode, ARTIFACT_ID_TAG, true)
                            .getTextContent();

                    if (!preserveExclusions.contains(String.format("%s:%s", groupId, artifactId))) {
                        deleteNode(exclusionNode, true);
                    } else {
                        deleteExclusionsNode = false;
                    }
                }
            }

            // Only delete the exclusions node if we deleted all the nested exclusions within
            if (deleteExclusionsNode) {
                deleteNode(exclusionsNode, true);
            }
        }
    }

    public void stripDependencyAngelDependencies() {
        stripDependencyAngelDependencies(propertiesNode);
        stripDependencyAngelDependencies(dependenciesNode);
    }
    protected void stripDependencyAngelDependencies(Node parentNode) {
        // ensure we have something to do
        if (parentNode == null) {
            return;
        }

        NodeList dependencies = parentNode.getChildNodes();
        boolean deleting = false;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);

            while (node != null) {
                if (Node.COMMENT_NODE == node.getNodeType()) {
                    if (COMMENT_DEPENDENCY_ANGEL_START.equals(node.getTextContent().trim())) {
                        if (deleting) {
                            throw new RuntimeException("Invalid Forced Dependency Start comment tag");
                        }
                        // this will be deleted below
                        deleting = true;
                    } else if (COMMENT_DEPENDENCY_ANGEL_END.equals(node.getTextContent().trim())) {
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

    public void deleteNode(Node deleteNode, boolean cleanPriorWhitespace) {
        if (deleteNode == null) {
            return;
        }

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

        dirty = true;
    }

    /**
     * Stores the version in properties and returns an id - or the version
     * @param version
     */
    protected String storeVersionInProperties(String groupId, String artifactId, String version,
                                              boolean needAngelComment) {
        if (propertiesNode == null) {
            return version;
        }

        // If the value we are receiving here is a property, then we will assume it's already defined
        if (PROPERTIES_VERSION.matcher(version).matches()) {
            return version;
        }

        dirty = true;

        String propertiesIndent = findNodeIndentation(propertiesNode);
        String versionIndent = propertiesIndent + nestedIndentation;
        String key = String.format("%s-%s.version", groupId, artifactId);

        // Store value in properties
        NodeList versionElements = document.getElementsByTagName(key);
        if (versionElements.getLength() == 1) {
            versionElements.item(0).setTextContent(version);
        } else {
            Node versionProperty = document.createElement(key);
            versionProperty.setTextContent(version);
            if (needAngelComment) {
                addLastChild(propertiesNode, document.createTextNode(versionIndent), propertiesIndent);
                addLastChild(propertiesNode, document.createComment(COMMENT_DEPENDENCY_ANGEL_START),
                        propertiesIndent);
            }
            addLastChild(propertiesNode, document.createTextNode(versionIndent), propertiesIndent);
            addLastChild(propertiesNode, versionProperty, propertiesIndent);
            if (needAngelComment) {
                addLastChild(propertiesNode, document.createTextNode(versionIndent), propertiesIndent);
                addLastChild(propertiesNode, document.createComment(COMMENT_DEPENDENCY_ANGEL_END),
                        propertiesIndent);
            }
        }
        return String.format("${%s}", key);
    }

    /**
     * Convert a NodeList to a List<Node>
     * The reason why you would want to do this is that NodeList changes when you delete items from the document.
     * @param nodeList
     * @return
     */
    private List<Node> convertNodeListToList(NodeList nodeList) {
        List<Node> nodes = new ArrayList<>(nodeList.getLength());
        for (int i = 0; i < nodeList.getLength(); i++) {
            nodes.add(nodeList.item(i));
        }
        return nodes;
    }

    public boolean saveFile() {
        // only save if something changed
        if (!dirty) {
            return false;
        }

        try {
            FileOutputStream output = new FileOutputStream(filename);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(output);

            transformer.transform(source, result);

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Problem saving: " + filename, e);
        }
    }
}

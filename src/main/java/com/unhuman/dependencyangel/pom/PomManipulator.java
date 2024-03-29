package com.unhuman.dependencyangel.pom;

import com.unhuman.dependencyangel.DependencyAngelConfig;
import com.unhuman.dependencyangel.StorableAngelConfigData;
import com.unhuman.dependencyangel.dependency.ArtifactHelper;
import com.unhuman.dependencyangel.dependency.Dependency;
import com.unhuman.dependencyangel.versioning.Version;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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
    public static final Pattern PROPERTIES_VARIABLE = Pattern.compile("\\$\\{(.*)\\}");
    private static final String ANGEL_TRACKING_ATTRIBUTE = "angel:tracking";
    private static final String ANGEL_MANAGED_VALUE = "managed";
    public static final String ANGEL_PRESERVE_VALUE = "preserve";
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
    public static final String PARENT_TAG = "parent";

    private String filename;
    private Document document;
    private Node dependencyManagementNode;
    private Node dependenciesNode;
    private Node propertiesNode;

    private boolean dirty;

    // Keep track of this nodes group + artifact
    private static final HashSet<String> knownArtifacts = new HashSet<>();
    private String groupId;
    private String artifactId;

    public PomManipulator(String filename) {
        try {
            this.filename = filename;
            dirty = false;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new File(filename));

            // Ensure we have a namespace for our attributes we use to track explicit angel content
            document.getDocumentElement().setAttributeNS("http://www.w3.org/2000/xmlns/",
                    "xmlns:angel", "http://unhuman.com/angel");

            document.getDocumentElement().normalize();

            Node projectNode = document.getFirstChild();
            if (!projectNode.getNodeName().equals("project")) {
                throw new RuntimeException("Could not find project node");
            }

            // Get the groupId (or leverage groupId from parent)
            groupId = getSingleNodeElementText(projectNode, GROUP_ID_TAG, false);
            if (groupId == null) {
                // get the groupId out of the parent
                Node parentNode = getSingleNodeElement(projectNode, PARENT_TAG, false);
                if (parentNode != null) {
                    groupId = getSingleNodeElementText(parentNode, GROUP_ID_TAG, false);
                }
            }
            artifactId = getSingleNodeElementText(projectNode, ARTIFACT_ID_TAG, true);
            String groupIdArtifactId = ArtifactHelper.getArtifactIdGroupIdString(groupId, artifactId);
            if (!knownArtifacts.contains(groupIdArtifactId)) {
                knownArtifacts.add(groupIdArtifactId);
            }

            // determine verious nodes
            propertiesNode = findDesiredNode(document.getElementsByTagName(PROPERTIES_TAG), projectNode, projectNode);
            dependencyManagementNode = findSingleElement(DEPENDENCY_MANAGEMENT_TAG, false);
            dependenciesNode = findDesiredNode(document.getElementsByTagName(DEPENDENCIES_TAG),
                    dependencyManagementNode, projectNode);

            if (dependenciesNode != null) {
                // Find indentations we need to use for child nodes
                List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
                Node groupIdNode = null;
                if (dependencyNodes.size() > 0) {
                    Node dependencyNode = dependencyNodes.get(0);
                    groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Problem processing pom file: " + filename, e);
        }
    }

    public static boolean isKnownArtifact(String groupId, String artifactId) {
        String artifact = ArtifactHelper.getArtifactIdGroupIdString(groupId, artifactId);
        return knownArtifacts.contains(artifact);
    }

    /**
     * Ensure there is a dependencyManagement section in the pom.xml
     */
    public void ensureDependencyManagement() {
        // We require dependencyManagement section be created
        if (!hasDependencyManagement()) {
            // Create a dependencyManagementNode
            dependencyManagementNode = document.createElement(DEPENDENCY_MANAGEMENT_TAG);

            // insert it where we can
            if (dependenciesNode != null) {
                dependenciesNode.getParentNode().insertBefore(dependencyManagementNode, dependenciesNode);
            } else {
                document.getFirstChild().appendChild(dependencyManagementNode);
            }
        }

        // Ensure dependencyManagement has dependencies
        if (findChildElement(dependencyManagementNode, DEPENDENCIES_TAG) == null) {
            dependenciesNode = document.createElement(DEPENDENCIES_TAG);
            addLastChild(dependencyManagementNode, dependenciesNode);
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

    public String getSingleNodeElementText(Node parentNode, String itemDesired, boolean required) {
        Node foundNode = getSingleNodeElement(parentNode, itemDesired, required);
        if (foundNode == null) {
            return null;
        }

        String text = foundNode.getTextContent().trim();
        if (required && text.length() == 0) {
            throw new RuntimeException("Expected text for element: " + itemDesired);
        }

        return text;
    }

    public boolean addExclusion(String parentGroupId, String parentArtifactId,
                             String exclusionGroupId, String exclusionArtifactId) {
        boolean changed = false;

        List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
        for (Node dependencyNode: dependencyNodes) {
            Node groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
            Node artifactIdNode = findChildElement(dependencyNode, ARTIFACT_ID_TAG);

            if (parentGroupId.equals(groupIdNode.getTextContent())
                    && parentArtifactId.equals(artifactIdNode.getTextContent())) {
                changed = true;
                // either find or create an <exclusions> node
                Node exclusionsNode = findChildElement(dependencyNode, EXCLUSIONS_TAG);
                if (exclusionsNode == null) {
                    exclusionsNode = document.createElement(EXCLUSIONS_TAG);
                    addLastChild(dependencyNode, exclusionsNode);
                }

                // add a new <exclusion>
                Node newExclusion = document.createElement(EXCLUSION_TAG);

                Node excludeGroupIdNode = document.createElement(GROUP_ID_TAG);
                excludeGroupIdNode.setTextContent(exclusionGroupId);
                addLastChild(newExclusion, excludeGroupIdNode);

                Node excludeArtifactIdNode = document.createElement(ARTIFACT_ID_TAG);
                excludeArtifactIdNode.setTextContent(exclusionArtifactId);
                addLastChild(newExclusion, excludeArtifactIdNode);

                addLastChild(exclusionsNode, newExclusion);
            }
        }
        return changed;
    }

    protected void addLastChild(Node parentNode, Node addNode) {
        setDirty();

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
        }
    }

    /**
     *
     * @param dependency
     * @return
     */
    public boolean updateExplicitVersion(Dependency dependency) {
        return updateExplicitVersion(dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(),
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
        boolean skipVersion = (version != null) ? PROPERTIES_VARIABLE.matcher(version.toString()).matches() : true;

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
                        Matcher matcher = PROPERTIES_VARIABLE.matcher(priorVersion);
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
                ensureExclusions(dependencyNode, exclusions);
            }
        }
        dirty = (foundExistingNode) ? foundExistingNode : dirty;

        return foundExistingNode;
    }

    public void addDependencyNode(Dependency dependency) {
        addDependencyNode(dependency.getGroupId(), dependency.getArtifactId(), dependency.getType(),
                dependency.getVersion(), dependency.getScope(), dependency.getClassifier(), dependency.getExclusions());
    }

    public void addDependencyNode(String groupId, String artifactId, String type, Version version, String scope,
                                  String classifier, List<Dependency> exclusions) {
        addDependencyNode(groupId, artifactId, type, version, scope, classifier, exclusions, false);
    }

    public void forceVersionDependencyNode(String groupId, String artifactId, String type, Version version,
                                           String scope, String classifier, List<Dependency> exclusions) {
        // See if we can find a pre-existing node that has this - without a version specified
        // If so, just update that (and wrap with DA tags).
        List<Node> childDependencies = findChildElements(dependenciesNode, DEPENDENCY_TAG);
        for (Node childDependency: childDependencies) {
            String checkGroupId = getSingleNodeElementText(childDependency, GROUP_ID_TAG, true);
            String checkArtifactId = getSingleNodeElementText(childDependency, ARTIFACT_ID_TAG, true);
            if (checkGroupId.equals(groupId) && checkArtifactId.equals(artifactId)) {
                String checkVersionId = getSingleNodeElementText(childDependency, VERSION_TAG, false);
                String versionInfo = storeVersionInProperties(groupId, artifactId, version.toString(), true);

                if (checkVersionId == null) {
                    // Update only the version in an existing item
                    Node versionNode = document.createElement(VERSION_TAG);
                    // add an attribute for angel tracking
                    addDependencyAngelTrackingAnnotation(true, versionNode);
                    versionNode.setTextContent(versionInfo);
                    addLastChild(childDependency, versionNode);
                    return;
                } else if (checkVersionId.equals(versionInfo)) {
                    // Prevent duplicate adds of this item
                    return;
                } else {
                    System.err.println(
                            String.format("You may wind up with duplicate entries of %s:%s", groupId, version));
                }
            }
        }

        // default behavior - create a dependency node
        addDependencyNode(groupId, artifactId, type, version, scope, classifier, exclusions, true);
    }

    private void addDependencyNode(String groupId, String artifactId, String type, Version version,
                                   String scope, String classifier, List<Dependency> exclusions,
                                   boolean needAngelTracking) {
        Node newDependency = createDependencyNode(groupId, artifactId, type, version, scope, classifier,
                exclusions, needAngelTracking);
        addLastChild(dependenciesNode, newDependency);
    }

    private Node createDependencyNode(String groupId, String artifactId, String type, Version version,
                                      String scope, String classifier, List<Dependency> exclusions,
                                      boolean needAngelTracking) {
        setDirty();

        Node newDependency = document.createElement(DEPENDENCY_TAG);

        Node groupIdNode = document.createElement(GROUP_ID_TAG);
        groupIdNode.setTextContent(groupId);
        newDependency.appendChild(groupIdNode);

        Node artifactNode = document.createElement(ARTIFACT_ID_TAG);
        artifactNode.setTextContent(artifactId);
        newDependency.appendChild(artifactNode);

        if (type != null && !type.isBlank()) {
            Node typeNode = document.createElement(TYPE_TAG);
            typeNode.setTextContent(type);
            newDependency.appendChild(typeNode);
        }

        if (version != null) {
            Node versionNode = document.createElement(VERSION_TAG);
            String versionInfo = storeVersionInProperties(groupId, artifactId, version.toString(), needAngelTracking);
            versionNode.setTextContent(versionInfo);
            newDependency.appendChild(versionNode);
        }

        if (scope != null && !scope.isBlank()) {
            Node scopeNode = document.createElement(SCOPE_TAG);
            scopeNode.setTextContent(scope);
            newDependency.appendChild(scopeNode);
        }

        if (classifier != null && !classifier.isBlank()) {
            Node classifierNode = document.createElement(CLASSIFIER_TAG);
            classifierNode.setTextContent(classifier);
            newDependency.appendChild(classifierNode);
        }

        // Add exclusions
        ensureExclusions(newDependency, exclusions);

        // add an attribute for angel tracking (if necessary)
        addDependencyAngelTrackingAnnotation(needAngelTracking, newDependency);

        return newDependency;
    }

    private void ensureExclusions(Node dependencyNode, List<Dependency> exclusions) {
        if (exclusions == null || exclusions.size() == 0) {
            return;
        }

        boolean addedExclusionsNode = false;
        Node exclusionsNode = findChildElement(dependencyNode, EXCLUSIONS_TAG);
        if (exclusionsNode == null) {
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

                if (exclusion.getGroupId().equals(groupIdNode.getTextContent())
                        && exclusion.getArtifactId().equals(artifactIdNode.getTextContent())) {
                    foundExclusion = true;
                    break;
                }
            }

            if (!foundExclusion) {
                Node newExclusionNode = document.createElement(EXCLUSION_TAG);
                exclusionsNode.appendChild(newExclusionNode);

                Node newGroupIdNode = document.createElement(GROUP_ID_TAG);
                newGroupIdNode.setTextContent(exclusion.getGroupId());
                newExclusionNode.appendChild(newGroupIdNode);
                Node newArtifactIdNode = document.createElement(ARTIFACT_ID_TAG);
                newArtifactIdNode.setTextContent(exclusion.getArtifactId());
                newExclusionNode.appendChild(newArtifactIdNode);
            }
        }
    }

    public Node getDependenciesNode() {
        return dependenciesNode;
    }

    public Node findDependency(String groupId, String artifactId) {
        if (dependenciesNode != null) {
            List<Node> dependencyNodes = findChildElements(dependenciesNode, DEPENDENCY_TAG);
            for (Node dependencyNode: dependencyNodes) {
                Node groupIdNode = findChildElement(dependencyNode, GROUP_ID_TAG);
                Node artifactIdNode = findChildElement(dependencyNode, ARTIFACT_ID_TAG);
                if (groupIdNode != null && artifactIdNode != null
                        && groupId.equals(groupIdNode.getTextContent().trim())
                        && artifactId.equals(artifactIdNode.getTextContent().trim())) {
                    return dependencyNode;
                }
            }
        }
        return null;
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

    public void stripDependencyAngelDependencies(StorableAngelConfigData config) {
        stripDependencyAngelDependencies(config, propertiesNode);
        stripDependencyAngelDependencies(config, dependenciesNode);
    }

    protected void stripDependencyAngelDependencies(StorableAngelConfigData config, Node parentNode) {
        // ensure we have something to do
        if (parentNode == null) {
            return;
        }

        NodeList dependencies = parentNode.getChildNodes();
        boolean deleting = false;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);

            while (node != null) {
                // Track where we are and delete if necessary
                Node nextNode = node.getNextSibling();

                // if we find our attribute, we can just delete this node
                if (nodeHasAngelAttributeValue(node, ANGEL_MANAGED_VALUE)) {
                    deleteNode(node, true);
                    node = nextNode;
                    continue;
                }

                // legacy handling cleans up all data within comments
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
                } else if (deleting && Node.ELEMENT_NODE == node.getNodeType() // track deleting versions
                        && node.getNodeName().endsWith(".version")) {
                    Node checkVersionNode = node.getFirstChild();
                    if (Node.TEXT_NODE == checkVersionNode.getNodeType()) {
                        String version = checkVersionNode.getTextContent().trim();
                        if (!version.isBlank()) {
                            config.trackVersion(node.getNodeName(), version);
                        }
                    }
                }

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

    public static boolean nodeHasAngelAttributeValue(Node node, String desiredValue) {
        // if we find our attribute, we can just delete this node
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null && attributes.getNamedItem(ANGEL_TRACKING_ATTRIBUTE) != null) {
            return (attributes.getNamedItem(ANGEL_TRACKING_ATTRIBUTE).getTextContent()
                    .equals(desiredValue));
        }
        return false;
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

        setDirty();
    }

    /**
     * Stores the version in properties and returns an id - or the version
     * @param version
     */
    protected String storeVersionInProperties(String groupId, String artifactId, String version,
                                              boolean needAngelTracking) {
        if (propertiesNode == null) {
            return version;
        }

        // If the value we are receiving here is a property, then we will assume it's already defined
        if (PROPERTIES_VARIABLE.matcher(version).matches()) {
            return version;
        }

        setDirty();

        String key = String.format("%s-%s.version", groupId, artifactId);

        // Store value in properties
        NodeList versionElements = document.getElementsByTagName(key);
        if (versionElements.getLength() == 1) {
            versionElements.item(0).setTextContent(version);
        } else {
            Node versionProperty = document.createElement(key);
            versionProperty.setTextContent(version);
            // add an attribute for angel tracking (if necessary)
            addDependencyAngelTrackingAnnotation(needAngelTracking, versionProperty);
            addLastChild(propertiesNode, versionProperty);
        }
        return String.format("${%s}", key);
    }

    private void addDependencyAngelTrackingAnnotation(boolean isNeeded, Node node) {
        if (isNeeded) {
            // add an attribute for angel tracking
            Attr attribute = document.createAttribute(ANGEL_TRACKING_ATTRIBUTE);
            attribute.setTextContent(ANGEL_MANAGED_VALUE);
            node.getAttributes().setNamedItem(attribute);
        }
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

    public boolean saveFile(String noOperationPerformed, String successOperationPerformed) {
        // only save if something changed
        if (!dirty) {
            if (noOperationPerformed != null) {
                System.out.println(noOperationPerformed + ": " + filename);
            }
            return false;
        }

        // Generate list of all empty Nodes, them remove them
        try {
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList nl = (NodeList) xp.evaluate("//text()[normalize-space(.)='']", document, XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); ++i) { // note the position of the '++'
                Node node = nl.item(i);
                node.getParentNode().removeChild(node);
            }
        } catch (XPathExpressionException xpee) {
            // Do nothing
        }

        try {
            FileOutputStream output = new FileOutputStream(filename);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(output);
            transformer.transform(source, result);

            if (successOperationPerformed != null) {
                System.out.println(successOperationPerformed + ": " + filename);
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Problem saving: " + filename, e);
        }
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    private void setDirty() {
        dirty = true;
    }
}

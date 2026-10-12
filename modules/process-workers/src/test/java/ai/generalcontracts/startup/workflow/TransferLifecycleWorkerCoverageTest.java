package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.annotation.JobWorker;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class TransferLifecycleWorkerCoverageTest {
    @Test
    void everyBpmnServiceTaskHasARegisteredCamundaJobWorker() throws Exception {
        Document document = parse();
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new TransferLifecycleBpmnTestNamespaceContext());
        var nodes = (org.w3c.dom.NodeList) xpath.evaluate(
            "//bpmn:serviceTask/bpmn:extensionElements/zeebe:taskDefinition/@type",
            document,
            XPathConstants.NODESET
        );
        Set<String> bpmnTypes = new HashSet<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            bpmnTypes.add(nodes.item(index).getNodeValue());
        }

        Set<String> workerTypes = new HashSet<>();
        for (var method : TransferLifecycleCamundaWorkers.class.getDeclaredMethods()) {
            JobWorker worker = method.getAnnotation(JobWorker.class);
            if (worker != null) {
                workerTypes.add(worker.type());
            }
        }

        assertEquals(bpmnTypes, workerTypes);
    }

    @Test
    void timeoutAndMissingPolicyPathsPersistBeforeConvergingIntoScreeningJoin() throws Exception {
        Document document = parse();
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new TransferLifecycleBpmnTestNamespaceContext());

        for (String screening : new String[] { "kyc", "aml", "sanctions" }) {
            assertTrue((Boolean) xpath.evaluate(
                "count(//bpmn:sequenceFlow[@sourceRef='" + screening
                    + "Timeout' and @targetRef='screeningJoin']) = 0",
                document,
                XPathConstants.BOOLEAN
            ));
            assertTrue((Boolean) xpath.evaluate(
                "boolean(//bpmn:sequenceFlow[@sourceRef='" + screening + "TimeoutPersist' "
                    + "and @targetRef='screeningJoin'])",
                document,
                XPathConstants.BOOLEAN
            ));
            assertTrue((Boolean) xpath.evaluate(
                "boolean(//bpmn:sequenceFlow[@sourceRef='" + screening + "PolicyHold' "
                    + "and @targetRef='screeningJoin'])",
                document,
                XPathConstants.BOOLEAN
            ));
            assertTrue((Boolean) xpath.evaluate(
                "boolean(//bpmn:serviceTask[@id='" + screening + "TimeoutPersist']"
                    + "/bpmn:extensionElements/zeebe:taskDefinition"
                    + "[starts-with(@type, 'screening-timeout-')])",
                document,
                XPathConstants.BOOLEAN
            ));
        }
    }

    @Test
    void unspecifiedGatewayConditionsDefaultToNonExecutablePaths() throws Exception {
        Document document = parse();
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new TransferLifecycleBpmnTestNamespaceContext());
        for (String gateway : new String[] {
            "validationGateway", "complianceGateway", "executionGateway", "submissionGateway",
            "reconciliationGateway"
        }) {
            assertTrue((Boolean) xpath.evaluate(
                "boolean(//bpmn:exclusiveGateway[@id='" + gateway + "']/@default)",
                document,
                XPathConstants.BOOLEAN
            ), gateway);
        }
    }

    private Document parse() throws Exception {
        try (InputStream model = getClass().getResourceAsStream("/processes/transfer-lifecycle.bpmn")) {
            assertNotNull(model);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(model);
        }
    }

    private static final class TransferLifecycleBpmnTestNamespaceContext
        implements javax.xml.namespace.NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "bpmn" -> "http://www.omg.org/spec/BPMN/20100524/MODEL";
                case "zeebe" -> "http://camunda.org/schema/zeebe/1.0";
                default -> javax.xml.XMLConstants.NULL_NS_URI;
            };
        }

        @Override
        public String getPrefix(String namespaceUri) {
            return null;
        }

        @Override
        public java.util.Iterator<String> getPrefixes(String namespaceUri) {
            return java.util.Collections.emptyIterator();
        }
    }
}

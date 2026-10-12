package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class TransferLifecycleBpmnTest {
    @Test
    void definesFailClosedScreeningExecutionAndReconciliationPaths() throws Exception {
        Document document = parse();
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new BpmnNamespaceContext());

        assertNotNull(xpath.evaluate("//*[@id='transfer-lifecycle' and @isExecutable='true']", document,
            XPathConstants.NODE));
        assertEquals(3.0, (Double) xpath.evaluate(
            "count(//bpmn:message[contains(@name, 'ScreeningResult')])", document, XPathConstants.NUMBER
        ));
        assertEquals(3.0, (Double) xpath.evaluate(
            "count(//bpmn:intermediateCatchEvent[bpmn:timerEventDefinition])", document, XPathConstants.NUMBER
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:serviceTask[bpmn:extensionElements/zeebe:taskDefinition/@type='transfer-pre-execution-gate']",
            document, XPathConstants.NODE
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:serviceTask[bpmn:extensionElements/zeebe:taskDefinition/@type='transfer-submit']",
            document, XPathConstants.NODE
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:serviceTask[bpmn:extensionElements/zeebe:taskDefinition/@type='transfer-reconcile']",
            document, XPathConstants.NODE
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//zeebe:subscription[@correlationKey='=sanctionsCorrelationKey'])",
            document, XPathConstants.BOOLEAN
        ));
        assertEquals(3.0, (Double) xpath.evaluate(
            "count(//bpmn:sequenceFlow[@targetRef='screeningJoin' and "
                + "(@sourceRef='kycTimeoutPersist' or @sourceRef='amlTimeoutPersist' "
                + "or @sourceRef='sanctionsTimeoutPersist')])",
            document, XPathConstants.NUMBER
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:sequenceFlow[@sourceRef='reconciliationGateway' and "
                + "@targetRef='transactionResult' and "
                + "contains(bpmn:conditionExpression, 'reconciliationPending')]",
            document, XPathConstants.NODE
        ));
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

    private static final class BpmnNamespaceContext implements javax.xml.namespace.NamespaceContext {
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

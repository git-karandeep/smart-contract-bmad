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
        assertNotNull(xpath.evaluate(
            "//bpmn:serviceTask[@id='validateTransfer']"
                + "/following-sibling::bpmn:exclusiveGateway[@id='validationGateway']",
            document, XPathConstants.NODE
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:sequenceFlow[@sourceRef='validationGateway' and "
                + "@targetRef='validationRejected' and @id='flowInvalid']",
            document, XPathConstants.NODE
        ));
        assertEquals("=validationPassed = false", xpath.evaluate(
            "string(//bpmn:sequenceFlow[@id='flowInvalid']/bpmn:conditionExpression)",
            document, XPathConstants.STRING
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:sequenceFlow[@sourceRef='validationGateway' and "
                + "@targetRef='screeningFork' and @id='flowValid']",
            document, XPathConstants.NODE
        ));
        assertEquals("=validationPassed = true", xpath.evaluate(
            "string(//bpmn:sequenceFlow[@id='flowValid']/bpmn:conditionExpression)",
            document, XPathConstants.STRING
        ));
        assertNotNull(xpath.evaluate(
            "//bpmn:endEvent[@id='validationRejected']",
            document, XPathConstants.NODE
        ));
        assertEquals(0.0, (Double) xpath.evaluate(
            "count(//bpmn:sequenceFlow[@sourceRef='validationRejected'])",
            document, XPathConstants.NUMBER
        ));
        assertEquals(3.0, (Double) xpath.evaluate(
            "count(//bpmn:message[contains(@name, 'ScreeningResult')])", document, XPathConstants.NUMBER
        ));
        assertEquals("=instructionId", xpath.evaluate(
            "string(//bpmn:message[@name='TransferInstructionReceived']/bpmn:extensionElements/"
                + "zeebe:subscription/@correlationKey)",
            document, XPathConstants.STRING
        ));
        assertEquals("=instructionId", xpath.evaluate(
            "string(//bpmn:message[@name='TransferCancellationRequested']/bpmn:extensionElements/"
                + "zeebe:subscription/@correlationKey)",
            document, XPathConstants.STRING
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//bpmn:message[@name='KycScreeningResult']/bpmn:extensionElements/"
                + "zeebe:subscription[@correlationKey='=kycCorrelationKey'])",
            document, XPathConstants.BOOLEAN
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//bpmn:message[@name='AmlScreeningResult']/bpmn:extensionElements/"
                + "zeebe:subscription[@correlationKey='=amlCorrelationKey'])",
            document, XPathConstants.BOOLEAN
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//bpmn:message[@name='SanctionsScreeningResult']/bpmn:extensionElements/"
                + "zeebe:subscription[@correlationKey='=sanctionsCorrelationKey'])",
            document, XPathConstants.BOOLEAN
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//bpmn:subProcess[@id='transferCancellation' and @triggeredByEvent='true']"
                + "/bpmn:startEvent[@id='cancellationRequested' and @isInterrupting='false']"
                + "/bpmn:messageEventDefinition[@messageRef='transferCancellationMessage'])",
            document, XPathConstants.BOOLEAN
        ));
        assertTrue((Boolean) xpath.evaluate(
            "boolean(//bpmn:serviceTask[@id='cancelTransfer']"
                + "/bpmn:extensionElements/zeebe:taskDefinition[@type='transfer-cancel' and @retries='3'])",
            document, XPathConstants.BOOLEAN
        ));
        assertEquals(0.0, (Double) xpath.evaluate(
            "count(//bpmn:serviceTask/bpmn:extensionElements/zeebe:taskDefinition[not(@retries) "
                + "or number(@retries) < 1 or number(@retries) > 3])",
            document, XPathConstants.NUMBER
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
        assertEquals("flowReconciliationMatch", xpath.evaluate(
            "string(//bpmn:sequenceFlow[@targetRef='transferCompleted']/@id)",
            document, XPathConstants.STRING
        ));
        assertEquals(0.0, (Double) xpath.evaluate(
            "count(//bpmn:sequenceFlow[@sourceRef="
                + "//bpmn:endEvent[@id!='transferCompleted']/@id])",
            document, XPathConstants.NUMBER
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

package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

class BpmnWorkflowGeneratorTest {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DI = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String DC = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String ZEEBE = "http://camunda.org/schema/zeebe/1.0";

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesDeterministicParentCalledProcessesAndVersionedReport() throws Exception {
        Path source = copySource("transfer-lifecycle-source.bpmn");
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");

        new BpmnWorkflowGenerator().generate(source, firstOutput);
        new BpmnWorkflowGenerator().generate(source, secondOutput);

        List<String> artifacts = List.of(
            "transfer-lifecycle-source.bpmn",
            "kyc-screening-process.bpmn",
            "aml-screening-process.bpmn",
            "sanctions-screening-process.bpmn",
            "report.json"
        );
        for (String artifact : artifacts) {
            assertTrue(Files.exists(firstOutput.resolve(artifact)), artifact);
            assertEquals(
                Files.readString(firstOutput.resolve(artifact)),
                Files.readString(secondOutput.resolve(artifact)),
                artifact
            );
        }

        Document parent = parse(firstOutput.resolve("transfer-lifecycle-source.bpmn"));
        assertTrue(has(parent, "//bpmn:callActivity[@id='kycScreeningCall']"
            + "/bpmn:extensionElements/zeebe:calledElement[@processId='kyc-screening-process']"));
        assertTrue(has(parent, "//bpmn:callActivity[@id='amlScreeningCall']"
            + "/bpmn:extensionElements/zeebe:calledElement[@processId='aml-screening-process']"));
        assertTrue(has(parent, "//bpmn:callActivity[@id='sanctionsScreeningCall']"
            + "/bpmn:extensionElements/zeebe:calledElement[@processId='sanctions-screening-process']"));
        assertEquals(3, count(parent, "//bpmn:callActivity"));
        assertEquals(5, count(parent, "//bpmn:callActivity//zeebe:input"));
        assertDiagramCoverage(parent);
        assertTrue(has(parent, "//bpmndi:BPMNPlane[@bpmnElement='Collaboration_1w3t2zw']"));
        assertTrue(has(parent, "//bpmndi:BPMNShape[@bpmnElement='kycScreeningCall']/dc:Bounds"));
        assertTrue(has(parent, "//bpmndi:BPMNShape[@bpmnElement='amlScreeningCall']/dc:Bounds"));
        assertTrue(has(parent, "//bpmndi:BPMNShape[@bpmnElement='sanctionsScreeningCall']/dc:Bounds"));
        assertTrue(has(parent, "//bpmndi:BPMNShape[@bpmnElement='recoverUnknownSubmission']/dc:Bounds"));
        assertTrue(has(parent, "//bpmndi:BPMNShape[@bpmnElement='unknownRecoveryGateway']"
            + "[@isMarkerVisible='true']"));
        assertFalse(has(parent, "//bpmndi:BPMNShape[@bpmnElement='kycSubjectWait']"));
        assertFalse(has(parent, "//bpmndi:BPMNEdge[@bpmnElement='flowKycSubjectStart']"));
        assertEquals(4, count(parent, "//bpmndi:BPMNEdge[@bpmnElement='flowUnknownRecoveryHash']"
            + "/di:waypoint"));
        assertTrue(Files.readString(firstOutput.resolve("report.json")).contains("\"schemaVersion\": \"1.0.0\""));
        assertTrue(Files.readString(firstOutput.resolve("report.json")).contains("\"status\": \"PASSED\""));
    }

    @Test
    void generatedContractsKeepCorrelationsTimeoutsAndWorkerDefinitions() throws Exception {
        Path output = temporaryDirectory.resolve("generated");
        new BpmnWorkflowGenerator().generate(copySource("transfer-lifecycle-source.bpmn"), output);

        Document kyc = parse(output.resolve("kyc-screening-process.bpmn"));
        Document aml = parse(output.resolve("aml-screening-process.bpmn"));
        Document sanctions = parse(output.resolve("sanctions-screening-process.bpmn"));
        assertTrue(has(kyc, "//bpmn:process[@id='kyc-screening-process']/bpmn:documentation"));
        assertTrue(has(kyc, "//bpmn:subProcess[@id='kycScreeningRequests']"
            + "/bpmn:multiInstanceLoopCharacteristics/bpmn:extensionElements/zeebe:loopCharacteristics"
            + "[@inputCollection='=kycScreeningRequestIds']"));
        assertTrue(has(kyc, "//bpmn:message[@name='KycScreeningResult']"
            + "/bpmn:extensionElements/zeebe:subscription[@correlationKey='=kycScreeningRequestId']"));
        assertEquals(1, count(kyc, "//bpmn:timerEventDefinition[bpmn:timeDuration='PT15M']"));
        assertTrue(has(aml, "//bpmn:message[@name='AmlScreeningResult']"
            + "/bpmn:extensionElements/zeebe:subscription[@correlationKey='=amlCorrelationKey']"));
        assertTrue(has(sanctions, "//bpmn:message[@name='SanctionsScreeningResult']"
            + "/bpmn:extensionElements/zeebe:subscription[@correlationKey='=sanctionsCorrelationKey']"));
        assertEquals(1, count(aml, "//bpmn:timerEventDefinition[bpmn:timeDuration='PT15M']"));
        assertEquals(1, count(sanctions, "//bpmn:timerEventDefinition[bpmn:timeDuration='PT15M']"));
        assertTrue(has(aml, "//bpmn:serviceTask[@id='requestAml']"
            + "/bpmn:extensionElements/zeebe:taskDefinition[@retries='3']"));
        assertTrue(has(sanctions, "//bpmn:serviceTask[@id='requestSanctions']"
            + "/bpmn:extensionElements/zeebe:taskDefinition[@retries='3']"));
    }

    @Test
    void generatedAmlProcessHasCompleteDiagramInterchange() throws Exception {
        Path output = temporaryDirectory.resolve("generated-with-di");
        new BpmnWorkflowGenerator().generate(copySource("transfer-lifecycle-source.bpmn"), output);
        Document aml = parse(output.resolve("aml-screening-process.bpmn"));

        assertDiagramCoverage(aml);
        assertTrue(has(aml, "//bpmndi:BPMNDiagram/bpmndi:BPMNPlane"
            + "[@bpmnElement='aml-screening-process']"));
        assertEquals(9, count(aml, "//bpmndi:BPMNShape"));
        assertEquals(10, count(aml, "//bpmndi:BPMNEdge"));
        assertEquals(0, count(aml, "//bpmndi:BPMNShape[not(dc:Bounds)]"));
        assertEquals(0, count(aml, "//bpmndi:BPMNEdge[not(di:waypoint[2])]"));
        assertTrue(has(aml, "//bpmndi:BPMNShape[@bpmnElement='amlRequestGateway']"
            + "[@isMarkerVisible='true']"));
        assertTrue(has(aml, "//bpmndi:BPMNEdge[@bpmnElement='flowAmlPolicyHoldJoin']"
            + "/di:waypoint[4]"));
    }

    @Test
    void generatedKycProcessHasDiagramForExpandedMultiInstanceSubprocess() throws Exception {
        Path output = temporaryDirectory.resolve("generated-kyc-with-di");
        new BpmnWorkflowGenerator().generate(copySource("transfer-lifecycle-source.bpmn"), output);
        Document kyc = parse(output.resolve("kyc-screening-process.bpmn"));

        assertDiagramCoverage(kyc);
        assertTrue(has(kyc, "//bpmndi:BPMNDiagram/bpmndi:BPMNPlane"
            + "[@bpmnElement='kyc-screening-process']"));
        assertEquals(12, count(kyc, "//bpmndi:BPMNShape"));
        assertEquals(13, count(kyc, "//bpmndi:BPMNEdge"));
        assertEquals(0, count(kyc, "//bpmndi:BPMNShape[not(dc:Bounds)]"));
        assertEquals(0, count(kyc, "//bpmndi:BPMNEdge[not(di:waypoint[2])]"));
        assertTrue(has(kyc, "//bpmndi:BPMNShape[@bpmnElement='kycScreeningRequests']"
            + "[@isExpanded='true']/dc:Bounds[@width='640'][@height='240']"));
        assertTrue(has(kyc, "//bpmndi:BPMNShape[@bpmnElement='kycSubjectWait']"
            + "/dc:Bounds[@width='50'][@height='50']"));
        assertTrue(has(kyc, "//bpmndi:BPMNEdge[@bpmnElement='flowKycSubjectTimeoutComplete']"
            + "/di:waypoint[3]"));
    }

    @Test
    void generatedSanctionsProcessHasCompleteDiagramInterchange() throws Exception {
        Path output = temporaryDirectory.resolve("generated-sanctions-with-di");
        new BpmnWorkflowGenerator().generate(copySource("transfer-lifecycle-source.bpmn"), output);
        Document sanctions = parse(output.resolve("sanctions-screening-process.bpmn"));

        assertDiagramCoverage(sanctions);
        assertTrue(has(sanctions, "//bpmndi:BPMNDiagram/bpmndi:BPMNPlane"
            + "[@bpmnElement='sanctions-screening-process']"));
        assertEquals(9, count(sanctions, "//bpmndi:BPMNShape"));
        assertEquals(10, count(sanctions, "//bpmndi:BPMNEdge"));
        assertEquals(0, count(sanctions, "//bpmndi:BPMNShape[not(dc:Bounds)]"));
        assertEquals(0, count(sanctions, "//bpmndi:BPMNEdge[not(di:waypoint[2])]"));
        assertTrue(has(sanctions, "//bpmndi:BPMNShape[@bpmnElement='sanctionsRequestGateway']"
            + "[@isMarkerVisible='true']"));
        assertTrue(has(sanctions, "//bpmndi:BPMNEdge[@bpmnElement='flowSanctionsPolicyHoldJoin']"
            + "/di:waypoint[4]"));
    }

    @Test
    void malformedAndUnsupportedInputsProduceActionableFailureReportsWithoutPartialArtifacts() throws Exception {
        Path malformed = temporaryDirectory.resolve("malformed.bpmn");
        Files.writeString(malformed, "<!DOCTYPE x [<!ENTITY leak SYSTEM \"file:///secret\">]><x>&leak;</x>");
        Path malformedOutput = temporaryDirectory.resolve("malformed-output");

        assertThrows(BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(malformed, malformedOutput));

        assertFalse(Files.exists(malformedOutput.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(malformedOutput.resolve("report.json")).contains("\"status\":\"FAILED\""));
        assertTrue(Files.readString(malformedOutput.resolve("report.json")).contains("DOCTYPE"));

        Path unsupported = copySource("unsupported.bpmn");
        String sourceXml = Files.readString(unsupported).replace(
            "type=\"screening-request-aml\"",
            "type=\"unrecognized-screening-worker\""
        );
        Files.writeString(unsupported, sourceXml);
        Path unsupportedOutput = temporaryDirectory.resolve("unsupported-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, unsupportedOutput)
        );

        assertTrue(failure.getMessage().contains("requestAml"));
        assertFalse(Files.exists(unsupportedOutput.resolve("aml-screening-process.bpmn")));
        assertTrue(Files.readString(unsupportedOutput.resolve("report.json")).contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejectsValidationFlowThatBypassesMandatoryScreening() throws Exception {
        Path unsupported = temporaryDirectory.resolve("bypasses-screening.bpmn");
        String sourceXml;
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            sourceXml = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.writeString(unsupported, sourceXml.replace(
            "id=\"flowValid\" sourceRef=\"validationGateway\" targetRef=\"screeningFork\"",
            "id=\"flowValid\" sourceRef=\"validationGateway\" targetRef=\"submitTransfer\""
        ));
        Path output = temporaryDirectory.resolve("bypasses-screening-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertTrue(failure.getMessage().contains("flowValid"));
        assertFalse(Files.exists(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejectsAdditionalValidationRouteThatCouldBypassScreening() throws Exception {
        Path unsupported = temporaryDirectory.resolve("extra-validation-route.bpmn");
        String sourceXml;
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            sourceXml = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        sourceXml = sourceXml.replace(
            "</bpmn:process>",
            "<bpmn:sequenceFlow id=\"flowValidationBypass\" sourceRef=\"validationGateway\" "
                + "targetRef=\"transferCompleted\"><bpmn:conditionExpression "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:type=\"bpmn:tFormalExpression\">=validationPassed = true"
                + "</bpmn:conditionExpression></bpmn:sequenceFlow></bpmn:process>"
        );
        Files.writeString(unsupported, sourceXml);
        Path output = temporaryDirectory.resolve("extra-validation-route-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertTrue(failure.getMessage().contains("validationGateway"));
        assertFalse(Files.exists(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejectsAdditionalValidationTaskRouteThatCouldBypassScreening() throws Exception {
        Path unsupported = temporaryDirectory.resolve("extra-validation-task-route.bpmn");
        String sourceXml;
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            sourceXml = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        sourceXml = sourceXml.replace(
            "</bpmn:process>",
            "<bpmn:sequenceFlow id=\"flowValidationTaskBypass\" sourceRef=\"validateTransfer\" "
                + "targetRef=\"transferCompleted\" /></bpmn:process>"
        );
        Files.writeString(unsupported, sourceXml);
        Path output = temporaryDirectory.resolve("extra-validation-task-route-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertTrue(failure.getMessage().contains("validateTransfer"));
        assertFalse(Files.exists(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\": \"FAILED\""));
    }

    @Test
    void rejectsUnknownSubmissionFlowThatCouldReportCompletion() throws Exception {
        Path unsupported = temporaryDirectory.resolve("unknown-submission-completes.bpmn");
        String sourceXml;
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            sourceXml = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.writeString(unsupported, sourceXml.replace(
            "id=\"flowSubmissionUnknown\" sourceRef=\"submissionGateway\" targetRef=\"submissionUnknown\"",
            "id=\"flowSubmissionUnknown\" sourceRef=\"submissionGateway\" targetRef=\"transferCompleted\""
        ));
        Path output = temporaryDirectory.resolve("unknown-submission-completes-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertTrue(failure.getMessage().contains("flowSubmissionUnknown"));
        assertFalse(Files.exists(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejectsFailOpenExecutionGatewayDefault() throws Exception {
        Path unsupported = temporaryDirectory.resolve("fail-open-execution-gateway.bpmn");
        String sourceXml;
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            sourceXml = new String(source.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.writeString(unsupported, sourceXml.replace(
            "id=\"executionGateway\" name=\"Execution gate passed?\" default=\"flowExecutionHeld\"",
            "id=\"executionGateway\" name=\"Execution gate passed?\" default=\"flowExecutionAllowed\""
        ));
        Path output = temporaryDirectory.resolve("fail-open-execution-gateway-output");

        BpmnWorkflowGenerator.GenerationException failure = assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertTrue(failure.getMessage().contains("executionGateway"));
        assertFalse(Files.exists(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\":\"FAILED\""));
    }

    @Test
    void failedGenerationPreservesTheLastCompleteArtifactSetButReplacesItsSuccessReport() throws Exception {
        Path source = copySource("transfer-lifecycle-source.bpmn");
        Path output = temporaryDirectory.resolve("preserved-output");
        new BpmnWorkflowGenerator().generate(source, output);
        String lastGoodParent = Files.readString(output.resolve("transfer-lifecycle-source.bpmn"));

        Path unsupported = temporaryDirectory.resolve("unsupported-existing-output.bpmn");
        Files.writeString(unsupported, Files.readString(source).replace(
            "type=\"screening-request-aml\"",
            "type=\"unsupported-screening-request\""
        ));
        org.junit.jupiter.api.Assertions.assertThrows(
            BpmnWorkflowGenerator.GenerationException.class,
            () -> new BpmnWorkflowGenerator().generate(unsupported, output)
        );

        assertEquals(lastGoodParent, Files.readString(output.resolve("transfer-lifecycle-source.bpmn")));
        assertTrue(Files.readString(output.resolve("report.json")).contains("\"status\": \"FAILED\""));
    }

    private Path copySource(String name) throws Exception {
        Path destination = temporaryDirectory.resolve(name);
        try (InputStream source = getClass().getResourceAsStream("/processes/transfer-lifecycle-source.bpmn")) {
            if (source == null) {
                throw new IllegalStateException("Transfer BPMN test resource is missing");
            }
            Files.copy(source, destination);
        }
        return destination;
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private boolean has(Document document, String expression) throws Exception {
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new TestNamespaceContext());
        return Boolean.TRUE.equals(xpath.evaluate(expression, document, javax.xml.xpath.XPathConstants.BOOLEAN));
    }

    private int count(Document document, String expression) throws Exception {
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new TestNamespaceContext());
        String nodeSetExpression = expression.startsWith("count(")
            ? expression.substring("count(".length(), expression.length() - 1)
            : expression;
        return ((org.w3c.dom.NodeList) xpath.evaluate(
            nodeSetExpression, document, javax.xml.xpath.XPathConstants.NODESET
        )).getLength();
    }

    private void assertDiagramCoverage(Document document) {
        var process = document.getElementsByTagNameNS(BPMN, "process").item(0);
        Set<String> flowNodeIds = new HashSet<>();
        for (String nodeType : List.of(
            "startEvent", "serviceTask", "callActivity", "exclusiveGateway", "parallelGateway",
            "eventBasedGateway", "intermediateCatchEvent", "endEvent", "subProcess"
        )) {
            var nodes = ((org.w3c.dom.Element) process).getElementsByTagNameNS(BPMN, nodeType);
            for (int index = 0; index < nodes.getLength(); index++) {
                flowNodeIds.add(((org.w3c.dom.Element) nodes.item(index)).getAttribute("id"));
            }
        }
        Set<String> sequenceFlowIds = new HashSet<>();
        var flows = ((org.w3c.dom.Element) process).getElementsByTagNameNS(BPMN, "sequenceFlow");
        for (int index = 0; index < flows.getLength(); index++) {
            sequenceFlowIds.add(((org.w3c.dom.Element) flows.item(index)).getAttribute("id"));
        }
        Set<String> shapeReferences = new HashSet<>();
        var shapes = document.getElementsByTagNameNS(BPMNDI, "BPMNShape");
        for (int index = 0; index < shapes.getLength(); index++) {
            shapeReferences.add(((org.w3c.dom.Element) shapes.item(index)).getAttribute("bpmnElement"));
        }
        Set<String> edgeReferences = new HashSet<>();
        var edges = document.getElementsByTagNameNS(BPMNDI, "BPMNEdge");
        for (int index = 0; index < edges.getLength(); index++) {
            edgeReferences.add(((org.w3c.dom.Element) edges.item(index)).getAttribute("bpmnElement"));
        }

        shapeReferences.remove("Participant_1t8twvl");
        assertEquals(flowNodeIds, shapeReferences);
        assertEquals(sequenceFlowIds, edgeReferences);
    }

    private static final class TestNamespaceContext implements javax.xml.namespace.NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "bpmn" -> BPMN;
                case "bpmndi" -> BPMNDI;
                case "di" -> DI;
                case "dc" -> DC;
                case "zeebe" -> ZEEBE;
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

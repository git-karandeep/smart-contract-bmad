package ai.generalcontracts.startup.workflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import io.camunda.zeebe.model.bpmn.Bpmn;

/**
 * Generates a validated parent BPMN process and its reusable screening processes.
 * The supported topology is deliberately explicit: unknown source shapes are rejected
 * rather than inferred into potentially unsafe process behavior.
 */
public final class BpmnWorkflowGenerator {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    private static final String DI = "http://www.omg.org/spec/DD/20100524/DI";
    private static final String DC = "http://www.omg.org/spec/DD/20100524/DC";
    private static final String ZEEBE = "http://camunda.org/schema/zeebe/1.0";
    private static final String BPMN_PREFIX = "bpmn:";
    private static final String BPMNDI_PREFIX = "bpmndi:";
    private static final String DI_PREFIX = "di:";
    private static final String DC_PREFIX = "dc:";
    private static final String ZEEBE_PREFIX = "zeebe:";
    private static final String INPUT_PROCESS_ID = "transfer-lifecycle";
    private static final String REPORT_NAME = "report.json";
    private static final List<ScreeningProcess> SCREENINGS = List.of(
        new ScreeningProcess(
            "KYC",
            "kyc-screening-process",
            "kyc-screening-process.bpmn",
            "kycResultMessage",
            List.of(
                "requestKyc", "kycRequestGateway", "kycPolicyHold", "kycScreeningRequests"
            ),
            List.of(
                "flowKycRequestGateway", "flowKycRequestPublished", "flowKycNotRequired",
                "flowKycPolicyHold", "flowKycPolicyHoldJoin", "flowKycRequestsJoin"
            ),
            List.of(
                "flowKycPolicyHoldJoin", "flowKycRequestsJoin", "flowKycNotRequired"
            ),
            "requestKyc",
            "flowKycRequest",
            "flowKycRequestsJoin",
            "kycScreeningCall",
            List.of("instructionId"),
            List.of("kycRequestReady", "kycNotRequired", "kycScreeningRequestIds")
        ),
        new ScreeningProcess(
            "AML",
            "aml-screening-process",
            "aml-screening-process.bpmn",
            "amlResultMessage",
            List.of(
                "requestAml", "amlRequestGateway", "amlWait", "amlResult", "amlTimeout",
                "amlTimeoutPersist", "amlPolicyHold"
            ),
            List.of(
                "flowAmlRequestGateway", "flowAmlRequestPublished", "flowAmlPolicyHold",
                "flowAmlResult", "flowAmlTimeout", "flowAmlResultJoin",
                "flowAmlTimeoutPersist", "flowAmlTimeoutJoin", "flowAmlPolicyHoldJoin"
            ),
            List.of("flowAmlResultJoin", "flowAmlTimeoutJoin", "flowAmlPolicyHoldJoin"),
            "requestAml",
            "flowAmlRequest",
            "flowAmlResultJoin",
            "amlScreeningCall",
            List.of("instructionId", "amlCorrelationKey"),
            List.of("amlRequestReady", "amlTimeoutPersisted")
        ),
        new ScreeningProcess(
            "SANCTIONS",
            "sanctions-screening-process",
            "sanctions-screening-process.bpmn",
            "sanctionsResultMessage",
            List.of(
                "requestSanctions", "sanctionsRequestGateway", "sanctionsWait", "sanctionsResult",
                "sanctionsTimeout", "sanctionsTimeoutPersist", "sanctionsPolicyHold"
            ),
            List.of(
                "flowSanctionsRequestGateway", "flowSanctionsRequestPublished", "flowSanctionsPolicyHold",
                "flowSanctionsResult", "flowSanctionsTimeout", "flowSanctionsResultJoin",
                "flowSanctionsTimeoutPersist", "flowSanctionsTimeoutJoin", "flowSanctionsPolicyHoldJoin"
            ),
            List.of(
                "flowSanctionsResultJoin", "flowSanctionsTimeoutJoin", "flowSanctionsPolicyHoldJoin"
            ),
            "requestSanctions",
            "flowSanctionsRequest",
            "flowSanctionsResultJoin",
            "sanctionsScreeningCall",
            List.of("instructionId", "sanctionsCorrelationKey"),
            List.of("sanctionsRequestReady", "sanctionsTimeoutPersisted")
        )
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: BpmnWorkflowGenerator <input-bpmn> <output-directory>");
        }
        new BpmnWorkflowGenerator().generate(Path.of(args[0]), Path.of(args[1]));
    }

    public void generate(Path source, Path outputDirectory) {
        Path input = source.toAbsolutePath().normalize();
        Path output = outputDirectory.toAbsolutePath().normalize();
        try {
            Document sourceDocument = parse(Files.readAllBytes(input));
            validateSource(sourceDocument);
            Map<String, byte[]> generated = new LinkedHashMap<>();
            generated.put("transfer-lifecycle-source.bpmn", serialize(generateParent(sourceDocument)));
            for (ScreeningProcess screening : SCREENINGS) {
                generated.put(screening.fileName(), serialize(generateScreeningProcess(sourceDocument, screening)));
            }
            validateGenerated(generated);
            validateCamundaModels(generated);
            generated.put(REPORT_NAME, successReport(input.getFileName().toString(), sourceDocument));
            publish(output, generated);
        } catch (Exception failure) {
            String message = actionableMessage(failure);
            try {
                writeFailureReport(output, input.getFileName().toString(), message);
            } catch (IOException reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            if (failure instanceof GenerationException generationFailure) {
                throw generationFailure;
            }
            throw new GenerationException(message, failure);
        }
    }

    private Document generateParent(Document source) throws Exception {
        Document parent = parse(serialize(source));
        Element definitions = parent.getDocumentElement();
        Element process = directChildById(definitions, "process", INPUT_PROCESS_ID);
        for (ScreeningProcess screening : SCREENINGS) {
            for (String flowId : screening.extractedFlowIds()) {
                if (!flowId.equals(screening.inboundFlowId())
                    && !flowId.equals(screening.outboundFlowId())) {
                    removeDirectChildById(process, flowId);
                }
            }
            for (String nodeId : screening.extractedNodeIds()) {
                removeDirectChildById(process, nodeId);
            }
            Element inbound = directChildById(process, screening.inboundFlowId());
            inbound.setAttribute("targetRef", screening.callId());
            Element outbound = directChildById(process, screening.outboundFlowId());
            outbound.setAttribute("sourceRef", screening.callId());
            process.insertBefore(createCallActivity(parent, screening), firstSequenceFlow(process));
        }
        for (String messageId : List.of("kycResultMessage", "amlResultMessage", "sanctionsResultMessage")) {
            removeDirectChildById(definitions, messageId);
        }
        addPaymentRecoveryRoute(parent, process);
        updateParentDiagram(definitions, process);
        return parent;
    }

    private void updateParentDiagram(Element definitions, Element process) {
        Element diagram = directChildByLocalName(definitions, "BPMNDiagram");
        Element plane = diagram == null ? null : directChildByLocalName(diagram, "BPMNPlane");
        require(plane != null, "Input BPMN diagram plane is required for transfer-lifecycle");

        for (ScreeningProcess screening : SCREENINGS) {
            Element originalShape = diagramElement(plane, "BPMNShape", screening.firstNodeId());
            Element originalBounds = originalShape == null ? null : directChildByLocalName(originalShape, "Bounds");
            require(originalBounds != null,
                "Input BPMN diagram is missing bounds for screening node " + screening.firstNodeId());
            int x = integerAttribute(originalBounds, "x");
            int y = integerAttribute(originalBounds, "y");
            int width = integerAttribute(originalBounds, "width");
            int height = integerAttribute(originalBounds, "height");

            for (String nodeId : screening.extractedNodeIds()) {
                removeDiagramElement(plane, "BPMNShape", nodeId);
            }
            for (String flowId : screening.extractedFlowIds()) {
                if (!flowId.equals(screening.inboundFlowId())
                    && !flowId.equals(screening.outboundFlowId())) {
                    removeDiagramElement(plane, "BPMNEdge", flowId);
                }
            }
            addAmlShape(plane, screening.callId(), x, y, width, height);
            updateScreeningJoinEdge(plane, screening);
        }

        addAmlShape(plane, "recoverUnknownSubmission", 2670, 340, 100, 80);
        addAmlShape(plane, "unknownRecoveryGateway", 2835, 355, 50, 50, true);
        setEdgeWaypoints(plane, "flowSubmissionUnknown", 2700, 585, 2700, 420, 2720, 420);
        addAmlEdge(plane, "flowUnknownRecoveryResult", 2770, 380, 2835, 380);
        addAmlEdge(plane, "flowUnknownRecoveryHash", 2885, 380, 2900, 380, 2900, 610, 2858, 610);
        addAmlEdge(plane, "flowUnknownRecoveryHold", 2835, 380, 2785, 380, 2785, 470, 2718, 470);
        removeDanglingDiagramElements(definitions, process, plane);
    }

    private void removeDanglingDiagramElements(Element definitions, Element process, Element plane) {
        Set<String> processIds = new HashSet<>();
        NodeList elements = process.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (element.hasAttribute("id")) {
                processIds.add(element.getAttribute("id"));
            }
        }
        Set<String> participants = new HashSet<>();
        NodeList participantElements = definitions.getElementsByTagNameNS(BPMN, "participant");
        for (int index = 0; index < participantElements.getLength(); index++) {
            participants.add(((Element) participantElements.item(index)).getAttribute("id"));
        }
        for (Element shape : directChildren(plane, "BPMNShape")) {
            if (!processIds.contains(shape.getAttribute("bpmnElement"))
                && !participants.contains(shape.getAttribute("bpmnElement"))) {
                plane.removeChild(shape);
            }
        }
        for (Element edge : directChildren(plane, "BPMNEdge")) {
            if (!processIds.contains(edge.getAttribute("bpmnElement"))) {
                plane.removeChild(edge);
            }
        }
    }

    private void updateScreeningJoinEdge(Element plane, ScreeningProcess screening) {
        if ("flowKycRequestsJoin".equals(screening.outboundFlowId())) {
            setEdgeWaypoints(plane, screening.outboundFlowId(),
                830, 310, 1740, 310, 1740, 610, 1805, 610);
        } else if ("flowAmlResultJoin".equals(screening.outboundFlowId())) {
            setEdgeWaypoints(plane, screening.outboundFlowId(), 830, 610, 1805, 610);
        } else {
            setEdgeWaypoints(plane, screening.outboundFlowId(),
                830, 850, 1740, 850, 1740, 610, 1805, 610);
        }
    }

    private void setEdgeWaypoints(Element plane, String flowId, int... coordinates) {
        Element edge = diagramElement(plane, "BPMNEdge", flowId);
        require(edge != null, "Input BPMN diagram is missing edge " + flowId);
        while (edge.hasChildNodes()) {
            edge.removeChild(edge.getFirstChild());
        }
        appendWaypoints(edge, coordinates);
    }

    private void appendWaypoints(Element edge, int... coordinates) {
        for (int index = 0; index < coordinates.length; index += 2) {
            Element waypoint = edge.getOwnerDocument().createElementNS(DI, DI_PREFIX + "waypoint");
            waypoint.setAttribute("x", Integer.toString(coordinates[index]));
            waypoint.setAttribute("y", Integer.toString(coordinates[index + 1]));
            edge.appendChild(waypoint);
        }
    }

    private Element diagramElement(Element plane, String localName, String bpmnElement) {
        for (Element candidate : directChildren(plane, localName)) {
            if (bpmnElement.equals(candidate.getAttribute("bpmnElement"))) {
                return candidate;
            }
        }
        return null;
    }

    private void removeDiagramElement(Element plane, String localName, String bpmnElement) {
        Element element = diagramElement(plane, localName, bpmnElement);
        if (element != null) {
            plane.removeChild(element);
        }
    }

    private int integerAttribute(Element element, String name) {
        try {
            return Integer.parseInt(element.getAttribute(name));
        } catch (NumberFormatException invalidCoordinate) {
            throw new GenerationException("Invalid BPMN diagram " + name + " coordinate", invalidCoordinate);
        }
    }

    private void addPaymentRecoveryRoute(Document document, Element process) {
        Element recovery = document.createElementNS(BPMN, BPMN_PREFIX + "serviceTask");
        recovery.setAttribute("id", "recoverUnknownSubmission");
        recovery.setAttribute("name", "Look up journaled payment submission");
        Element recoveryDocumentation = document.createElementNS(BPMN, BPMN_PREFIX + "documentation");
        recoveryDocumentation.setTextContent(
            "Lookup the existing instruction-keyed submission journal only. "
                + "Reconcile through the existing transaction-result path only when a matching known hash exists. "
                + "Without a journaled hash, keep the transfer operationally held. Never submit again."
        );
        recovery.appendChild(recoveryDocumentation);
        Element recoveryExtensions = document.createElementNS(BPMN, BPMN_PREFIX + "extensionElements");
        Element taskDefinition = document.createElementNS(ZEEBE, ZEEBE_PREFIX + "taskDefinition");
        taskDefinition.setAttribute("type", "transfer-recover-unknown");
        taskDefinition.setAttribute("retries", "3");
        recoveryExtensions.appendChild(taskDefinition);
        Element recoveryMapping = document.createElementNS(ZEEBE, ZEEBE_PREFIX + "ioMapping");
        appendMapping(document, recoveryMapping, "input", "=instructionId", "instructionId");
        for (String variable : List.of(
            "recoveryHasTransactionHash", "recoveryRequiresInvestigation", "recoveryReasonCode",
            "transactionHash", "transactionOutcome", "finalized"
        )) {
            appendMapping(document, recoveryMapping, "output", "=" + variable, variable);
        }
        recoveryExtensions.appendChild(recoveryMapping);
        recovery.appendChild(recoveryExtensions);

        Element gateway = document.createElementNS(BPMN, BPMN_PREFIX + "exclusiveGateway");
        gateway.setAttribute("id", "unknownRecoveryGateway");
        gateway.setAttribute("name", "Journaled transaction hash found?");
        gateway.setAttribute("default", "flowUnknownRecoveryHold");
        process.insertBefore(recovery, firstSequenceFlow(process));
        process.insertBefore(gateway, firstSequenceFlow(process));

        Element originalUnknownFlow = directChildById(process, "flowSubmissionUnknown");
        originalUnknownFlow.setAttribute("targetRef", "recoverUnknownSubmission");
        Element taskToGateway = document.createElementNS(BPMN, BPMN_PREFIX + "sequenceFlow");
        taskToGateway.setAttribute("id", "flowUnknownRecoveryResult");
        taskToGateway.setAttribute("sourceRef", "recoverUnknownSubmission");
        taskToGateway.setAttribute("targetRef", "unknownRecoveryGateway");
        process.appendChild(taskToGateway);

        Element hashFound = document.createElementNS(BPMN, BPMN_PREFIX + "sequenceFlow");
        hashFound.setAttribute("id", "flowUnknownRecoveryHash");
        hashFound.setAttribute("sourceRef", "unknownRecoveryGateway");
        hashFound.setAttribute("targetRef", "transactionResult");
        Element condition = document.createElementNS(BPMN, BPMN_PREFIX + "conditionExpression");
        condition.setAttributeNS(
            "http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "bpmn:tFormalExpression"
        );
        condition.setTextContent("=recoveryHasTransactionHash = true");
        hashFound.appendChild(condition);
        process.appendChild(hashFound);

        Element noHash = document.createElementNS(BPMN, BPMN_PREFIX + "sequenceFlow");
        noHash.setAttribute("id", "flowUnknownRecoveryHold");
        noHash.setAttribute("sourceRef", "unknownRecoveryGateway");
        noHash.setAttribute("targetRef", "submissionUnknown");
        process.appendChild(noHash);
    }

    private Element createCallActivity(Document document, ScreeningProcess screening) {
        Element call = document.createElementNS(BPMN, BPMN_PREFIX + "callActivity");
        call.setAttribute("id", screening.callId());
        call.setAttribute("name", screening.type() + " screening");
        Element extensions = document.createElementNS(BPMN, BPMN_PREFIX + "extensionElements");
        Element calledElement = document.createElementNS(ZEEBE, ZEEBE_PREFIX + "calledElement");
        calledElement.setAttribute("processId", screening.processId());
        calledElement.setAttribute("propagateAllParentVariables", "false");
        calledElement.setAttribute("propagateAllChildVariables", "false");
        extensions.appendChild(calledElement);
        Element mapping = document.createElementNS(ZEEBE, ZEEBE_PREFIX + "ioMapping");
        for (String input : screening.inputs()) {
            appendMapping(document, mapping, "input", "=" + input, input);
        }
        for (String output : screening.outputs()) {
            appendMapping(document, mapping, "output", "=" + output, output);
        }
        extensions.appendChild(mapping);
        call.appendChild(extensions);
        return call;
    }

    private void appendMapping(Document document, Element mapping, String direction, String source, String target) {
        Element entry = document.createElementNS(ZEEBE, ZEEBE_PREFIX + direction);
        entry.setAttribute("source", source);
        entry.setAttribute("target", target);
        mapping.appendChild(entry);
    }

    private Document generateScreeningProcess(Document source, ScreeningProcess screening) throws Exception {
        Document result = newDocument();
        Element sourceDefinitions = source.getDocumentElement();
        Element definitions = result.createElementNS(BPMN, BPMN_PREFIX + "definitions");
        copyAttributes(sourceDefinitions, definitions);
        result.appendChild(definitions);
        definitions.appendChild(result.importNode(
            directChildById(sourceDefinitions, screening.messageId()), true
        ));

        Element sourceProcess = directChildById(sourceDefinitions, "process", INPUT_PROCESS_ID);
        Element process = result.createElementNS(BPMN, BPMN_PREFIX + "process");
        process.setAttribute("id", screening.processId());
        process.setAttribute("name", screening.type() + " screening");
        process.setAttribute("isExecutable", "true");
        Element documentation = result.createElementNS(BPMN, BPMN_PREFIX + "documentation");
        documentation.setTextContent(contractDocumentation(screening));
        process.appendChild(documentation);
        definitions.appendChild(process);

        Element start = result.createElementNS(BPMN, BPMN_PREFIX + "startEvent");
        start.setAttribute("id", screening.type().toLowerCase(java.util.Locale.ROOT) + "Start");
        start.setAttribute("name", "Start " + screening.type() + " screening");
        process.appendChild(start);
        Element startFlow = result.createElementNS(BPMN, BPMN_PREFIX + "sequenceFlow");
        startFlow.setAttribute("id", screening.type().toLowerCase(java.util.Locale.ROOT) + "StartFlow");
        startFlow.setAttribute("sourceRef", start.getAttribute("id"));
        startFlow.setAttribute("targetRef", screening.firstNodeId());

        for (String nodeId : screening.extractedNodeIds()) {
            process.appendChild(result.importNode(directChildById(sourceProcess, nodeId), true));
        }
        process.appendChild(startFlow);
        for (String flowId : screening.extractedFlowIds()) {
            Element flow = (Element) result.importNode(directChildById(sourceProcess, flowId), true);
            if (screening.terminalFlowIds().contains(flowId)) {
                flow.setAttribute("targetRef", screening.endId());
            }
            process.appendChild(flow);
        }
        Element end = result.createElementNS(BPMN, BPMN_PREFIX + "endEvent");
        end.setAttribute("id", screening.endId());
        end.setAttribute("name", screening.type() + " outcome persisted");
        process.appendChild(end);
        if ("KYC".equals(screening.type())) {
            addKycDiagram(definitions, process);
        } else if ("AML".equals(screening.type())) {
            addAmlDiagram(definitions, process);
        } else if ("SANCTIONS".equals(screening.type())) {
            addSanctionsDiagram(definitions, process);
        }
        return result;
    }

    private void addKycDiagram(Element definitions, Element process) {
        Element diagram = definitions.getOwnerDocument()
            .createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNDiagram");
        diagram.setAttribute("id", "BPMNDiagram_kyc-screening-process");
        Element plane = definitions.getOwnerDocument()
            .createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNPlane");
        plane.setAttribute("id", "BPMNPlane_kyc-screening-process");
        plane.setAttribute("bpmnElement", process.getAttribute("id"));
        diagram.appendChild(plane);
        definitions.appendChild(diagram);

        addAmlShape(plane, "kycStart", 100, 292, 36, 36);
        addAmlShape(plane, "requestKyc", 200, 270, 100, 80);
        addAmlShape(plane, "kycRequestGateway", 365, 285, 50, 50, true);
        addAmlShape(plane, "kycPolicyHold", 540, 100, 100, 80);
        addAmlShape(plane, "kycScreeningRequests", 500, 190, 640, 240, false, true);
        addAmlShape(plane, "kycSubjectWaitStart", 522, 292, 36, 36);
        addAmlShape(plane, "kycSubjectWait", 595, 285, 50, 50);
        addAmlShape(plane, "kycResult", 722, 222, 36, 36);
        addAmlShape(plane, "kycTimeout", 722, 362, 36, 36);
        addAmlShape(plane, "kycTimeoutPersist", 820, 340, 100, 80);
        addAmlShape(plane, "kycSubjectComplete", 1042, 292, 36, 36);
        addAmlShape(plane, "kycEnd", 1260, 292, 36, 36);

        addAmlEdge(plane, "kycStartFlow", 136, 310, 200, 310);
        addAmlEdge(plane, "flowKycRequestGateway", 300, 310, 365, 310);
        addAmlEdge(plane, "flowKycRequestPublished", 415, 310, 500, 310);
        addAmlEdge(plane, "flowKycNotRequired", 390, 335, 390, 455, 1210, 455, 1210, 310, 1260, 310);
        addAmlEdge(plane, "flowKycPolicyHold", 390, 285, 390, 140, 540, 140);
        addAmlEdge(plane, "flowKycPolicyHoldJoin", 640, 140, 1200, 140, 1200, 310, 1260, 310);
        addAmlEdge(plane, "flowKycRequestsJoin", 1140, 310, 1260, 310);

        addAmlEdge(plane, "flowKycSubjectStart", 558, 310, 595, 310);
        addAmlEdge(plane, "flowKycSubjectResult", 620, 285, 620, 240, 722, 240);
        addAmlEdge(plane, "flowKycSubjectTimeout", 620, 335, 620, 380, 722, 380);
        addAmlEdge(plane, "flowKycSubjectResultComplete", 740, 258, 740, 310, 1042, 310);
        addAmlEdge(plane, "flowKycSubjectPersistTimeout", 758, 380, 820, 380);
        addAmlEdge(plane, "flowKycSubjectTimeoutComplete", 870, 340, 870, 310, 1042, 310);
    }

    private void addAmlDiagram(Element definitions, Element process) {
        Document document = definitions.getOwnerDocument();
        Element diagram = document.createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNDiagram");
        diagram.setAttribute("id", "BPMNDiagram_aml-screening-process");
        Element plane = document.createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNPlane");
        plane.setAttribute("id", "BPMNPlane_aml-screening-process");
        plane.setAttribute("bpmnElement", process.getAttribute("id"));
        diagram.appendChild(plane);
        definitions.appendChild(diagram);

        addAmlShape(plane, "amlStart", 100, 220, 36, 36);
        addAmlShape(plane, "requestAml", 200, 198, 120, 80);
        addAmlShape(plane, "amlRequestGateway", 390, 213, 50, 50, true);
        addAmlShape(plane, "amlWait", 520, 213, 50, 50);
        addAmlShape(plane, "amlResult", 650, 120, 36, 36);
        addAmlShape(plane, "amlTimeout", 650, 330, 36, 36);
        addAmlShape(plane, "amlTimeoutPersist", 760, 308, 120, 80);
        addAmlShape(plane, "amlPolicyHold", 520, 470, 120, 80);
        addAmlShape(plane, "amlEnd", 980, 225, 36, 36);

        addAmlEdge(plane, "amlStartFlow", 136, 238, 200, 238);
        addAmlEdge(plane, "flowAmlRequestGateway", 320, 238, 390, 238);
        addAmlEdge(plane, "flowAmlRequestPublished", 440, 238, 520, 238);
        addAmlEdge(plane, "flowAmlPolicyHold", 415, 263, 415, 510, 520, 510);
        addAmlEdge(plane, "flowAmlResult", 545, 213, 545, 138, 650, 138);
        addAmlEdge(plane, "flowAmlTimeout", 545, 263, 545, 348, 650, 348);
        addAmlEdge(plane, "flowAmlResultJoin", 686, 138, 900, 138, 900, 243, 980, 243);
        addAmlEdge(plane, "flowAmlTimeoutPersist", 686, 348, 760, 348);
        addAmlEdge(plane, "flowAmlTimeoutJoin", 880, 348, 930, 348, 930, 243, 980, 243);
        addAmlEdge(plane, "flowAmlPolicyHoldJoin", 640, 510, 950, 510, 950, 243, 980, 243);
    }

    private void addSanctionsDiagram(Element definitions, Element process) {
        Document document = definitions.getOwnerDocument();
        Element diagram = document.createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNDiagram");
        diagram.setAttribute("id", "BPMNDiagram_sanctions-screening-process");
        Element plane = document.createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNPlane");
        plane.setAttribute("id", "BPMNPlane_sanctions-screening-process");
        plane.setAttribute("bpmnElement", process.getAttribute("id"));
        diagram.appendChild(plane);
        definitions.appendChild(diagram);

        addAmlShape(plane, "sanctionsStart", 100, 220, 36, 36);
        addAmlShape(plane, "requestSanctions", 200, 198, 120, 80);
        addAmlShape(plane, "sanctionsRequestGateway", 390, 213, 50, 50, true);
        addAmlShape(plane, "sanctionsWait", 520, 213, 50, 50);
        addAmlShape(plane, "sanctionsResult", 650, 120, 36, 36);
        addAmlShape(plane, "sanctionsTimeout", 650, 330, 36, 36);
        addAmlShape(plane, "sanctionsTimeoutPersist", 760, 308, 120, 80);
        addAmlShape(plane, "sanctionsPolicyHold", 520, 470, 120, 80);
        addAmlShape(plane, "sanctionsEnd", 980, 225, 36, 36);

        addAmlEdge(plane, "sanctionsStartFlow", 136, 238, 200, 238);
        addAmlEdge(plane, "flowSanctionsRequestGateway", 320, 238, 390, 238);
        addAmlEdge(plane, "flowSanctionsRequestPublished", 440, 238, 520, 238);
        addAmlEdge(plane, "flowSanctionsPolicyHold", 415, 263, 415, 510, 520, 510);
        addAmlEdge(plane, "flowSanctionsResult", 545, 213, 545, 138, 650, 138);
        addAmlEdge(plane, "flowSanctionsTimeout", 545, 263, 545, 348, 650, 348);
        addAmlEdge(plane, "flowSanctionsResultJoin", 686, 138, 900, 138, 900, 243, 980, 243);
        addAmlEdge(plane, "flowSanctionsTimeoutPersist", 686, 348, 760, 348);
        addAmlEdge(plane, "flowSanctionsTimeoutJoin", 880, 348, 930, 348, 930, 243, 980, 243);
        addAmlEdge(plane, "flowSanctionsPolicyHoldJoin", 640, 510, 950, 510, 950, 243, 980, 243);
    }

    private void addAmlShape(
        Element plane, String elementId, int x, int y, int width, int height
    ) {
        addAmlShape(plane, elementId, x, y, width, height, false);
    }

    private void addAmlShape(
        Element plane, String elementId, int x, int y, int width, int height, boolean markerVisible
    ) {
        addAmlShape(plane, elementId, x, y, width, height, markerVisible, false);
    }

    private void addAmlShape(
        Element plane,
        String elementId,
        int x,
        int y,
        int width,
        int height,
        boolean markerVisible,
        boolean expanded
    ) {
        Document document = plane.getOwnerDocument();
        Element shape = document.createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNShape");
        shape.setAttribute("id", elementId + "_di");
        shape.setAttribute("bpmnElement", elementId);
        if (markerVisible) {
            shape.setAttribute("isMarkerVisible", "true");
        }
        if (expanded) {
            shape.setAttribute("isExpanded", "true");
        }
        Element bounds = document.createElementNS(DC, DC_PREFIX + "Bounds");
        bounds.setAttribute("x", Integer.toString(x));
        bounds.setAttribute("y", Integer.toString(y));
        bounds.setAttribute("width", Integer.toString(width));
        bounds.setAttribute("height", Integer.toString(height));
        shape.appendChild(bounds);
        plane.appendChild(shape);
    }

    private void addAmlEdge(Element plane, String flowId, int... coordinates) {
        Element edge = plane.getOwnerDocument().createElementNS(BPMNDI, BPMNDI_PREFIX + "BPMNEdge");
        edge.setAttribute("id", flowId + "_di");
        edge.setAttribute("bpmnElement", flowId);
        for (int index = 0; index < coordinates.length; index += 2) {
            Element waypoint = plane.getOwnerDocument().createElementNS(DI, DI_PREFIX + "waypoint");
            waypoint.setAttribute("x", Integer.toString(coordinates[index]));
            waypoint.setAttribute("y", Integer.toString(coordinates[index + 1]));
            edge.appendChild(waypoint);
        }
        plane.appendChild(edge);
    }

    private String contractDocumentation(ScreeningProcess screening) {
        return "Contract v1.0.0. Inputs: " + String.join(", ", screening.inputs())
            + ". Outputs: " + String.join(", ", screening.outputs())
            + ". Outcome codes: APPROVED, REJECTED, MANUAL_REVIEW, RESTRICTED, ERROR, TIMED_OUT, HOLD. "
            + "The instruction-keyed persisted screening requests and results are authoritative; "
            + "timeout, missing, and exhausted technical outcomes remain fail-closed. "
            + "Correlation is preserved by the declared screening message subscription. "
            + "Deploy compatible revisions under this stable process ID and use Camunda's process version.";
    }

    private void validateSource(Document document) {
        Element definitions = document.getDocumentElement();
        require(BPMN.equals(definitions.getNamespaceURI()) && "definitions".equals(definitions.getLocalName()),
            "Input root must be BPMN definitions");
        Element process = directChildById(definitions, "process", INPUT_PROCESS_ID);
        require("true".equals(process.getAttribute("isExecutable")),
            "Input process transfer-lifecycle must be executable");
        for (ScreeningProcess screening : SCREENINGS) {
            for (String nodeId : screening.extractedNodeIds()) {
                directChildById(process, nodeId);
            }
            for (String flowId : screening.extractedFlowIds()) {
                directChildById(process, flowId);
            }
            require(directChildById(definitions, screening.messageId()) != null,
                "Missing screening message " + screening.messageId());
        }
        for (TaskDefinition task : List.of(
            new TaskDefinition("requestKyc", "screening-request-kyc", "3"),
            new TaskDefinition("requestAml", "screening-request-aml", "3"),
            new TaskDefinition("requestSanctions", "screening-request-sanctions", "3"),
            new TaskDefinition("submitTransfer", "transfer-submit", "1")
        )) {
            Element taskElement = directChildById(process, task.id());
            Element definition = firstDescendant(taskElement, "taskDefinition");
            require(definition != null
                    && task.type().equals(definition.getAttribute("type"))
                    && task.retries().equals(definition.getAttribute("retries")),
                "Unsupported service task '" + task.id() + "': expected type=" + task.type()
                    + " and retries=" + task.retries());
        }
        for (String flowId : List.of("flowKycRequest", "flowKycRequestsJoin",
            "flowAmlRequest", "flowAmlResultJoin",
            "flowSanctionsRequest", "flowSanctionsResultJoin")) {
            directChildById(process, flowId);
        }
        for (String flowId : List.of(
            "flowKycRequest", "flowKycRequestsJoin", "flowAmlRequest", "flowAmlResultJoin",
            "flowSanctionsRequest", "flowSanctionsResultJoin"
        )) {
            Element flow = directChildById(process, flowId);
            require(!flow.getAttribute("sourceRef").isBlank() && !flow.getAttribute("targetRef").isBlank(),
                "Unsupported sequence flow '" + flowId + "': source and target are required");
        }
        for (String[] expected : new String[][] {
            {"validationGateway", "flowInvalid"},
            {"complianceGateway", "flowComplianceReject"},
            {"executionGateway", "flowExecutionHeld"},
            {"submissionGateway", "flowSubmissionUnknown"},
            {"reconciliationGateway", "flowReconciliationMismatch"}
        }) {
            Element gateway = directChildById(process, expected[0]);
            require(expected[1].equals(gateway.getAttribute("default")),
                "Unsupported BPMN default on gateway '" + expected[0] + "': expected " + expected[1]);
        }
        for (String[] expected : new String[][] {
            {"flowStartValidate", "transferReceived", "validateTransfer"},
            {"flowValidateGateway", "validateTransfer", "validationGateway"},
            {"flowInvalid", "validationGateway", "validationRejected"},
            {"flowValid", "validationGateway", "screeningFork"},
            {"flowKycRequest", "screeningFork", "requestKyc"},
            {"flowKycRequestGateway", "requestKyc", "kycRequestGateway"},
            {"flowKycRequestPublished", "kycRequestGateway", "kycScreeningRequests"},
            {"flowKycNotRequired", "kycRequestGateway", "screeningJoin"},
            {"flowKycPolicyHold", "kycRequestGateway", "kycPolicyHold"},
            {"flowKycPolicyHoldJoin", "kycPolicyHold", "screeningJoin"},
            {"flowKycRequestsJoin", "kycScreeningRequests", "screeningJoin"},
            {"flowAmlRequest", "screeningFork", "requestAml"},
            {"flowAmlRequestGateway", "requestAml", "amlRequestGateway"},
            {"flowAmlRequestPublished", "amlRequestGateway", "amlWait"},
            {"flowAmlResultJoin", "amlResult", "screeningJoin"},
            {"flowAmlTimeoutJoin", "amlTimeoutPersist", "screeningJoin"},
            {"flowAmlPolicyHoldJoin", "amlPolicyHold", "screeningJoin"},
            {"flowSanctionsRequest", "screeningFork", "requestSanctions"},
            {"flowSanctionsRequestGateway", "requestSanctions", "sanctionsRequestGateway"},
            {"flowSanctionsRequestPublished", "sanctionsRequestGateway", "sanctionsWait"},
            {"flowSanctionsResultJoin", "sanctionsResult", "screeningJoin"},
            {"flowSanctionsTimeoutJoin", "sanctionsTimeoutPersist", "screeningJoin"},
            {"flowSanctionsPolicyHoldJoin", "sanctionsPolicyHold", "screeningJoin"},
            {"flowJoinAggregate", "screeningJoin", "aggregateCompliance"},
            {"flowAggregateGateway", "aggregateCompliance", "complianceGateway"},
            {"flowComplianceReject", "complianceGateway", "complianceRejected"},
            {"flowComplianceReview", "complianceGateway", "manualReview"},
            {"flowComplianceApproved", "complianceGateway", "revalidateTransfer"},
            {"flowRevalidateGateway", "revalidateTransfer", "executionGateway"},
            {"flowExecutionHeld", "executionGateway", "executionHeld"},
            {"flowExecutionAllowed", "executionGateway", "submitTransfer"},
            {"flowSubmitResult", "submitTransfer", "submissionGateway"},
            {"flowSubmissionAccepted", "submissionGateway", "transactionResult"},
            {"flowSubmissionUnknown", "submissionGateway", "submissionUnknown"},
            {"flowReconciliationPending", "reconciliationGateway", "transactionResult"},
            {"flowResultReconcile", "transactionResult", "reconcileTransfer"},
            {"flowReconcileGateway", "reconcileTransfer", "reconciliationGateway"},
            {"flowReconciliationMismatch", "reconciliationGateway", "reconciliationHeld"},
            {"flowReconciliationMatch", "reconciliationGateway", "transferCompleted"}
        }) {
            Element flow = directChildById(process, expected[0]);
            require(expected[1].equals(flow.getAttribute("sourceRef"))
                    && expected[2].equals(flow.getAttribute("targetRef")),
                "Unsupported BPMN sequence flow '" + expected[0] + "': expected "
                    + expected[1] + " -> " + expected[2]);
        }
        for (String[] expected : new String[][] {
            {"flowInvalid", "=validationPassed = false"},
            {"flowValid", "=validationPassed = true"},
            {"flowKycRequestPublished", "=kycRequestReady = true and kycNotRequired != true"},
            {"flowKycNotRequired", "=kycNotRequired = true"},
            {"flowKycPolicyHold", "=kycRequestReady = false"},
            {"flowAmlRequestPublished", "=amlRequestReady = true"},
            {"flowAmlPolicyHold", "=amlRequestReady = false"},
            {"flowSanctionsRequestPublished", "=sanctionsRequestReady = true"},
            {"flowSanctionsPolicyHold", "=sanctionsRequestReady = false"},
            {"flowComplianceReject",
                "=complianceOutcome = \"REJECTED\" or complianceOutcome = \"HOLD\" or complianceOutcome = \"RESTRICTED\""},
            {"flowComplianceReview", "=complianceOutcome = \"MANUAL_REVIEW\""},
            {"flowComplianceApproved", "=complianceOutcome = \"APPROVED\""},
            {"flowExecutionHeld", "=executionAllowed = false"},
            {"flowExecutionAllowed", "=executionAllowed = true"},
            {"flowSubmissionAccepted", "=submissionOutcome = \"SUBMITTED\""},
            {"flowSubmissionUnknown", "=submissionOutcome != \"SUBMITTED\""},
            {"flowReconciliationPending", "=reconciliationPending = true"},
            {"flowReconciliationMismatch", "=reconciliationMatched = false"},
            {"flowReconciliationMatch", "=reconciliationMatched = true"}
        }) {
            require(expected[1].equals(text(directChildById(process, expected[0]), "conditionExpression")),
                "Unsupported BPMN condition on sequence flow '" + expected[0] + "'");
        }
        validateOutgoingFlowIds(document, "validateTransfer", Set.of("flowValidateGateway"));
        validateOutgoingFlowIds(document, "validationGateway", Set.of("flowInvalid", "flowValid"));
        validateOutgoingFlowIds(
            document, "screeningFork", Set.of("flowKycRequest", "flowAmlRequest", "flowSanctionsRequest")
        );
        validateOutgoingFlowIds(
            document, "kycRequestGateway",
            Set.of("flowKycRequestPublished", "flowKycNotRequired", "flowKycPolicyHold")
        );
        validateOutgoingFlowIds(
            document, "amlRequestGateway", Set.of("flowAmlRequestPublished", "flowAmlPolicyHold")
        );
        validateOutgoingFlowIds(document, "amlWait", Set.of("flowAmlResult", "flowAmlTimeout"));
        validateOutgoingFlowIds(
            document, "sanctionsRequestGateway",
            Set.of("flowSanctionsRequestPublished", "flowSanctionsPolicyHold")
        );
        validateOutgoingFlowIds(
            document, "sanctionsWait", Set.of("flowSanctionsResult", "flowSanctionsTimeout")
        );
        validateOutgoingFlowIds(document, "screeningJoin", Set.of("flowJoinAggregate"));
        validateOutgoingFlowIds(
            document, "complianceGateway",
            Set.of("flowComplianceReject", "flowComplianceReview", "flowComplianceApproved")
        );
        validateOutgoingFlowIds(
            document, "executionGateway", Set.of("flowExecutionHeld", "flowExecutionAllowed")
        );
        validateOutgoingFlowIds(
            document, "submissionGateway", Set.of("flowSubmissionAccepted", "flowSubmissionUnknown")
        );
        validateOutgoingFlowIds(
            document, "reconciliationGateway",
            Set.of("flowReconciliationPending", "flowReconciliationMismatch", "flowReconciliationMatch")
        );
        validateOutgoingFlowIds(
            document, "kycSubjectWait", Set.of("flowKycSubjectResult", "flowKycSubjectTimeout")
        );
        validateOutgoingFlowIds(
            document, "cancellationGateway", Set.of("flowCancellationAccepted", "flowCancellationRejected")
        );
        validateMessageSubscription(definitions, "KycScreeningResult", "=kycScreeningRequestId");
        validateMessageSubscription(definitions, "AmlScreeningResult", "=amlCorrelationKey");
        validateMessageSubscription(definitions, "SanctionsScreeningResult", "=sanctionsCorrelationKey");
        require("PT15M".equals(text(elementById(document, "kycTimeout"), "timeDuration"))
                && "PT15M".equals(text(elementById(document, "amlTimeout"), "timeDuration"))
                && "PT15M".equals(text(elementById(document, "sanctionsTimeout"), "timeDuration")),
            "Unsupported BPMN timer: screening waits must remain PT15M");
        require(firstDescendant(directChildById(process, "kycScreeningRequests"),
                "loopCharacteristics") != null,
            "Unsupported KYC screening structure: its subject loop must be retained");
        validateUniqueIds(document, "Input BPMN");
        validateSequenceFlowReferences(document, "Input BPMN");
    }

    private void validateOutgoingFlowIds(Document document, String sourceId, Set<String> expectedFlowIds) {
        Set<String> actualFlowIds = new HashSet<>();
        NodeList flows = document.getElementsByTagNameNS(BPMN, "sequenceFlow");
        for (int index = 0; index < flows.getLength(); index++) {
            Element flow = (Element) flows.item(index);
            if (sourceId.equals(flow.getAttribute("sourceRef"))) {
                actualFlowIds.add(flow.getAttribute("id"));
            }
        }
        require(actualFlowIds.equals(expectedFlowIds),
            "Unsupported outgoing sequence flows from BPMN element '" + sourceId + "'");
    }

    private void validateMessageSubscription(Element definitions, String messageName, String correlationKey) {
        Element message = null;
        for (Element candidate : directChildren(definitions, "message")) {
            if (messageName.equals(candidate.getAttribute("name"))) {
                message = candidate;
                break;
            }
        }
        require(message != null, "Unsupported screening message: " + messageName + " is missing");
        Element subscription = firstDescendant(message, "subscription");
        require(subscription != null && correlationKey.equals(subscription.getAttribute("correlationKey")),
            "Unsupported screening message correlation for " + messageName);
    }

    private String text(Element parent, String descendantName) {
        Element child = firstDescendant(parent, descendantName);
        return child == null ? "" : child.getTextContent().trim();
    }

    private void validateGenerated(Map<String, byte[]> files) throws Exception {
        Map<String, Document> documents = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (entry.getKey().endsWith(".bpmn")) {
                documents.put(entry.getKey(), parse(entry.getValue()));
            }
        }
        Document parent = documents.get("transfer-lifecycle-source.bpmn");
        for (ScreeningProcess screening : SCREENINGS) {
            Element call = elementById(parent, screening.callId());
            require(call != null, "Generated parent is missing Call Activity " + screening.callId());
            Element calledElement = firstDescendant(call, "calledElement");
            require(calledElement != null && screening.processId().equals(calledElement.getAttribute("processId")),
                "Generated Call Activity " + screening.callId() + " has an invalid target process ID");
            Element mapping = firstDescendant(call, "ioMapping");
            require(mapping != null, "Generated Call Activity " + screening.callId() + " has no variable mappings");
            require(mappingTargets(mapping, "input").equals(java.util.Set.copyOf(screening.inputs())),
                "Generated Call Activity " + screening.callId() + " maps undeclared input variables");
            require(mappingTargets(mapping, "output").equals(java.util.Set.copyOf(screening.outputs())),
                "Generated Call Activity " + screening.callId() + " maps undeclared output variables");
            for (String variable : screening.inputs()) {
                require(hasMapping(mapping, "input", variable),
                    "Generated Call Activity " + screening.callId() + " is missing input " + variable);
            }
            for (String variable : screening.outputs()) {
                require(hasMapping(mapping, "output", variable),
                    "Generated Call Activity " + screening.callId() + " is missing output " + variable);
            }
            Document child = documents.get(screening.fileName());
            Element childProcess = child == null ? null : elementById(child, screening.processId());
            require(childProcess != null && "true".equals(childProcess.getAttribute("isExecutable")),
                "Generated called process " + screening.processId() + " is missing or non-executable");
        }
        for (Map.Entry<String, Document> entry : documents.entrySet()) {
            validateUniqueIds(entry.getValue(), entry.getKey());
            validateSequenceFlowReferences(entry.getValue(), entry.getKey());
        }
        Element parentProcess = elementById(parent, INPUT_PROCESS_ID);
        require(parentProcess != null && elementById(parent, "submitTransfer") != null
                && elementById(parent, "reconcileTransfer") != null,
            "Generated parent is missing payment or reconciliation paths");
        require("1".equals(firstDescendant(elementById(parent, "submitTransfer"), "taskDefinition")
                .getAttribute("retries")),
            "Generated payment submission must retain retries=1");
        Element recovery = elementById(parent, "recoverUnknownSubmission");
        require(recovery != null
                && "transfer-recover-unknown".equals(firstDescendant(recovery, "taskDefinition")
                    .getAttribute("type")),
            "Generated parent is missing instruction-keyed unknown-submission recovery");
        require("transactionResult".equals(elementById(parent, "flowUnknownRecoveryHash")
                .getAttribute("targetRef"))
                && "submissionUnknown".equals(elementById(parent, "flowUnknownRecoveryHold")
                    .getAttribute("targetRef")),
            "Unknown payment recovery must reconcile only a journaled hash and hold otherwise");
    }

    private void validateCamundaModels(Map<String, byte[]> files) {
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (!entry.getKey().endsWith(".bpmn")) {
                continue;
            }
            try (ByteArrayInputStream model = new ByteArrayInputStream(entry.getValue())) {
                Bpmn.validateModel(Bpmn.readModelFromStream(model));
            } catch (Exception invalidModel) {
                throw new GenerationException(
                    "Camunda BPMN model validation failed for " + entry.getKey() + ": "
                        + invalidModel.getMessage(),
                    invalidModel
                );
            }
        }
    }

    private boolean hasMapping(Element mapping, String localName, String variable) {
        for (Element child : directChildren(mapping, localName)) {
            if (variable.equals(child.getAttribute("target"))
                && ("=" + variable).equals(child.getAttribute("source"))) {
                return true;
            }
        }
        return false;
    }

    private java.util.Set<String> mappingTargets(Element mapping, String localName) {
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        for (Element child : directChildren(mapping, localName)) {
            targets.add(child.getAttribute("target"));
        }
        return targets;
    }

    private void validateUniqueIds(Document document, String label) {
        Map<String, Element> ids = new LinkedHashMap<>();
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String id = element.getAttribute("id");
            if (!id.isBlank()) {
                require(ids.putIfAbsent(id, element) == null, label + " has duplicate XML ID '" + id + "'");
            }
        }
    }

    private void validateSequenceFlowReferences(Document document, String label) {
        NodeList flows = document.getElementsByTagNameNS(BPMN, "sequenceFlow");
        for (int index = 0; index < flows.getLength(); index++) {
            Element flow = (Element) flows.item(index);
            require(elementById(document, flow.getAttribute("sourceRef")) != null,
                label + " sequence flow '" + flow.getAttribute("id") + "' has an unknown source");
            require(elementById(document, flow.getAttribute("targetRef")) != null,
                label + " sequence flow '" + flow.getAttribute("id") + "' has an unknown target");
        }
    }

    private byte[] successReport(String inputName, Document source) {
        List<String> generatedNames = new ArrayList<>();
        generatedNames.add("transfer-lifecycle-source.bpmn");
        SCREENINGS.forEach(screening -> generatedNames.add(screening.fileName()));
        StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"schemaVersion\": \"1.0.0\",\n")
            .append("  \"status\": \"PASSED\",\n")
            .append("  \"inputModel\": \"").append(jsonEscape(inputName)).append("\",\n")
            .append("  \"generatedArtifacts\": ").append(jsonArray(generatedNames)).append(",\n")
            .append("  \"extractedReusableProcesses\": [\n");
        for (int index = 0; index < SCREENINGS.size(); index++) {
            ScreeningProcess screening = SCREENINGS.get(index);
            json.append("    {\"processId\":\"").append(screening.processId())
                .append("\",\"artifact\":\"").append(screening.fileName())
                .append("\",\"capability\":\"").append(screening.type())
                .append("\",\"contractVersion\":\"1.0.0\"}");
            json.append(index == SCREENINGS.size() - 1 ? "\n" : ",\n");
        }
        json.append("  ],\n")
            .append("  \"validation\": {\"status\":\"PASSED\",\"errors\":[],\"warnings\":[],\"structures\":{")
            .append("\"serviceTasks\":").append(count(source, "serviceTask")).append(',')
            .append("\"gateways\":").append(count(source, "exclusiveGateway") + count(source, "parallelGateway")
                + count(source, "eventBasedGateway")).append(',')
            .append("\"messageEvents\":").append(count(source, "messageEventDefinition")).append(',')
            .append("\"timers\":").append(count(source, "timerEventDefinition")).append(',')
            .append("\"subprocesses\":").append(count(source, "subProcess"))
            .append("}}}\n");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int count(Document document, String localName) {
        return document.getElementsByTagNameNS(BPMN, localName).getLength();
    }

    private byte[] failureReport(String inputName, String message) {
        String json = "{\n"
            + "  \"schemaVersion\": \"1.0.0\",\n"
            + "  \"status\": \"FAILED\",\n"
            + "  \"inputModel\": \"" + jsonEscape(inputName) + "\",\n"
            + "  \"generatedArtifacts\": [],\n"
            + "  \"extractedReusableProcesses\": [],\n"
            + "  \"validation\": {\"status\":\"FAILED\",\"errors\":[\""
            + jsonEscape(message) + "\"],\"warnings\":[]}\n"
            + "}\n";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String jsonArray(List<String> entries) {
        return entries.stream().map(value -> "\"" + jsonEscape(value) + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n")
            .replace("\r", "\\r").replace("\t", "\\t");
    }

    private void publish(Path output, Map<String, byte[]> files) throws IOException {
        Path parent = output.getParent();
        if (parent == null) {
            throw new IOException("Generated output must have a parent directory");
        }
        Files.createDirectories(parent);
        String suffix = UUID.randomUUID().toString();
        Path stage = parent.resolve(output.getFileName() + ".stage-" + suffix);
        Path backup = parent.resolve(output.getFileName() + ".backup-" + suffix);
        Files.createDirectory(stage);
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Files.write(stage.resolve(file.getKey()), file.getValue());
            }
            boolean previousMoved = false;
            if (Files.exists(output)) {
                move(output, backup);
                previousMoved = true;
            }
            try {
                move(stage, output);
            } catch (IOException publishFailure) {
                if (previousMoved && Files.exists(backup)) {
                    move(backup, output);
                }
                throw publishFailure;
            }
            if (previousMoved) {
                deleteTree(backup);
            }
        } finally {
            if (Files.exists(stage)) {
                deleteTree(stage);
            }
        }
    }

    private void writeFailureReport(Path output, String inputName, String message) throws IOException {
        Files.createDirectories(output);
        Path temporary = output.resolve(REPORT_NAME + ".tmp-" + UUID.randomUUID());
        Files.write(temporary, failureReport(inputName, message));
        replace(temporary, output.resolve(REPORT_NAME));
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private void replace(Path source, Path target) throws IOException {
        try {
            Files.move(
                source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder.parse(new ByteArrayInputStream(xml));
    }

    private Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().newDocument();
    }

    private byte[] serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toByteArray();
    }

    private Element directChildById(Element parent, String id) {
        for (Element child : directChildren(parent, null)) {
            if (id.equals(child.getAttribute("id"))) {
                return child;
            }
        }
        throw new GenerationException("Unsupported BPMN structure: required element '" + id + "' is missing");
    }

    private Element directChildById(Element parent, String localName, String id) {
        Element element = directChildById(parent, id);
        require(localName.equals(element.getLocalName()),
            "Element '" + id + "' must be a BPMN " + localName);
        return element;
    }

    private Element elementById(Document document, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private Element firstDescendant(Element parent, String localName) {
        NodeList elements = parent.getElementsByTagNameNS("*", localName);
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element
                && (localName == null || localName.equals(element.getLocalName()))) {
                result.add(element);
            }
        }
        return result;
    }

    private Element directChildByLocalName(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        return children.isEmpty() ? null : children.get(0);
    }

    private Element firstSequenceFlow(Element process) {
        for (Element child : directChildren(process, null)) {
            if ("sequenceFlow".equals(child.getLocalName())) {
                return child;
            }
        }
        throw new GenerationException("Unsupported BPMN structure: process has no sequence flows");
    }

    private void removeDirectChildById(Element parent, String id) {
        Element child = directChildById(parent, id);
        parent.removeChild(child);
    }

    private void removeElementsByName(Element parent, String localName) {
        List<Element> remove = directChildren(parent, localName);
        remove.forEach(parent::removeChild);
    }

    private void copyAttributes(Element source, Element target) {
        NamedNodeMap attributes = source.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (attribute.getNamespaceURI() == null) {
                target.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            } else {
                target.setAttributeNS(attribute.getNamespaceURI(), attribute.getNodeName(), attribute.getNodeValue());
            }
        }
    }

    private String actionableMessage(Exception failure) {
        if (failure instanceof SAXParseException parseFailure) {
            return "Invalid BPMN XML at line " + parseFailure.getLineNumber()
                + ", column " + parseFailure.getColumnNumber() + ": " + parseFailure.getMessage();
        }
        if (failure instanceof GenerationException) {
            return failure.getMessage();
        }
        if (failure instanceof IOException) {
            return "Unable to read or publish BPMN artifacts: " + failure.getMessage();
        }
        return "BPMN generation failed: " + failure.getMessage();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new GenerationException(message);
        }
    }

    private record TaskDefinition(String id, String type, String retries) {
    }

    private record ScreeningProcess(
        String type,
        String processId,
        String fileName,
        String messageId,
        List<String> extractedNodeIds,
        List<String> extractedFlowIds,
        List<String> terminalFlowIds,
        String firstNodeId,
        String inboundFlowId,
        String outboundFlowId,
        String callId,
        List<String> inputs,
        List<String> outputs
    ) {
        private String endId() {
            return type.toLowerCase(java.util.Locale.ROOT) + "End";
        }
    }

    public static final class GenerationException extends RuntimeException {
        public GenerationException(String message) {
            super(message);
        }

        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package ca.jahed.rtpoet.papyrusrt

import ca.jahed.rtpoet.papyrusrt.rts.PapyrusRTLibrary
import ca.jahed.rtpoet.rtmodel.*
import ca.jahed.rtpoet.rtmodel.cppproperties.*
import ca.jahed.rtpoet.rtmodel.rts.RTSystemSignal
import ca.jahed.rtpoet.rtmodel.rts.classes.RTSystemClass
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTSystemProtocol
import ca.jahed.rtpoet.rtmodel.sm.*
import ca.jahed.rtpoet.rtmodel.types.RTType
import ca.jahed.rtpoet.rtmodel.types.primitivetype.RTPrimitiveType
import ca.jahed.rtpoet.rtmodel.visitors.RTCachedVisitor
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EcoreFactory
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.papyrusrt.codegen.cpp.profile.RTCppProperties.*
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.PortRegistrationType
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.RTMessageKind
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.UMLRealTimeFactory
import org.eclipse.papyrusrt.umlrt.profile.statemachine.UMLRTStateMachines.UMLRTStateMachinesFactory
import org.eclipse.uml2.uml.*
import java.io.File

class PapyrusRTWriter private constructor(private val resource: Resource) : RTCachedVisitor() {

    private constructor(file: String) : this(PapyrusRTLibrary.createResourceSet()
        .createResource(URI.createFileURI(File(file).absolutePath)))

    companion object {
        @JvmStatic
        fun write(file: String, model: RTModel) {
            PapyrusRTWriter(file).write(model, true)
        }

        @JvmStatic
        fun write(resource: Resource, model: RTModel) {
            PapyrusRTWriter(resource).write(model)
        }
    }

    private fun write(model: RTModel, save: Boolean = false) {
        val umlModel = visit(model) as Model
        resource.contents.add(umlModel)

        if (save) {
            resource.save(null)
        }
    }

    override fun visitModel(model: RTModel): Model {
        val umlModel = UMLFactory.eINSTANCE.createModel()
        umlModel.name = model.name

        val topAnnotation = EcoreFactory.eINSTANCE.createEAnnotation()
        topAnnotation.source = "UMLRT_Default_top"
        topAnnotation.details.put("top_name", model.top.capsule.name)
        umlModel.eAnnotations.add(topAnnotation)

        val langAnnotation = EcoreFactory.eINSTANCE.createEAnnotation()
        langAnnotation.source = "http://www.eclipse.org/papyrus-rt/language/1.0.0"
        langAnnotation.details.put("language", "umlrt-cpp")
        umlModel.eAnnotations.add(langAnnotation)

        umlModel.applyProfile(PapyrusRTLibrary.getProfile("UMLRealTime"))
        umlModel.applyProfile(PapyrusRTLibrary.getProfile("UMLRTStateMachines"))
        umlModel.applyProfile(PapyrusRTLibrary.getProfile("RTCppProperties"))

        model.capsules.forEach { umlModel.packagedElements.add(visit(it) as Class) }
        model.classes.forEach { umlModel.packagedElements.add(visit(it) as Class) }
        model.protocols.forEach { umlModel.packagedElements.add(visit(it) as Package) }
        model.enumerations.forEach { umlModel.packagedElements.add(visit(it) as Enumeration) }
        model.artifacts.forEach { umlModel.packagedElements.add(visit(it) as Artifact) }
        model.packages.forEach { umlModel.packagedElements.add(visit(it) as Package) }
        return umlModel
    }

    override fun visitPackage(pkg: RTPackage): Package {
        val umlPackage = UMLFactory.eINSTANCE.createPackage()
        umlPackage.name = pkg.name

        pkg.capsules.forEach { umlPackage.packagedElements.add(visit(it) as Class) }
        pkg.classes.forEach { umlPackage.packagedElements.add(visit(it) as Class) }
        pkg.protocols.forEach { umlPackage.packagedElements.add(visit(it) as Package) }
        pkg.enumerations.forEach { umlPackage.packagedElements.add(visit(it) as Enumeration) }
        pkg.artifacts.forEach { umlPackage.packagedElements.add(visit(it) as Artifact) }
        pkg.packages.forEach { umlPackage.packagedElements.add(visit(it) as Package) }
        return umlPackage
    }

    override fun visitCapsule(capsule: RTCapsule): Class {
        val umlClass = UMLFactory.eINSTANCE.createClass()
        umlClass.name = capsule.name

        capsule.parts.forEach { umlClass.ownedAttributes.add(visit(it) as Property) }
        capsule.ports.forEach { umlClass.ownedPorts.add(visit(it) as Port) }
        capsule.connectors.forEach { umlClass.ownedConnectors.add(visit(it) as Connector) }
        capsule.attributes.forEach { umlClass.ownedAttributes.add(visit(it) as Property) }
        capsule.operations.forEach { umlClass.ownedOperations.add(visit(it) as Operation) }

        umlClass.ownedOperations.forEach { umlClass.ownedBehaviors.add(it.methods[0]) }

        if (capsule.stateMachine != null)
            umlClass.ownedBehaviors.add(visit(capsule.stateMachine!!) as StateMachine)

        if (capsule.properties != null) {
            val props = visit(capsule.properties!!) as CapsuleProperties
            props.base_Class = umlClass
            resource.contents.add(props)
        }

        val umlrtCapsule = UMLRealTimeFactory.eINSTANCE.createCapsule()
        umlrtCapsule.base_Class = umlClass
        resource.contents.add(umlrtCapsule)
        return umlClass
    }

    override fun visitClass(klass: RTClass): Class {
        if (klass is RTSystemClass)
            return PapyrusRTLibrary.getSystemClass(klass)

        val umlClass = UMLFactory.eINSTANCE.createClass()
        umlClass.name = klass.name
        klass.attributes.forEach { umlClass.ownedAttributes.add(visit(it) as Property) }
        klass.operations.forEach { umlClass.ownedOperations.add(visit(it) as Operation) }
        umlClass.ownedOperations.forEach { umlClass.ownedBehaviors.add(it.methods[0]) }

        if (klass.properties != null) {
            val props = visit(klass.properties!!) as PassiveClassProperties
            props.base_Class = umlClass
            resource.contents.add(props)
        }

        return umlClass
    }

    override fun visitProtocol(protocol: RTProtocol): Package {
        if (protocol is RTSystemProtocol)
            return PapyrusRTLibrary.getProtocol(protocol).base_Package

        val umlPackage = UMLFactory.eINSTANCE.createPackage()
        umlPackage.name = protocol.name

        val umlCollaboration = UMLFactory.eINSTANCE.createCollaboration()
        umlCollaboration.name = protocol.name
        umlPackage.packagedElements.add(umlCollaboration)

        val i1 = UMLFactory.eINSTANCE.createInterface()
        i1.name = protocol.name
        umlPackage.packagedElements.add(i1)

        protocol.inputSignals.forEach { i1.ownedOperations.add((visit(it) as CallEvent).operation) }

        val i2 = UMLFactory.eINSTANCE.createInterface()
        i2.name = protocol.name + "~"
        umlPackage.packagedElements.add(i2)

        protocol.outputSignals.forEach { i2.ownedOperations.add((visit(it) as CallEvent).operation) }

        val i3 = UMLFactory.eINSTANCE.createInterface()
        i3.name = protocol.name + "IO"
        umlPackage.packagedElements.add(i3)

        protocol.inOutSignals.filterNot { it is RTSystemSignal }
            .forEach { i3.ownedOperations.add((visit(it) as CallEvent).operation) }

        val ir1 = UMLFactory.eINSTANCE.createInterfaceRealization()
        ir1.clients.add(umlCollaboration)
        ir1.suppliers.add(i1)
        ir1.contract = i1
        umlCollaboration.interfaceRealizations.add(ir1)

        val ir2 = UMLFactory.eINSTANCE.createInterfaceRealization()
        ir2.clients.add(umlCollaboration)
        ir2.suppliers.add(i3)
        ir2.contract = i3
        umlCollaboration.interfaceRealizations.add(ir2)

        val u1 = UMLFactory.eINSTANCE.createUsage()
        u1.clients.add(umlCollaboration)
        u1.suppliers.add(i2)
        umlPackage.packagedElements.add(u1)

        val u2 = UMLFactory.eINSTANCE.createUsage()
        u2.clients.add(umlCollaboration)
        u2.suppliers.add(i3)
        umlPackage.packagedElements.add(u2)

        protocol.inputSignals.forEach { umlPackage.packagedElements.add(visit(it) as MessageEvent) }
        protocol.outputSignals.forEach { umlPackage.packagedElements.add(visit(it) as MessageEvent) }
        protocol.inOutSignals.filterNot { it is RTSystemSignal }
            .forEach { umlPackage.packagedElements.add(visit(it) as MessageEvent) }
        umlPackage.packagedElements.add(visit(protocol.anySignal) as MessageEvent)

        val rtp = UMLRealTimeFactory.eINSTANCE.createProtocol()
        rtp.base_Collaboration = umlCollaboration
        resource.contents.add(rtp)

        val rtm1 = UMLRealTimeFactory.eINSTANCE.createRTMessageSet()
        rtm1.rtMsgKind = RTMessageKind.IN
        rtm1.base_Interface = i1
        resource.contents.add(rtm1)

        val rtm2 = UMLRealTimeFactory.eINSTANCE.createRTMessageSet()
        rtm2.rtMsgKind = RTMessageKind.OUT
        rtm2.base_Interface = i2
        resource.contents.add(rtm2)

        val rtm3 = UMLRealTimeFactory.eINSTANCE.createRTMessageSet()
        rtm3.rtMsgKind = RTMessageKind.IN_OUT
        rtm3.base_Interface = i3
        resource.contents.add(rtm3)

        val umlrtProtocolContainer = UMLRealTimeFactory.eINSTANCE.createProtocolContainer()
        umlrtProtocolContainer.base_Package = umlPackage
        resource.contents.add(umlrtProtocolContainer)
        return umlPackage
    }

    override fun visitEnumeration(enumeration: RTEnumeration): Enumeration {
        val umlEnumeration = UMLFactory.eINSTANCE.createEnumeration()
        umlEnumeration.name = enumeration.name

        enumeration.literals.forEach {
            val literal = UMLFactory.eINSTANCE.createEnumerationLiteral()
            literal.name = it
            umlEnumeration.ownedLiterals.add(literal)
        }

        if (enumeration.properties != null) {
            val props = visit(enumeration.properties!!) as EnumerationProperties
            props.base_Enumeration = umlEnumeration
            resource.contents.add(props)
        }

        return umlEnumeration
    }

    override fun visitPart(part: RTCapsulePart): Property {
        val umlProperty = UMLFactory.eINSTANCE.createProperty()
        umlProperty.name = part.name
        umlProperty.upper = part.replication
        umlProperty.type = visit(part.capsule) as Class

        umlProperty.aggregation = if (part.plugin) AggregationKind.SHARED_LITERAL else AggregationKind.COMPOSITE_LITERAL
        umlProperty.lower = if (part.optional || part.plugin) 0 else part.replication

        val umlrtCapsulePart = UMLRealTimeFactory.eINSTANCE.createCapsulePart()
        umlrtCapsulePart.base_Property = umlProperty
        resource.contents.add(umlrtCapsulePart)
        return umlProperty
    }

    override fun visitPort(port: RTPort): Port {
        val umlPort = UMLFactory.eINSTANCE.createPort()
        umlPort.name = port.name
        umlPort.setIsBehavior(port.behaviour)
        umlPort.setIsConjugated(port.conjugated)
        umlPort.setIsService(port.service)
        umlPort.upper = port.replication
        umlPort.lower = port.replication
        umlPort.visibility = VisibilityKind.get(port.visibility.ordinal)
        umlPort.type = (visit(port.protocol) as Package).packagedElements[0] as Type

        val umlrtPort = UMLRealTimeFactory.eINSTANCE.createRTPort()
        umlrtPort.base_Port = umlPort
        umlrtPort.setIsPublish(port.publish)
        umlrtPort.setIsWired(port.wired)
        umlrtPort.setIsNotification(port.notification)
        umlrtPort.registration = PortRegistrationType.get(port.registrationType.ordinal)
        umlrtPort.registrationOverride = port.registrationOverride
        resource.contents.add(umlrtPort)
        return umlPort
    }

    override fun visitConnector(connector: RTConnector): Connector {
        val umlConnector = UMLFactory.eINSTANCE.createConnector()
        umlConnector.name = connector.name

        val umlEnd1 = UMLFactory.eINSTANCE.createConnectorEnd()
        umlEnd1.role = visit(connector.end1.port) as Port
        umlEnd1.partWithPort =
            if (connector.end1.part != null) visit(connector.end1.part!!) as Property
            else null

        val umlEnd2 = UMLFactory.eINSTANCE.createConnectorEnd()
        umlEnd2.role = visit(connector.end2.port) as Port
        umlEnd2.partWithPort =
            if (connector.end2.part != null) visit(connector.end2.part!!) as Property
            else null

        umlConnector.ends.add(umlEnd1)
        umlConnector.ends.add(umlEnd2)

        val umlrtConnector = UMLRealTimeFactory.eINSTANCE.createRTConnector()
        umlrtConnector.base_Connector = umlConnector
        resource.contents.add(umlrtConnector)
        return umlConnector
    }

    override fun visitSignal(signal: RTSignal): MessageEvent {
        if (signal is RTSystemSignal)
            return PapyrusRTLibrary.getSystemSignal(signal)

        if (signal.isAny) {
            val c = UMLFactory.eINSTANCE.createAnyReceiveEvent()
            c.name = signal.name
            return c
        }

        val umlCallEvent = UMLFactory.eINSTANCE.createCallEvent()
        umlCallEvent.name = signal.name

        umlCallEvent.operation = UMLFactory.eINSTANCE.createOperation()
        umlCallEvent.operation.name = signal.name
        signal.parameters.forEach { umlCallEvent.operation.ownedParameters.add(visit(it) as Parameter) }
        return umlCallEvent
    }

    override fun visitArtifact(artifact: RTArtifact): Artifact {
        val umlArtifact = UMLFactory.eINSTANCE.createArtifact()
        umlArtifact.name = umlArtifact.name
        umlArtifact.fileName = umlArtifact.fileName

        if (artifact.properties != null) {
            val props = visit(artifact.properties!!) as ArtifactProperties
            props.base_Artifact = umlArtifact
            resource.contents.add(props)
        }

        return umlArtifact
    }

    override fun visitAttribute(attribute: RTAttribute): Property {
        val umlProperty = UMLFactory.eINSTANCE.createProperty()
        umlProperty.name = attribute.name
        umlProperty.type = visit(attribute.type) as Type
        umlProperty.upper = attribute.replication
        umlProperty.lower = attribute.replication
        umlProperty.visibility = VisibilityKind.get(attribute.visibility.ordinal)

        if (attribute.properties != null) {
            val props = visit(attribute.properties!!) as AttributeProperties
            props.base_Property = umlProperty
            resource.contents.add(props)
        }

        return umlProperty
    }

    override fun visitAction(action: RTAction): OpaqueBehavior {
        val umlOpaqueBehaviour = UMLFactory.eINSTANCE.createOpaqueBehavior()
        umlOpaqueBehaviour.name = action.name
        umlOpaqueBehaviour.bodies.add(action.body)
        umlOpaqueBehaviour.languages.add(action.language)
        return umlOpaqueBehaviour
    }

    override fun visitOperation(operation: RTOperation): Operation {
        val umlOperation = UMLFactory.eINSTANCE.createOperation()
        umlOperation.name = operation.name
        operation.parameters.forEach { umlOperation.ownedParameters.add(visit(it) as Parameter) }

        if (operation.ret != null) {
            val ret = visit(operation.ret!!) as Parameter
            ret.direction = ParameterDirectionKind.RETURN_LITERAL
            umlOperation.ownedParameters.add(ret)
        }

        if (operation.action != null)
            umlOperation.methods.add(visit(operation.action!!) as OpaqueBehavior)

        if (operation.properties != null) {
            val props = visit(operation.properties!!) as OperationProperties
            props.base_Operation = umlOperation
            resource.contents.add(props)
        }

        return umlOperation
    }

    override fun visitParameter(param: RTParameter): Parameter {
        val umlParameter = UMLFactory.eINSTANCE.createParameter()
        umlParameter.name = param.name
        umlParameter.type = visit(param.type) as Type
        umlParameter.lower = param.replication
        umlParameter.upper = param.replication
        umlParameter.direction = ParameterDirectionKind.IN_LITERAL

        if (param.properties != null) {
            val props = visit(param.properties!!) as ParameterProperties
            props.base_Parameter = umlParameter
            resource.contents.add(props)
        }

        return umlParameter
    }

    override fun visitType(type: RTType): Type {
        return when (type) {
            is RTPrimitiveType -> PapyrusRTLibrary.getType(type)
            is RTEnumeration -> visit(type) as Type
            is RTCapsule -> visit(type) as Type
            is RTClass -> visit(type) as Type
            else -> throw RuntimeException("Unexpected type class ${type.javaClass.simpleName}")
        }
    }

    override fun visitStateMachine(statemachine: RTStateMachine): StateMachine {
        val umlStateMachine = UMLFactory.eINSTANCE.createStateMachine()
        umlStateMachine.name = statemachine.name

        val umlRegion = UMLFactory.eINSTANCE.createRegion()
        statemachine.states().forEach { umlRegion.subvertices.add(visit(it) as Vertex) }
        statemachine.transitions().forEach { umlRegion.transitions.add(visit(it) as Transition) }
        umlStateMachine.regions.add(umlRegion)

        val umlrtRegion = UMLRTStateMachinesFactory.eINSTANCE.createRTRegion()
        umlrtRegion.base_Region = umlRegion
        resource.contents.add(umlrtRegion)

        val umlrtStateMachine = UMLRTStateMachinesFactory.eINSTANCE.createRTStateMachine()
        umlrtStateMachine.base_StateMachine = umlStateMachine
        resource.contents.add(umlrtStateMachine)
        return umlStateMachine
    }

    override fun visitCompositeState(state: RTCompositeState): State {
        val umlState = UMLFactory.eINSTANCE.createState()
        umlState.name = state.name

        umlState.entry = if (state.entryAction != null) visit(state.entryAction!!) as OpaqueBehavior else null
        umlState.exit = if (state.exitAction != null) visit(state.exitAction!!) as OpaqueBehavior else null

        val umlRegion = UMLFactory.eINSTANCE.createRegion()
        state.states().forEach { umlRegion.subvertices.add(visit(it) as Vertex) }
        state.transitions().forEach { umlRegion.transitions.add(visit(it) as Transition) }
        umlState.regions.add(umlRegion)

        val umlrtRegion = UMLRTStateMachinesFactory.eINSTANCE.createRTRegion()
        umlrtRegion.base_Region = umlRegion
        resource.contents.add(umlrtRegion)

        state.states().filter {
            it is RTPseudoState
                    && (it.kind == RTPseudoState.Kind.ENTRY_POINT || it.kind == RTPseudoState.Kind.EXIT_POINT)
        }
            .forEach { umlState.connectionPoints.add(visit(it) as Pseudostate) }

        val umlrtState = UMLRTStateMachinesFactory.eINSTANCE.createRTState()
        resource.contents.add(umlrtState)
        umlrtState.base_State = umlState
        return umlState
    }

    override fun visitPseudoState(state: RTPseudoState): Pseudostate {
        val umlState = UMLFactory.eINSTANCE.createPseudostate()
        umlState.name = state.name
        umlState.kind = when (state.kind) {
            RTPseudoState.Kind.INITIAL -> PseudostateKind.INITIAL_LITERAL
            RTPseudoState.Kind.HISTORY -> PseudostateKind.SHALLOW_HISTORY_LITERAL
            RTPseudoState.Kind.JOIN -> PseudostateKind.JOIN_LITERAL
            RTPseudoState.Kind.JUNCTION -> PseudostateKind.JUNCTION_LITERAL
            RTPseudoState.Kind.CHOICE -> PseudostateKind.CHOICE_LITERAL
            RTPseudoState.Kind.ENTRY_POINT -> PseudostateKind.ENTRY_POINT_LITERAL
            RTPseudoState.Kind.EXIT_POINT -> PseudostateKind.EXIT_POINT_LITERAL
            else -> throw RuntimeException("Unknown pseudosate kind ${state.kind}")
        }

        val umlrtState = UMLRTStateMachinesFactory.eINSTANCE.createRTPseudostate()
        umlrtState.base_Pseudostate = umlState
        resource.contents.add(umlrtState)
        return umlState
    }

    override fun visitState(state: RTState): State {
        val umlState = UMLFactory.eINSTANCE.createState()
        umlState.name = state.name

        umlState.entry = if (state.entryAction != null) visit(state.entryAction!!) as OpaqueBehavior else null
        umlState.exit = if (state.exitAction != null) visit(state.exitAction!!) as OpaqueBehavior else null

        val umlrtState = UMLRTStateMachinesFactory.eINSTANCE.createRTState()
        umlrtState.base_State = umlState
        resource.contents.add(umlrtState)
        return umlState
    }

    override fun visitTransition(transition: RTTransition): Transition {
        val umlTransition = UMLFactory.eINSTANCE.createTransition()
        umlTransition.name = transition.name

        umlTransition.source = visit(transition.source) as Vertex
        umlTransition.target = visit(transition.target) as Vertex
        umlTransition.effect = if (transition.action != null) visit(transition.action!!) as OpaqueBehavior else null

        if (transition.guard != null) {
            val guard = transition.guard!!
            val umlExpression = UMLFactory.eINSTANCE.createOpaqueExpression()
            umlExpression.name = guard.name
            umlExpression.languages.add(guard.language)
            umlExpression.bodies.add(guard.body)

            val umlConstraint = UMLFactory.eINSTANCE.createConstraint()
            umlConstraint.specification = umlExpression
            umlTransition.guard = umlConstraint
        }

        transition.triggers.forEach { umlTransition.triggers.add(visit(it) as Trigger) }
        return umlTransition
    }

    override fun visitTrigger(trigger: RTTrigger): Trigger {
        val umlTrigger = UMLFactory.eINSTANCE.createTrigger()
        umlTrigger.name = trigger.name
        umlTrigger.event = visit(trigger.signal) as MessageEvent
        trigger.ports.forEach { umlTrigger.ports.add(visit(it) as Port) }
        return umlTrigger
    }

    override fun visitArtifactProperties(props: RTArtifactProperties): ArtifactProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createArtifactProperties()
        umlProps.includeFile = props.includeFile
        umlProps.sourceFile = props.sourceFile
        return umlProps
    }

    override fun visitAttributeProperties(props: RTAttributeProperties): AttributeProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createAttributeProperties()
        umlProps.isPointsToConstType = props.pointsToConstType
        umlProps.isPointsToType = props.pointsToType
        umlProps.isPointsToVolatileType = props.pointsToVolatileType
        umlProps.isVolatile = props.isVolatile
        umlProps.size = props.size
        umlProps.type = props.type

        if (props.kind != null) umlProps.kind = AttributeKind.get(props.kind!!.ordinal)
        if (props.initialization != null)
            umlProps.initialization = InitializationKind.get(props.initialization!!.ordinal)

        return umlProps
    }

    override fun visitCapsuleProperties(props: RTCapsuleProperties): CapsuleProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createCapsuleProperties()
        umlProps.headerPreface = props.headerPreface
        umlProps.headerEnding = props.headerEnding
        umlProps.implementationPreface = props.implementationPreface
        umlProps.implementationEnding = props.implementationEnding
        umlProps.publicDeclarations = props.publicDeclarations
        umlProps.privateDeclarations = props.privateDeclarations
        umlProps.protectedDeclarations = props.protectedDeclarations
        umlProps.isGenerateHeader = props.generateHeader
        umlProps.isGenerateImplementation = props.generateImplementation
        return umlProps
    }

    override fun visitClassProperties(props: RTClassProperties): ClassProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createPassiveClassProperties()
        umlProps.headerPreface = props.headerPreface
        umlProps.headerEnding = props.headerEnding
        umlProps.implementationPreface = props.implementationPreface
        umlProps.implementationEnding = props.implementationEnding
        umlProps.publicDeclarations = props.publicDeclarations
        umlProps.privateDeclarations = props.privateDeclarations
        umlProps.protectedDeclarations = props.protectedDeclarations
        umlProps.implementationType = props.implementationType
        umlProps.isGenerate = props.generate
        umlProps.isGenerateHeader = props.generateHeader
        umlProps.isGenerateImplementation = props.generateImplementation
        umlProps.isGenerateStateMachine = props.generateStateMachine
        umlProps.isGenerateAssignmentOperator = props.generateAssignmentOperator
        umlProps.isGenerateEqualityOperator = props.generateEqualityOperator
        umlProps.isGenerateInequalityOperator = props.generateInequalityOperator
        umlProps.isGenerateInsertionOperator = props.generateInsertionOperator
        umlProps.isGenerateExtractionOperator = props.generateExtractionOperator
        umlProps.isGenerateCopyConstructor = props.generateCopyConstructor
        umlProps.isGenerateDefaultConstructor = props.generateDefaultConstructor
        umlProps.isGenerateDestructor = props.generateDestructor
        if (props.kind != null) umlProps.kind = ClassKind.get(props.kind!!.ordinal)
        return umlProps
    }

    override fun visitEnumerationProperties(props: RTEnumerationProperties): EnumerationProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createEnumerationProperties()
        umlProps.headerPreface = props.headerPreface
        umlProps.headerEnding = props.headerEnding
        umlProps.implementationPreface = props.implementationPreface
        umlProps.implementationEnding = props.implementationEnding
        umlProps.isGenerate = props.generate
        return umlProps
    }

    override fun visitOperationProperties(props: RTOperationProperties): OperationProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createOperationProperties()
        umlProps.isGenerateDefinition = props.generateDefinition
        umlProps.isInline = props.inline
        umlProps.isPolymorphic = props.polymorphic
        if (props.kind != null) umlProps.kind = OperationKind.get(props.kind!!.ordinal)
        return umlProps
    }

    override fun visitParameterProperties(props: RTParameterProperties): ParameterProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createParameterProperties()
        umlProps.type = props.type
        umlProps.isPointsToConst = props.pointsToConst
        umlProps.isPointsToVolatile = props.pointsToVolatile
        umlProps.isPointsToType = props.pointsToType
        return umlProps
    }

    override fun visitTypeProperties(props: RTTypeProperties): TypeProperties {
        val umlProps = RTCppPropertiesFactory.eINSTANCE.createTypeProperties()
        umlProps.name = props.name
        umlProps.definitionFile = props.definitionFile
        return umlProps
    }
}
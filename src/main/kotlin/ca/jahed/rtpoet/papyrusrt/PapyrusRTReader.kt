package ca.jahed.rtpoet.papyrusrt

import ca.jahed.rtpoet.papyrusrt.rts.PapyrusRTLibrary
import ca.jahed.rtpoet.papyrusrt.utils.EMFUtils
import ca.jahed.rtpoet.papyrusrt.utils.PapyrusRTUtils
import ca.jahed.rtpoet.rtmodel.*
import ca.jahed.rtpoet.rtmodel.cppproperties.*
import ca.jahed.rtpoet.rtmodel.sm.*
import ca.jahed.rtpoet.rtmodel.types.RTType
import ca.jahed.rtpoet.rtmodel.values.*
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.papyrusrt.codegen.cpp.profile.RTCppProperties.*
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.PortRegistrationType
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.RTMessageKind
import org.eclipse.uml2.uml.*
import java.io.File

class PapyrusRTReader constructor(private var resourceSet: ResourceSet) {
    private val content = mutableMapOf<EClassifier, MutableList<EObject>>()
    private val cache = mutableMapOf<EObject, Any>()
    private val dependencies = mutableSetOf<Resource>()
    private var currentResource: Resource? = null

    constructor() : this(PapyrusRTLibrary.createResourceSet())

    init {
        EcoreUtil.resolveAll(resourceSet)

        resourceSet.resources.forEach { res ->
            res.allContents.forEach {
                content.getOrPut(it.eClass(), { mutableListOf() }).add(it)
            }
        }
    }

    companion object {
        @JvmStatic
        fun read(file: String): RTModel {
            return PapyrusRTReader().readModel(file)
        }

        @JvmStatic
        fun read(resource: Resource): RTModel {
            return PapyrusRTReader(resource.resourceSet).readModel(resource)
        }
    }

    fun readModel(file: String): RTModel {
        return read(resourceSet.getResource(URI.createFileURI(File(file).absolutePath), true))
    }

    fun readModel(resource: Resource): RTModel {
        if (resource.resourceSet != resourceSet)
            throw RuntimeException("Resource ${resource.uri} not in resource set")
        currentResource = resource
        dependencies.clear()

        val model = EMFUtils.getObjectByType(resource.contents, UMLPackage.Literals.MODEL) as Model
        val rtModel = visit(model) as RTModel

        val deps = dependencies.toMutableList()
        val imports = mutableSetOf<RTModel>()
        deps.forEach { imports.add(readModel(it)) }
        rtModel.imports.addAll(imports)
        return rtModel
    }

    private fun visit(eObj: EObject): Any {
        return cache.getOrPut(eObj) {
            if (eObj.eResource().uri.isFile && !eObj.eResource().equals(currentResource))
                dependencies.add(eObj.eResource())

            when (eObj) {
                is Model -> visitModel(eObj)
                is Artifact -> visitArtifact(eObj)
                is Enumeration -> visitEnumeration(eObj)
                is CallEvent -> visitCallEvent(eObj)
                is Connector -> visitConnector(eObj)
                is ConnectorEnd -> visitConnectorEnd(eObj)
                is Operation -> visitOperation(eObj)
                is Parameter -> visitParameter(eObj)
                is OpaqueBehavior -> visitOpaqueBehavior(eObj)
                is OpaqueExpression -> visitOpaqueExpression(eObj)
                is Vertex -> visitVertex(eObj)
                is Transition -> visitTransition(eObj)
                is Trigger -> visitTrigger(eObj)
                is StateMachine -> visitStateMachine(eObj)

                is Class -> {
                    if (PapyrusRTUtils.isCapsule(eObj)) visitCapsule(eObj)
                    else visitClass(eObj)
                }
                is Package -> {
                    if (PapyrusRTUtils.isProtocol(eObj)) visitProtocol(eObj)
                    else visitPackage(eObj)
                }
                is Property -> {
                    if (PapyrusRTUtils.isCapsulePart(eObj)) visitCapsulePart(eObj)
                    else if (PapyrusRTUtils.isPort(eObj)) visitPort(eObj as Port)
                    else visitAttribute(eObj)
                }

                is Type -> visitType(eObj)
                is ValueSpecification -> visitValueSpecification(eObj)

                is ArtifactProperties -> visitArtifactProperties(eObj)
                is AttributeProperties -> visitAttributeProperties(eObj)
                is CapsuleProperties -> visitCapsuleProperties(eObj)
                is PassiveClassProperties -> visitPassiveClassProperties(eObj)
                is EnumerationProperties -> visitEnumerationProperties(eObj)
                is OperationProperties -> visitOperationProperties(eObj)
                is ParameterProperties -> visitParameterProperties(eObj)
                is TypeProperties -> visitTypeProperties(eObj)

                else -> throw RuntimeException("Unexpected element type ${eObj.eClass().name}")
            }
        }
    }

    private fun visitModel(model: Model): RTModel {
        val topName = model.getEAnnotation("UMLRT_Default_top")?.details?.get("top_name") ?: "Top"
        val topClass = EMFUtils.getObjectByType(
            content,
            UMLPackage.Literals.CLASS, mapOf(Pair("name", topName))
        )

        val builder =
            if (topClass != null && topClass.eResource() == model.eResource())
                RTModel.builder(model.name, visit(topClass) as RTCapsule)
            else RTModel.builder(model.name)

        model.packagedElements.forEach {
            when (it) {
                is Artifact -> builder.artifact(visit(it) as RTArtifact)
                is Enumeration -> builder.enumeration(visit(it) as RTEnumeration)
                is Class -> if (PapyrusRTUtils.isCapsule(it)) builder.capsule(visit(it) as RTCapsule)
                else builder.klass(visit(it) as RTClass)
                is Package -> if (PapyrusRTUtils.isProtocol(it)) builder.protocol(visit(it) as RTProtocol)
                else builder.pkg(visit(it) as RTPackage)
            }
        }
        return builder.build()
    }

    private fun visitPackage(pkg: Package): RTPackage {
        val builder = RTPackage.builder(pkg.name)
        pkg.packagedElements.forEach {
            when (it) {
                is Artifact -> builder.artifact(visit(it) as RTArtifact)
                is Enumeration -> builder.enumeration(visit(it) as RTEnumeration)
                is Class -> if (PapyrusRTUtils.isCapsule(it)) builder.capsule(visit(it) as RTCapsule)
                else builder.klass(visit(it) as RTClass)
                is Package -> if (PapyrusRTUtils.isProtocol(it)) builder.protocol(visit(it) as RTProtocol)
                else builder.pkg(visit(it) as RTPackage)
            }
        }
        return builder.build()
    }

    private fun visitCapsule(capsule: Class): RTCapsule {
        val builder = RTCapsule.builder(capsule.name)
        capsule.ownedAttributes.forEach {
            if (PapyrusRTUtils.isCapsulePart(it)) builder.part(visit(it) as RTCapsulePart)
            else if (PapyrusRTUtils.isPort(it)) builder.port(visit(it) as RTPort)
            else builder.attribute(visit(it) as RTAttribute)
        }

        capsule.ownedOperations.forEach { builder.operation(visit(it) as RTOperation) }
        capsule.ownedConnectors.forEach { builder.connector(visit(it) as RTConnector) }
        capsule.ownedBehaviors.filterIsInstance<StateMachine>().forEach {
            builder.statemachine(visit(it) as RTStateMachine)
        }

        val properties = EMFUtils.getReferencingObjectByType(
            capsule.eResource().contents,
            RTCppPropertiesPackage.Literals.CAPSULE_PROPERTIES, capsule
        )
        if (properties != null) builder.properties(visit(properties) as RTCapsuleProperties)

        return builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun visitConnector(connector: Connector): RTConnector {
        return RTConnector.builder(
            visit(connector.ends[0]) as RTConnectorEnd,
            visit(connector.ends[1]) as RTConnectorEnd).build()
    }

    private fun visitConnectorEnd(connectorEnd: ConnectorEnd): RTConnectorEnd {
        val part = if (connectorEnd.partWithPort != null) visit(connectorEnd.partWithPort) as RTCapsulePart else null
        return RTConnectorEnd(visit(connectorEnd.role as Port) as RTPort, part)
    }

    private fun visitClass(klass: Class): RTClass {
        if (PapyrusRTLibrary.isSystemClass(klass))
            return PapyrusRTLibrary.getSystemClass(klass)

        val builder = RTClass.builder(klass.name)
        klass.superClasses.forEach { builder.superClass(visit(it) as RTClass) }
        klass.ownedAttributes.forEach { builder.attribute(visit(it) as RTAttribute) }
        klass.ownedOperations.forEach { builder.operation(visit(it) as RTOperation) }

        val properties = EMFUtils.getReferencingObjectByType(
            klass.eResource().contents,
            RTCppPropertiesPackage.Literals.CLASS_PROPERTIES, klass
        )
        if (properties != null) builder.properties(visit(properties) as RTClassProperties)

        return builder.build()
    }

    private fun visitArtifact(artifact: Artifact): RTArtifact {
        val builder = RTArtifact.builder(artifact.name).fileName(artifact.fileName)
        val properties = EMFUtils.getReferencingObjectByType(
            artifact.eResource().contents,
            RTCppPropertiesPackage.Literals.ARTIFACT_PROPERTIES, artifact
        )
        if (properties != null) builder.properties(visit(properties) as RTArtifactProperties)
        return builder.build()
    }

    private fun visitEnumeration(enumeration: Enumeration): RTEnumeration {
        val builder = RTEnumeration.builder(enumeration.name)
        enumeration.ownedLiterals.forEach { builder.literal(it.name) }

        val properties = EMFUtils.getReferencingObjectByType(
            enumeration.eResource().contents,
            RTCppPropertiesPackage.Literals.ENUMERATION_PROPERTIES, enumeration
        )
        if (properties != null) builder.properties(visit(properties) as RTEnumerationProperties)

        return builder.build()
    }

    private fun visitCapsulePart(capsulePart: Property): RTCapsulePart {
        val builder = RTCapsulePart.builder(capsulePart.name,
            visit(capsulePart.type) as RTCapsule)
            .replication(capsulePart.upper)

        if (capsulePart.lower == 0)
            when (capsulePart.aggregation) {
                AggregationKind.SHARED_LITERAL -> builder.plugin()
                else -> builder.optional()
            }

        return builder.build()
    }

    private fun visitAttribute(property: Property): RTAttribute {
        val builder = RTAttribute.builder(property.name, visit(property.type) as RTType).replication(property.upper)
        when (property.visibility) {
            VisibilityKind.PUBLIC_LITERAL -> builder.publicVisibility()
            VisibilityKind.PRIVATE_LITERAL -> builder.privateVisibility()
            else -> builder.protectedVisibility()
        }

        if (property.defaultValue != null) builder.value(visit(property.defaultValue) as RTValue)

        val properties = EMFUtils.getReferencingObjectByType(
            property.eResource().contents,
            RTCppPropertiesPackage.Literals.ATTRIBUTE_PROPERTIES, property
        )
        if (properties != null) builder.properties(visit(properties) as RTAttributeProperties)

        return builder.build()
    }

    private fun visitOperation(operation: Operation): RTOperation {
        val builder = RTOperation.builder(operation.name)
        when (operation.visibility) {
            VisibilityKind.PUBLIC_LITERAL -> builder.publicVisibility()
            VisibilityKind.PRIVATE_LITERAL -> builder.privateVisibility()
            else -> builder.protectedVisibility()
        }

        operation.methods.forEach { builder.action(visit(it) as RTAction) }
        operation.ownedParameters.forEach {
            when (it.direction) {
                ParameterDirectionKind.RETURN_LITERAL -> builder.ret(visit(it) as RTParameter)
                else -> builder.parameter(visit(it) as RTParameter)
            }
        }

        val properties = EMFUtils.getReferencingObjectByType(
            operation.eResource().contents,
            RTCppPropertiesPackage.Literals.OPERATION_PROPERTIES, operation
        )
        if (properties != null) builder.properties(visit(properties) as RTOperationProperties)

        return builder.build()
    }

    private fun visitParameter(parameter: Parameter): RTParameter {
        val builder = RTParameter.builder(parameter.name, visit(parameter.type) as RTType)
            .replication(parameter.upper)
        val properties = EMFUtils.getReferencingObjectByType(
            parameter.eResource().contents,
            RTCppPropertiesPackage.Literals.PARAMETER_PROPERTIES, parameter
        )
        if (properties != null) builder.properties(visit(properties) as RTParameterProperties)
        return builder.build()
    }

    private fun visitPort(port: Port): RTPort {
        val realTimePort = PapyrusRTUtils.getRealTimePort(port)!!

        val builder = RTPort
            .builder(port.name, visit(PapyrusRTUtils
                .getProtocol(port.type as Collaboration)!!.base_Package) as RTProtocol)
            .replication(port.upper)
            .registrationOverride(realTimePort.registrationOverride)

        if (port.isBehavior) builder.behaviour()
        if (port.isConjugated) builder.conjugate()
        if (port.isService) builder.service()
        if (realTimePort.isNotification) builder.notification()
        if (realTimePort.isPublish) builder.publish()
        if (realTimePort.isWired) builder.wired()

        when (port.visibility) {
            VisibilityKind.PUBLIC_LITERAL -> builder.publicVisibility()
            VisibilityKind.PRIVATE_LITERAL -> builder.privateVisibility()
            else -> builder.protectedVisibility()
        }

        when (realTimePort.registration) {
            PortRegistrationType.APPLICATION -> builder.appRegistration()
            PortRegistrationType.AUTOMATIC_LOCKED -> builder.autoLockedRegistration()
            else -> builder.autoRegistration()
        }

        return builder.build()
    }

    private fun visitType(type: Type): RTType {
        return when (type) {
            is Enumeration -> visit(type) as RTEnumeration
            is Class -> if (PapyrusRTUtils.isCapsule(type)) visit(type) as RTCapsule else visit(type) as RTClass
            is Collaboration -> visit(PapyrusRTUtils.getProtocol(type)!!.base_Package) as RTProtocol
            else -> PapyrusRTLibrary.getType(type.name)
        }
    }

    private fun visitValueSpecification(value: ValueSpecification): Any {
        return when (value) {
            is LiteralBoolean -> RTLiteralBoolean(value.isValue)
            is LiteralInteger -> RTLiteralInteger(value.value)
            is LiteralString -> RTLiteralString(value.value)
            is LiteralReal -> RTLiteralReal(value.value)
            is LiteralNull -> RTLiteralNull
            is LiteralUnlimitedNatural -> RTLiteralUnlimitedNatural
            is OpaqueExpression -> RTExpression(visit(value) as RTAction)
            else -> throw RuntimeException("Unknown ValueSpecification class ${value::class.java.simpleName}")
        }
    }

    private fun visitProtocol(protocol: Package): RTProtocol {
        val protocolContainer = PapyrusRTUtils.getProtocol(protocol)!!
        if (PapyrusRTLibrary.isSystemProtocol(protocolContainer))
            return PapyrusRTLibrary.getSystemProtocol(protocolContainer)

        val builder = RTProtocol.builder(protocol.name)
        val operationMap = mutableMapOf<Operation, RTSignal>()
        protocol.packagedElements.filterIsInstance<CallEvent>().forEach {
            operationMap[it.operation] = visit(it) as RTSignal
        }

        protocol.packagedElements.filterIsInstance<Interface>().forEach { iface ->
            val messageSet = PapyrusRTUtils.getMessageSet(iface)
            iface.ownedOperations.forEach {
                when (messageSet!!.rtMsgKind) {
                    RTMessageKind.IN -> builder.input(operationMap[it]!!)
                    RTMessageKind.OUT -> builder.output(operationMap[it]!!)
                    else -> builder.inOut(operationMap[it]!!)
                }
            }
        }

        return builder.build()
    }

    private fun visitCallEvent(callEvent: CallEvent): RTSignal {
        if (PapyrusRTLibrary.isSystemSignal(callEvent))
            return PapyrusRTLibrary.getSystemSignal(callEvent)

        val operation = callEvent.operation!!
        val builder = RTSignal.builder(operation.name)
        operation.ownedParameters.forEach { builder.parameter(visit(it) as RTParameter) }
        return builder.build()
    }


    private fun visitOpaqueBehavior(behavior: OpaqueBehavior): RTAction {
        return RTAction.builder(behavior.bodies.getOrNull(0)?.toString()).build()
    }

    private fun visitOpaqueExpression(expression: OpaqueExpression): RTAction {
        return RTAction.builder(expression.bodies.getOrNull(0)?.toString()).build()
    }

    private fun visitStateMachine(stateMachine: StateMachine): RTStateMachine {
        val builder = RTStateMachine.builder()
        stateMachine.regions[0].subvertices.forEach { builder.state(visit(it) as RTGenericState) }
        stateMachine.regions[0].transitions.forEach { builder.transition(visit(it) as RTTransition) }
        return builder.build()
    }

    private fun visitTransition(transition: Transition): RTTransition {
        val builder = RTTransition.builder(visit(transition.source) as RTGenericState,
            visit(transition.target) as RTGenericState)

        if (transition.effect != null) builder.action(visit(transition.effect) as RTAction)
        if (transition.guard != null) builder.guard(visit(transition.guard.specification) as RTAction)
        transition.triggers.forEach { builder.trigger(visit(it) as RTTrigger) }

        return builder.build()
    }

    private fun visitTrigger(trigger: Trigger): RTTrigger {
        val builder = RTTrigger.builder(visit(trigger.event) as RTSignal)
        trigger.ports.forEach { builder.port(visit(it) as RTPort) }
        return builder.build()
    }

    private fun visitVertex(vertex: Vertex): RTGenericState {
        if (vertex is Pseudostate) {
            return when (vertex.kind) {
                PseudostateKind.INITIAL_LITERAL -> RTPseudoState.initial(vertex.name).build()
                PseudostateKind.CHOICE_LITERAL -> RTPseudoState.choice(vertex.name).build()
                PseudostateKind.SHALLOW_HISTORY_LITERAL -> RTPseudoState.history(vertex.name).build()
                PseudostateKind.JOIN_LITERAL -> RTPseudoState.join(vertex.name).build()
                PseudostateKind.JUNCTION_LITERAL -> RTPseudoState.junction(vertex.name).build()
                PseudostateKind.ENTRY_POINT_LITERAL -> RTPseudoState.entryPoint(vertex.name).build()
                PseudostateKind.EXIT_POINT_LITERAL -> RTPseudoState.exitPoint(vertex.name).build()
                else -> throw RuntimeException("Unknown pseudosate kind ${vertex.kind}")
            }

        } else {
            vertex as State

            return if (vertex.regions.isEmpty()) {
                val builder = RTState.builder(vertex.name)
                if (vertex.entry != null) builder.entry(visit(vertex.entry) as RTAction)
                if (vertex.exit != null) builder.entry(visit(vertex.exit) as RTAction)
                builder.build()
            } else {
                val builder = RTCompositeState.builder(vertex.name)
                if (vertex.entry != null) builder.entry(visit(vertex.entry) as RTAction)
                if (vertex.exit != null) builder.entry(visit(vertex.exit) as RTAction)

                vertex.regions[0].subvertices.forEach { builder.state(visit(it) as RTGenericState) }
                vertex.regions[0].transitions.forEach { builder.transition(visit(it) as RTTransition) }
                vertex.connectionPoints.forEach { builder.state(visit(it) as RTGenericState) }
                builder.build()
            }
        }
    }

    private fun visitArtifactProperties(props: ArtifactProperties): RTArtifactProperties {
        return RTArtifactProperties(props.includeFile, props.sourceFile)
    }

    private fun visitAttributeProperties(props: AttributeProperties): RTAttributeProperties {
        return RTAttributeProperties(
            RTAttributeProperties.InitKind.values()[props.initialization.ordinal],
            RTAttributeProperties.Kind.values()[props.kind.ordinal],
            props.size,
            props.type,
            props.isPointsToConstType,
            props.isPointsToVolatileType,
            props.isPointsToType,
            props.isVolatile)
    }

    private fun visitCapsuleProperties(props: CapsuleProperties): RTCapsuleProperties {
        return RTCapsuleProperties(
            props.headerPreface,
            props.headerEnding,
            props.implementationPreface,
            props.implementationEnding,
            props.publicDeclarations,
            props.privateDeclarations,
            props.protectedDeclarations,
            props.isGenerateHeader,
            props.isGenerateImplementation)
    }

    private fun visitEnumerationProperties(props: EnumerationProperties): RTEnumerationProperties {
        return RTEnumerationProperties(
            props.headerPreface,
            props.headerEnding,
            props.implementationPreface,
            props.implementationEnding,
            props.isGenerate)
    }

    private fun visitOperationProperties(props: OperationProperties): RTOperationProperties {
        return RTOperationProperties(
            RTOperationProperties.OpKind.values()[props.kind.ordinal],
            props.isGenerateDefinition,
            props.isInline,
            props.isPolymorphic)
    }

    private fun visitParameterProperties(props: ParameterProperties): RTParameterProperties {
        return RTParameterProperties(
            props.type,
            props.isPointsToConst,
            props.isPointsToVolatile,
            props.isPointsToType)
    }

    private fun visitPassiveClassProperties(props: PassiveClassProperties): RTClassProperties {
        return RTClassProperties(
            RTClassProperties.ClsKind.values()[props.kind.ordinal],
            props.headerPreface,
            props.headerEnding,
            props.implementationPreface,
            props.implementationEnding,
            props.publicDeclarations,
            props.privateDeclarations,
            props.protectedDeclarations,
            props.implementationType,
            props.isGenerate,
            props.isGenerateHeader,
            props.isGenerateImplementation,
            props.isGenerateStateMachine,
            props.isGenerateAssignmentOperator,
            props.isGenerateEqualityOperator,
            props.isGenerateInequalityOperator,
            props.isGenerateInsertionOperator,
            props.isGenerateExtractionOperator,
            props.isGenerateCopyConstructor,
            props.isGenerateDefaultConstructor,
            props.isGenerateDestructor)
    }

    private fun visitTypeProperties(props: TypeProperties): RTTypeProperties {
        return RTTypeProperties(props.name, props.definitionFile)
    }
}
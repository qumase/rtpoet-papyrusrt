package ca.jahed.rtpoet.papyrusrt.rts

import ca.jahed.rtpoet.papyrusrt.rts.protocols.RTBaseCommProtocol
import ca.jahed.rtpoet.papyrusrt.rts.protocols.RTMQTTProtocol
import ca.jahed.rtpoet.papyrusrt.rts.protocols.RTTCPProtocol
import ca.jahed.rtpoet.rtmodel.rts.RTLibrary
import ca.jahed.rtpoet.rtmodel.rts.RTSystemSignal
import ca.jahed.rtpoet.rtmodel.rts.classes.*
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTFrameProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTLogProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTSystemProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTTimingProtocol
import ca.jahed.rtpoet.rtmodel.types.RTType
import ca.jahed.rtpoet.rtmodel.types.primitivetype.*
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.papyrusrt.codegen.cpp.profile.RTCppProperties.RTCppPropertiesPackage
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.ProtocolContainer
import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.UMLRealTimePackage
import org.eclipse.papyrusrt.umlrt.profile.statemachine.UMLRTStateMachines.UMLRTStateMachinesPackage
import org.eclipse.papyrusrt.umlrt.system.profile.systemelements.SystemClass
import org.eclipse.papyrusrt.umlrt.system.profile.systemelements.SystemElementsPackage
import org.eclipse.uml2.uml.*
import org.eclipse.uml2.uml.resources.util.UMLResourcesUtil
import java.net.URL

object PapyrusRTLibrary : RTLibrary {
    private val pathMap = mutableMapOf<String, URL>()
    private val profiles = mutableMapOf<String, Profile>()
    private val protocols = mutableMapOf<String, ProtocolContainer>()
    private val classes = mutableMapOf<String, Class>()
    private val types = mutableMapOf<String, PrimitiveType>()
    private val events = mutableMapOf<ProtocolContainer, MutableMap<String, MessageEvent>>()
    private val signals = mutableMapOf<RTSystemSignal, MessageEvent>()

    fun createResourceSet(): ResourceSetImpl {
        val resourceSet = ResourceSetImpl()
        init(resourceSet)
        return resourceSet
    }

    fun init(resourceSet: ResourceSet) {
        pathMap["pathmap://UMLRTRTSLIB/UMLRT-RTS.uml"] =
            javaClass.classLoader.getResource("models/UMLRT-RTS.uml")!!
        pathMap["pathmap://UML_RT_PROFILE/uml-rt.profile.uml"] =
            javaClass.classLoader.getResource("models/uml-rt.profile.uml")!!
        pathMap["pathmap://UML_RT_PROFILE/UMLRealTimeSM-addendum.profile.uml"] =
            javaClass.classLoader.getResource("models/UMLRealTimeSM-addendum.profile.uml")!!
        pathMap["pathmap://UMLRT_CPP/RTCppProperties.profile.uml"] =
            javaClass.classLoader.getResource("models/RTCppProperties.profile.uml")!!
        pathMap["pathmap://UML_LIBRARIES/UMLPrimitiveTypes.library.uml"] =
            javaClass.classLoader.getResource("models/UMLPrimitiveTypes.library.uml")!!
        pathMap["pathmap://PapyrusC_Cpp_LIBRARIES/AnsiCLibrary.uml"] =
            javaClass.classLoader.getResource("models/AnsiCLibrary.uml")!!

        UMLResourcesUtil.init(resourceSet)
        resourceSet.packageRegistry[UMLPackage.eNS_URI] = UMLPackage.eINSTANCE
        resourceSet.packageRegistry[UMLRealTimePackage.eNS_URI] = UMLRealTimePackage.eINSTANCE
        resourceSet.packageRegistry[UMLRTStateMachinesPackage.eNS_URI] = UMLRTStateMachinesPackage.eINSTANCE
        resourceSet.packageRegistry[RTCppPropertiesPackage.eNS_URI] = RTCppPropertiesPackage.eINSTANCE
        resourceSet.packageRegistry[SystemElementsPackage.eNS_URI] = SystemElementsPackage.eINSTANCE

        pathMap.forEach {
            resourceSet.uriConverter.uriMap[URI.createURI(it.key)] = URI.createURI(it.value.toString())
        }

        pathMap.keys.forEach { resourceSet.getResource(URI.createURI(it), true) }

        loadProfiles(resourceSet)
        loadClasses(resourceSet)
        loadTypes(resourceSet)
        loadProtocols(resourceSet)
    }

    private fun loadProfiles(resourceSet: ResourceSet) {
        resourceSet.resources.forEach { resource ->
            EcoreUtil.getObjectsByType<Profile>(resource.contents,
                UMLPackage.Literals.PROFILE).forEach {
                profiles[it.name] = it
            }
        }
    }

    private fun loadProtocols(resourceSet: ResourceSet) {
        resourceSet.resources.forEach { resource ->
            EcoreUtil.getObjectsByType<ProtocolContainer>(resource.contents,
                UMLRealTimePackage.Literals.PROTOCOL_CONTAINER).forEach { protocol ->
                protocols[protocol.base_Package.name] = protocol

                (protocol.base_Package.packagedElements[0] as Collaboration)
                    .`package`.packagedElements.filterIsInstance<MessageEvent>().forEach { event ->
                        when (event) {
                            is CallEvent -> events.getOrPut(protocol, { mutableMapOf() })[event.operation.name] = event
                            is AnyReceiveEvent -> events.getOrPut(protocol, { mutableMapOf() })[event.name] = event
                        }
                    }

                events.forEach { (protocol, eventMap) ->
                    eventMap.forEach { (eventName, event) ->
                        signals[getSystemSignal(protocol.base_Package.name, eventName)] = event
                    }
                }
            }
        }
    }

    private fun loadClasses(resourceSet: ResourceSet) {
        resourceSet.resources.forEach { resource ->
            EcoreUtil.getObjectsByType<SystemClass>(resource.contents,
                SystemElementsPackage.Literals.SYSTEM_CLASS).forEach {
                classes[it.base_Class.name] = it.base_Class
            }
        }
    }

    private fun loadTypes(resourceSet: ResourceSet) {
        resourceSet.resources.forEach { resource ->
            EcoreUtil.getObjectsByType<Model>(resource.contents,
                UMLPackage.Literals.MODEL).forEach { model ->
                EcoreUtil.getObjectsByType<PrimitiveType>(model.packagedElements,
                    UMLPackage.Literals.PRIMITIVE_TYPE).forEach {
                    types[it.name] = it
                }
            }
        }
    }

    override fun getProtocol(protocol: RTSystemProtocol): ProtocolContainer {
        return when (protocol) {
            is RTLogProtocol -> protocols["Log"]!!
            is RTTimingProtocol -> protocols["Timing"]!!
            is RTFrameProtocol -> protocols["Frame"]!!
            is RTTCPProtocol -> protocols["TCP"]!!
            is RTMQTTProtocol -> protocols["MQTT"]!!
            is RTBaseCommProtocol -> protocols["UMLRTBaseCommProtocol"]!!
            else -> throw RuntimeException("Unknown system protocol ${protocol.name}")
        }
    }

    override fun getSystemSignal(event: RTSystemSignal): MessageEvent {
        return signals[event]!!
    }

    override fun getSystemSignal(protocol: RTSystemProtocol, signal: RTSystemSignal): MessageEvent {
        return (events[getProtocol(protocol)]!![signal.name])!!

    }

    override fun getSystemClass(klass: RTSystemClass): Class {
        return when (klass) {
            is RTCapsuleId -> classes["UMLRTCapsuleId"]!!
            is RTMessage -> classes["UMLRTMessage"]!!
            is RTTimerId -> classes["UMLRTTimerId"]!!
            is RTTimespec -> classes["UMLRTTimespec"]!!
            else -> throw RuntimeException("Unknown system class ${klass.name}")
        }
    }

    override fun getProfile(name: String): Profile {
        return profiles[name]!!
    }

    override fun getType(type: RTType): PrimitiveType {
        return types[type.name]!!
    }

    override fun getSystemProtocol(name: String): RTSystemProtocol {
        return when (name) {
            "Log" -> RTLogProtocol
            "Timing" -> RTTimingProtocol
            "Frame" -> RTFrameProtocol
            "TCP" -> RTTCPProtocol
            "MQTT" -> RTMQTTProtocol
            "UMLRTBaseCommProtocol" -> RTBaseCommProtocol
            else -> throw RuntimeException("Unknown system protocol $name")
        }
    }

    override fun getSystemSignal(protocol: String, name: String): RTSystemSignal {
        return getSystemProtocol(protocol).inputs().find { it.name == name } as RTSystemSignal
    }

    override fun getSystemProtocol(protocol: Any): RTSystemProtocol {
        protocol as ProtocolContainer
        return getSystemProtocol(protocol.base_Package.name)
    }

    override fun getSystemClass(name: String): RTSystemClass {
        return when (name) {
            "UMLRTCapsuleId" -> RTCapsuleId
            "UMLRTMessage" -> RTMessage
            "UMLRTTimerId" -> RTTimerId
            "UMLRTTimespec" -> RTTimespec
            else -> throw RuntimeException("Unknown system class $name")
        }
    }

    override fun getType(name: String): RTPrimitiveType {
        return when (name) {
            "Boolean" -> RTBoolean
            "Integer" -> RTInteger
            "String" -> RTString
            "Real" -> RTReal
            "UnlimitedNatural" -> RTUnlimitedNatural

            "char" -> RTChar
            "double" -> RTDouble
            "float" -> RTFloat
            "bool" -> RTBool
            "int" -> RTInt
            "int8_t" -> RTInt8
            "int16_t" -> RTInt16
            "int32_t" -> RTInt32
            "int64_t" -> RTInt64
            "long" -> RTLong
            "long double" -> RTLongDouble
            "short" -> RTShort
            "unsigned char" -> RTUnsignedChar
            "unsigned int" -> RTUnsignedInt
            "uint8_t" -> RTUnsignedInt8
            "uint16_t" -> RTUnsignedInt16
            "uint32_t" -> RTUnsignedInt32
            "uint64_t" -> RTUnsignedInt64
            "unsigned long" -> RTUnsignedLong
            "unsigned short" -> RTUnsignedShort
            "wchar_t" -> RTWChar
            "void" -> RTVoid
            else -> throw RuntimeException("Unknown system type $name")
        }
    }

    override fun getSystemSignal(signal: Any): RTSystemSignal {
        signal as MessageEvent
        val eventName = if (signal is CallEvent) signal.operation.name else signal.name

        for (protocol in events.keys)
            if (events[protocol]!!.containsValue(signal))
                return getSystemSignal(protocol.base_Package.name, eventName)
        throw java.lang.RuntimeException("Unknown system signal $eventName")
    }

    override fun getSystemClass(klass: Any): RTSystemClass {
        klass as Class
        return getSystemClass(klass.name)
    }

    override fun isSystemSignal(event: Any): Boolean {
        for (protocol in events.keys)
            if (events[protocol]!!.containsValue(event))
                return true
        return false
    }

    override fun isSystemProtocol(protocol: Any): Boolean {
        return protocols.values.contains(protocol)
    }

    override fun isSystemClass(klass: Any): Boolean {
        return classes.values.contains(klass)
    }
}
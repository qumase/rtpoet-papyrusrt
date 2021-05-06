package ca.jahed.rtpoet.papyrusrt.utils

import org.eclipse.papyrusrt.umlrt.profile.UMLRealTime.*
import org.eclipse.uml2.uml.*

internal object PapyrusRTUtils {

    fun getCapsule(klass: Class): Capsule? {
        return EMFUtils.getReferencingObjectByType(klass.eResource().contents,
            UMLRealTimePackage.Literals.CAPSULE, klass) as Capsule?
    }

    fun getProtocol(pkg: Package): ProtocolContainer? {
        return EMFUtils.getReferencingObjectByType(pkg.eResource().contents,
            UMLRealTimePackage.Literals.PROTOCOL_CONTAINER, pkg) as ProtocolContainer?
    }

    fun getProtocol(collaboration: Collaboration): ProtocolContainer? {
        return getProtocol(collaboration.nearestPackage)
    }

    fun getCapsulePart(property: Property): CapsulePart? {
        return EMFUtils.getReferencingObjectByType(property.eResource().contents,
            UMLRealTimePackage.Literals.CAPSULE_PART, property) as CapsulePart?
    }

    fun getRealTimePort(port: Property): RTPort? {
        return EMFUtils.getReferencingObjectByType(port.eResource().contents,
            UMLRealTimePackage.Literals.RT_PORT, port) as RTPort?
    }

    fun getMessageSet(iface: Interface): RTMessageSet? {
        return EMFUtils.getReferencingObjectByType(iface.eResource().contents,
            UMLRealTimePackage.Literals.RT_MESSAGE_SET, iface) as RTMessageSet?
    }

    fun isCapsule(klass: Class): Boolean {
        return getCapsule(klass) != null
    }

    fun isProtocol(pkg: Package): Boolean {
        return getProtocol(pkg) != null
    }

    fun isCapsulePart(property: Property): Boolean {
        return getCapsulePart(property) != null
    }

    fun isPort(property: Property): Boolean {
        return getRealTimePort(property) != null
    }
}
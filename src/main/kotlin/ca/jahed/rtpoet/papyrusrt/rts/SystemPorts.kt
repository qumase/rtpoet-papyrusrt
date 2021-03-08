package ca.jahed.rtpoet.papyrusrt.rts

import ca.jahed.rtpoet.papyrusrt.rts.protocols.RTMQTTProtocol
import ca.jahed.rtpoet.papyrusrt.rts.protocols.RTTCPProtocol
import ca.jahed.rtpoet.rtmodel.RTPort
import ca.jahed.rtpoet.rtmodel.RTProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTFrameProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTLogProtocol
import ca.jahed.rtpoet.rtmodel.rts.protocols.RTTimingProtocol

object SystemPorts {
    @JvmStatic
    fun builder(name: String, protocol: RTProtocol): RTPort {
        return RTPort.builder(name, protocol).build()
    }

    @JvmStatic
    fun log(name: String = "log"): RTPort {
        return RTPort.builder(name, RTLogProtocol).internal().build()
    }

    @JvmStatic
    fun timing(name: String = "timing"): RTPort {
        return RTPort.builder(name, RTTimingProtocol).internal().behaviour().build()
    }

    @JvmStatic
    fun frame(name: String = "frame"): RTPort {
        return RTPort.builder(name, RTFrameProtocol).internal().behaviour().build()
    }

    @JvmStatic
    fun tcp(name: String = "tcp"): RTPort {
        return RTPort.builder(name, RTTCPProtocol).internal().behaviour().build()
    }

    @JvmStatic
    fun mqtt(name: String = "mqtt"): RTPort {
        return RTPort.builder(name, RTMQTTProtocol).internal().behaviour().build()
    }
}
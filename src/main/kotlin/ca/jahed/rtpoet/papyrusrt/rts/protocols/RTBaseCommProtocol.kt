package ca.jahed.rtpoet.papyrusrt.rts.protocols

import ca.jahed.rtpoet.rtmodel.rts.RTSystemProtocol
import ca.jahed.rtpoet.rtmodel.rts.RTSystemSignal

object RTBaseCommProtocol : RTSystemProtocol("UMLRTBaseCommProtocol") {
    private object RTBound : RTSystemSignal("rtBound")
    private object RTUnbound : RTSystemSignal("rtUnbound")

    init {
        inOutSignals.add(RTSystemSignal("rtBound"))
        inOutSignals.add(RTSystemSignal("rtUnbound"))
    }

    fun rtBound(): RTSystemSignal {
        return RTBound
    }

    fun rtUnbound(): RTSystemSignal {
        return RTUnbound
    }
}
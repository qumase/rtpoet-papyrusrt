package ca.jahed.rtpoet.papyrusrt.rts.protocols

import ca.jahed.rtpoet.papyrusrt.rts.primitivetype.RTInteger
import ca.jahed.rtpoet.papyrusrt.rts.primitivetype.RTString
import ca.jahed.rtpoet.rtmodel.RTParameter
import ca.jahed.rtpoet.rtmodel.rts.RTSystemProtocol
import ca.jahed.rtpoet.rtmodel.rts.RTSystemSignal

object RTTCPProtocol : RTSystemProtocol("TCP") {
    private object Connected : RTSystemSignal("connected")
    private object Disconnected : RTSystemSignal("disconnected")
    private object Error : RTSystemSignal("error")
    private object Received : RTSystemSignal("received")

    init {
        Disconnected.parameters.add(RTParameter.builder("errno", RTInteger).build())
        Error.parameters.add(RTParameter.builder("errno", RTInteger).build())
        Received.parameters.add(RTParameter.builder("payload", RTString).build())
        Received.parameters.add(RTParameter.builder("length", RTInteger).build())

        inputSignals.add(Connected)
        inputSignals.add(Disconnected)
        inputSignals.add(Error)
        inputSignals.add(Received)
    }

    fun connected(): RTSystemSignal {
        return Connected
    }

    fun disconnected(): RTSystemSignal {
        return Disconnected
    }

    fun error(): RTSystemSignal {
        return Error
    }

    fun received(): RTSystemSignal {
        return Received
    }
}
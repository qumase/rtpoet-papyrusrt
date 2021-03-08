import ca.jahed.rtpoet.papyrusrt.rts.SystemPorts;
import ca.jahed.rtpoet.rtmodel.*;
import ca.jahed.rtpoet.rtmodel.sm.RTPseudoState;
import ca.jahed.rtpoet.rtmodel.sm.RTState;
import ca.jahed.rtpoet.rtmodel.sm.RTStateMachine;
import ca.jahed.rtpoet.rtmodel.sm.RTTransition;
import ca.jahed.rtpoet.rtmodel.types.primitivetype.RTInt;
import org.junit.Test;

public class TestJava {

    public RTModel pingerPonger() {
        RTProtocol ppProtocol = RTProtocol.builder("PPProtocol")
                .output(RTSignal.builder("ping").parameter(RTParameter.builder("round", RTInt.INSTANCE)))
                .input(RTSignal.builder("pong").parameter(RTParameter.builder("round", RTInt.INSTANCE)))
                .build();


        RTCapsule pinger = RTCapsule.builder("Pinger")
                .attribute(RTAttribute.builder("count", RTInt.INSTANCE))
                .port(RTPort.builder("ppPort", ppProtocol).external())
                .port(SystemPorts.log("log"))
                .statemachine(
                        RTStateMachine.builder()
                                .state(RTPseudoState.initial("initial"))
                                .state(RTState.builder("playing"))
                                .transition(
                                        RTTransition.builder("initial", "playing")
                                                .action("this->count = 1;\nppPong.ping(count).send();")
                                )
                                .transition(
                                        RTTransition.builder("playing", "playing")
                                                .trigger("ppPort", "pong")
                                                .action("log.log(\"Round %d: got pong!\", round);\nppPort.ping(++count).send();")
                                )
                )
                .build();


        RTCapsule ponger = RTCapsule.builder("Ponger")
                .port(RTPort.builder("ppPort", ppProtocol).external().conjugate())
                .port(SystemPorts.log("log"))
                .statemachine(
                        RTStateMachine.builder()
                                .state(RTPseudoState.initial("initial"))
                                .state(RTState.builder("playing"))
                                .transition(RTTransition.builder("initial", "playing"))
                                .transition(
                                        RTTransition.builder("playing", "playing")
                                                .trigger("ppPort", "ping")
                                                .action("log.log(\"Round %d: got ping!\", round);\nppPort.pong(round++).send();")
                                )
                )
                .build();

        RTCapsule top = RTCapsule.builder("Top")
                .part(RTCapsulePart.builder("pinger", pinger))
                .part(RTCapsulePart.builder("ponger", ponger))
                .connector(RTConnector.builder()
                        .end1(RTConnectorEnd.builder("ppPort", "pinger"))
                        .end2(RTConnectorEnd.builder("ppPort", "ponger"))
                )
                .build();

        return RTModel.builder("PingerPonger", top)
                .capsule(pinger)
                .capsule(ponger)
                .protocol(ppProtocol)
                .build();
    }

    @Test
    public void testPingerPonger() {
        pingerPonger();
    }
}

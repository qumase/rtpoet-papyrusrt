import ca.jahed.rtpoet.generators.RTTextualModelGenerator
import ca.jahed.rtpoet.papyrusrt.PapyrusRTReader
import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter
import ca.jahed.rtpoet.papyrusrt.generators.CppCodeGenerator
import ca.jahed.rtpoet.papyrusrt.rts.PapyrusRTLibrary
import ca.jahed.rtpoet.papyrusrt.rts.SystemPorts
import ca.jahed.rtpoet.papyrusrt.rts.primitivetype.RTInt
import ca.jahed.rtpoet.rtmodel.*
import ca.jahed.rtpoet.rtmodel.sm.RTPseudoState
import ca.jahed.rtpoet.rtmodel.sm.RTState
import ca.jahed.rtpoet.rtmodel.sm.RTStateMachine
import ca.jahed.rtpoet.rtmodel.sm.RTTransition
import ca.jahed.rtpoet.utils.RTDeepCopier
import ca.jahed.rtpoet.utils.RTEqualityHelper
import ca.jahed.rtpoet.utils.RTModelValidator
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileWriter

class TestLib {

    private fun loadResourceModel(name: String): Resource {
        val url = javaClass.classLoader.getResource("models/$name")
        return PapyrusRTLibrary.createResourceSet().getResource(URI.createURI(url!!.toString()), true)!!
    }

    private fun loadFileModel(file: String): Resource {
        return PapyrusRTLibrary.createResourceSet().getResource(URI.createFileURI(File(file).absolutePath), true)!!
    }

    private fun saveModel(model: RTModel) {
        val outputDir = File("output", "${model.name}.uml")
        outputDir.mkdirs()
        PapyrusRTWriter.writeAll(outputDir.absolutePath, model)
    }

    @Test
    fun TestPingerPonger() {
        val model = loadResourceModel("PP_Basic.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/PP_Basic.uml/PP_Basic.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))

        assertTrue(CppCodeGenerator.generate(rtModel, "output"))
    }

    @Test
    fun TestMulti() {
        val model = loadResourceModel("Runner.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/Runner.uml/Runner.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))

        assertTrue(CppCodeGenerator.generate(rtModel, "output"))
    }

    @Test
    internal fun TestParcelRouter() {
        val model = loadResourceModel("ParcelRouter.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/ParcelRouter_v4.uml/ParcelRouter_v4.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))
    }

    @Test
    internal fun TestRPS() {
        val model = loadResourceModel("RPS.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/RootElement.uml/RootElement.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))
    }

    @Test
    internal fun TestBankATM() {
        val model = loadResourceModel("BankATM.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/BankATM.uml/BankATM.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))
    }

    @Test
    internal fun TestSafe() {
        val model = loadResourceModel("Safe.uml")
        val rtModel = PapyrusRTReader.read(model)
        saveModel(rtModel)

        val model2 = loadFileModel("output/Safe.uml/Safe.uml")
        val rtModel2 = PapyrusRTReader.read(model2)
        assertTrue(RTEqualityHelper.isEqual(rtModel, rtModel2))
    }

    private fun pingerPonger(): RTModel {
        val ppProtocol =
            RTProtocol.builder("PPProtocol")
                .output(RTSignal.builder("ping").parameter(RTParameter.builder("round", RTInt)))
                .input(RTSignal.builder("pong").parameter(RTParameter.builder("round", RTInt)))
                .build()

        val pinger =
            RTCapsule.builder("Pinger")
                .attribute(RTAttribute.builder("count", RTInt).value(10))
                .port(RTPort.builder("ppPort", ppProtocol).external())
                .port(SystemPorts.log())
                .statemachine(
                    RTStateMachine.builder()
                        .state(RTPseudoState.initial("init"))
                        .state(RTState.builder("playing"))
                        .transition(
                            RTTransition.builder("init", "playing")
                                .action("""
                                    ppPort.ping(count).send();
                                """)
                        )
                        .transition(
                            RTTransition.builder("playing", "playing")
                                .trigger("ppPort", "pong")
                                .action("""
                                   log.log("Round %d: got pong!", round);
                                   ppPort.ping(++count).send();
                                """)
                        )
                )
                .build()

        val ponger =
            RTCapsule.builder("Ponger")
                .port(RTPort.builder("ppPort", ppProtocol).external().conjugate())
                .port(SystemPorts.log())
                .statemachine(
                    RTStateMachine.builder()
                        .state(RTPseudoState.initial("init"))
                        .state(RTState.builder("playing"))
                        .transition(RTTransition.builder("init", "playing"))
                        .transition(
                            RTTransition.builder("playing", "playing")
                                .trigger("ppPort", "ping")
                                .action("""
                                   log.log("Round %d: got ping!", round);
                                   ppPort.pong(round++).send();
                                """)
                        )
                )
                .build()

        val top =
            RTCapsule.builder("Top")
                .part(RTCapsulePart.builder("pinger", pinger))
                .part(RTCapsulePart.builder("ponger", ponger))
                .connector(RTConnector.builder()
                    .end1(RTConnectorEnd.builder("ppPort", "pinger"))
                    .end2(RTConnectorEnd.builder("ppPort", "ponger"))
                )
                .build()

        return RTModel.builder("PingerPonger", top)
            .capsule(pinger)
            .capsule(ponger)
            .protocol(ppProtocol)
            .build()
    }

    @Test
    internal fun TestBuilder() {
        saveModel(pingerPonger())
    }

    @Test
    internal fun TestGenerator() {
        val model = loadResourceModel("ParcelRouter.uml")
        val rtModel = PapyrusRTReader.read(model)
        val writer = FileWriter(File("output", "${rtModel.name}.rt"))
        writer.write(RTTextualModelGenerator.generate(rtModel))
        writer.close()
    }

    @Test
    internal fun TestValidate() {
        val model = pingerPonger()
        val validator = RTModelValidator(model)
        assert(validator.validate())
    }

    @Test
    internal fun TestCopy() {
        val model = pingerPonger()
        RTDeepCopier().copy(model) as RTModel
    }

    @Test
    internal fun TestEquality() {
        val model = pingerPonger()
        val copy = RTDeepCopier().copy(model) as RTModel
        assertTrue(RTEqualityHelper.isEqual(model, copy))
    }
}
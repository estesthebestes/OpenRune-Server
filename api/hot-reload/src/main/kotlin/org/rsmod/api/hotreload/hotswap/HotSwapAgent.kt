package org.rsmod.api.hotreload.hotswap

import com.sun.tools.attach.VirtualMachine
import java.lang.instrument.Instrumentation
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import org.rsmod.api.hotreload.ReloadException

/**
 * Obtains a [java.lang.instrument.Instrumentation] for this JVM by attaching to itself. Needs the
 * server to run with `-Djdk.attach.allowAttachSelf=true`, which `gradlew run` sets.
 */
public object HotSwapAgent {
    @Volatile private var instrumentation: Instrumentation? = null

    @JvmStatic
    public fun agentmain(@Suppress("UNUSED_PARAMETER") args: String?, inst: Instrumentation) {
        instrumentation = inst
    }

    @Synchronized
    public fun instrumentation(): Instrumentation {
        instrumentation?.let {
            return it
        }
        if (System.getProperty("jdk.attach.allowAttachSelf") != "true") {
            throw ReloadException(
                "Hot swap needs -Djdk.attach.allowAttachSelf=true (start the server with gradlew run)."
            )
        }
        val jar = Files.createTempFile("rsmod-hotswap-agent", ".jar")
        jar.toFile().deleteOnExit()
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Agent-Class")] = HotSwapAgent::class.java.name
        manifest.mainAttributes[Attributes.Name("Can-Redefine-Classes")] = "true"
        manifest.mainAttributes[Attributes.Name("Can-Retransform-Classes")] = "true"
        JarOutputStream(Files.newOutputStream(jar), manifest).close()
        val vm = VirtualMachine.attach(ProcessHandle.current().pid().toString())
        try {
            vm.loadAgent(jar.toAbsolutePath().toString())
        } finally {
            vm.detach()
        }
        return instrumentation ?: throw ReloadException("The hot swap agent did not start.")
    }

    /** JetBrains Runtime with `-XX:+AllowEnhancedClassRedefinition` can also add members. */
    public val supportsStructuralChanges: Boolean
        get() =
            java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.any {
                it == "-XX:+AllowEnhancedClassRedefinition"
            }
}

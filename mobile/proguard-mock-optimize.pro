# Opt-in Mock candidate only. Manifest entry points and Android XML callbacks are
# covered by AGP's generated rules; dependency consumer rules remain enabled.
# Keep retrace source/line information and generic/annotation metadata used by
# Koog typeToken/Kotlin serialization. Do not disable optimization or obfuscation.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# SLF4J 2 loads this exact installed implementation through ServiceLoader.
# Preserve the no-op provider: losing it must not enable an alternative logger.
-keep class org.slf4j.nop.NOPServiceProvider { public <init>(); *; }

# Ktor JVM's optional IntelliJ debugger detector probes these JVM-only types.
# Inspected ktor-utils-jvm 3.3.3 bytecode wraps the probe in catch(Throwable)
# and returns false on Android. No application/protocol path needs MXBean.
# These are the only missing types reported by the first candidate R8 build.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# kotlinx-serialization-core ships its own serializer/Companion consumer rules.
# App data uses explicit JSON codecs, not reflective field-name serialization.
# App tools use explicit ToolDescriptor schemas, not @Tool annotated reflection.
# No blanket app/library keep and no global dontwarn: unresolved classes fail
# the candidate build and must be investigated rather than suppressed.

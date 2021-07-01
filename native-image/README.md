# GraalVM
JDK 8/11/16 + performance oriented + native image build capability

# Why ?
Improve JVM based app for Cloud => better startup (scaling) / memory consumption => cheaper price

Lot of JAVA Web frameworks already provide support (compatible code / maven plugin)
- Spring-boot
- Quarkus
- Micronaut

# Fast startup / execution ?
- No classloading
- No interpreted code
- Simple GC
- No JIT

# Less memory how ?
- No metadata for loaded classes
- No profiling / JIT / debug / interpreter code ...

# Some constraints
- Good for small app (low heap) => the GC is really simple
- Avoid reflection or provide config (ex: picocli)
- Not 100% compatible with all libraries (ex: keycloak / AWS v1) improve its compatibility every day. Sometimes it's the libraries that need to be changed not GraalVM
- Generated binary is massive (xxMB) => size can be reduced https://upx.github.io/
- Generation of the native-image cost time and memory => better use github actions

# Personal experience
- Building the native command required some digging graalvm/libs docs / options ... Changes your code adding default constructors, remove some private members, reflection omg no ... it's another layer of work
- Your JAR may work perfectly but the native image will failed at runtime for some weird reasons => google pain ....
- Need to test every use cases of the app
- Provide a fallback mechanism that will still create a native file with the JAR embedded inside and required a JVM to start. Could be an appropriate solution in some cases

# Demo with ferload-client-cli
Build clean JAR and execute it
```shell
sbt clean assembly && java -jar target/scala-2.13/ferload-client-0.1.jar download -m data/m1.tsv -o data
```
Build native image from JAR and execute it
```shell
sh native-image/build.sh && ./ferload-client download -m data/m1.tsv -o data
```
Content of the native-image/build.sh
```sh
sbt clean assembly

java -cp target/scala-2.13/ferload-client-0.1.jar:native-image/picocli-4.6.1.jar:native-image/picocli-codegen-4.6.1.jar picocli.codegen.aot.graalvm.ReflectionConfigGenerator ca.ferlab.ferload.client.Main --factory ca.ferlab.ferload.client.commands.factory.CommandFactory > reflection-config.json

native-image --enable-http --enable-https --no-fallback --allow-incomplete-classpath -H:IncludeResources=application.conf -H:ReflectionConfigurationFiles=reflection-config.json -H:+ReportUnsupportedElementsAtRuntime --static --no-server -jar target/scala-2.13/ferload-client-0.1.jar ferload-client
```
## options
**--enable-http/s** (part of the feature on demand mechanism) specify yes my app use this protocol

**--no-fallback** don't allow fallback native creation (binary that contains the JAR and call the JVM installed) --force-fallback can be used to always build this kind of binary

**--allow-incomplete-classpath** allow un-resolved type at build-time, really common when the JAR dependencies tree is big but you only use part of it

**-H:IncludeResources=application.conf** all the src/main/resources you want, it's a regex

**-H:ReflectionConfigurationFiles=reflection-config.json** the reflection configuration if needed

**--static** include libc :)

**--no-server** native-image start by default a server to build the binary, we don't care just build it I'm in local

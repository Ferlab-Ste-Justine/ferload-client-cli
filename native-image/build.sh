sbt clean assembly
java -cp target/scala-2.13/ferload-client-0.1.jar:native-image/picocli-4.6.1.jar:native-image/picocli-codegen-4.6.1.jar picocli.codegen.aot.graalvm.ReflectionConfigGenerator ca.ferlab.ferload.client.Main --factory ca.ferlab.ferload.client.commands.factory.CommandFactory > reflection-config.json
native-image --enable-http --enable-https --no-fallback --allow-incomplete-classpath -H:IncludeResources=application.conf -H:ReflectionConfigurationFiles=reflection-config.json --static --no-server -jar target/scala-2.13/ferload-client-0.1.jar ferload-client

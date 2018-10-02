#!/bin/sh

# Stolen from https://github.com/square/misk/blob/master/misk-grpc-tests/build-wire.sh

# TODO(mmihic): Totally temporary script to build wire files. To be replaces with
# proper gradle support
# Build protos captured in test resources
java -jar ~/Downloads/wire-compiler-2.3.0-RC1-jar-with-dependencies.jar \
	--proto_path=src/main/kotlin/misk/digester/proto/ \
	--java_out=src/main/kotlin/ \
	tdigest.proto
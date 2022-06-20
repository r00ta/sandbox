#!/bin/bash

########
# Run Fleet Shard locally in dev mode
#
########

SCRIPT_DIR_PATH=`dirname "${BASH_SOURCE[0]}"`

export MANAGED_KAFKA_INSTANCE_NAME=rhose-local-development

. "${SCRIPT_DIR_PATH}/configure.sh" kafka

mvn \
  -Dminikubeip=kind-control-plane \
  -Dquarkus.http.port=1337 \
  -Devent-bridge.logging.json=false \
  -Pminikube \
  -f "${SCRIPT_DIR_PATH}/../../shard-operator/pom.xml" \
  clean compile quarkus:dev $@

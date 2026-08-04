#!/bin/sh
cd "$(dirname "$0")/.."
exec mvn -f develop/backend/pom.xml spring-boot:run

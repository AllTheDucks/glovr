#!/bin/bash

read -p "Username? " USERNAME
read -p "Key File? " KEYFILE

./gradlew uploadArchives "-PuserName=$USERNAME" "-PkeyPath=$KEYFILE"












#!/bin/bash

read -p "Username? " USERNAME
read -p "Key File (absolute path)? " KEYPATH

./gradlew uploadArchives "-PuserName=$USERNAME" "-PkeyPath=$KEYPATH"












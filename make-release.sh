#!/bin/bash

# Pass in release name, no prefixed `v`.
# Make sure that git flow is configured with a version tag prefix of `v`.

export RELEASE_OR_HOTFIX=$1
VERSION=$2

if [[ $RELEASE_OR_HOTFIX == "release" || ($RELEASE_OR_HOTFIX == "hotfix") ]]
then
    echo "You have selected $RELEASE_OR_HOTFIX"
else
    echo "usage: You must specify one of release or hotfix. eg $0 release 3.0.0"
    exit -1
fi

if [ -z $VERSION ]
then
    echo "usage: Pass in release or hotfix name, no prefixed 'v'."
    exit -1
fi

export JAVA_HOME=/Applications/Android\ Studio.app/Contents/jre/jdk/Contents/Home

set -x
set -e

# confirm that git flow is present:
git flow version

# confirm that working directory has no untracked stuff:
git diff-index --quiet HEAD --

# make sure remote is refresh:
git fetch origin

git checkout master

# destroy master state!
git reset --hard origin/master

git checkout develop

# destroy develop state!
git reset --hard origin/develop

echo "Making $RELEASE_OR_HOTFIX branch for: $VERSION"

if [ $RELEASE_OR_HOTFIX == "hotfix" ]
then
  echo "Assuming hotfix branch already exists."
  git checkout hotfix/$VERSION
  echo "Verifying SDK."
  DEPLOY_MAVEN_PATH=`pwd`/maven ./gradlew clean test
else
  git checkout develop
  echo "Verifying SDK."
  DEPLOY_MAVEN_PATH=`pwd`/maven ./gradlew clean test
  git flow $RELEASE_OR_HOTFIX start $VERSION
fi

echo "Edit your version numbers (README.md and build.gradle) and press return!"
read -n 1

echo "Building SDK."
DEPLOY_MAVEN_PATH=`pwd`/maven ./gradlew clean test sdk:assembleRelease sdk:publishProductionPublicationToMavenRepository

# add the untracked files that have appeared under maven with the new bits.
git add maven

git commit -a -m "Releasing $VERSION."

git flow $RELEASE_OR_HOTFIX finish $VERSION

# move the gh-pages branch to develop to keep hosting (and allowing any pre-release builds in develop to be exposed for download).
git branch -f gh-pages develop

git push origin master
git push origin develop
git push origin gh-pages
git push origin v$VERSION

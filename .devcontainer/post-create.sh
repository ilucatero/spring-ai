#!/bin/bash
set -e

echo "========================================"
echo " DevContainer post-create"
echo "========================================"

echo "MAVEN: Check"
if command -v mvn &> /dev/null; then
    echo "Maven is already installed:"
    mvn -version
else
    echo "Maven is not installed."
    echo "Installing Maven..."

    sudo apt-get update
    sudo apt-get install -y maven

    echo "Maven installed:"
    mvn -version
fi

echo "MAVEN: Downloading Maven dependencies and prepare cache"
mvn dependency:go-offline


echo "GIT: Configure Git for consistent line endings"
git config --global core.autocrlf input
git config --global core.eol lf
git config --global core.fileMode false


echo "========================================"
echo " DevContainer initialization completed"
echo "========================================"
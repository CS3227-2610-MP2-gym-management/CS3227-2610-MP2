# GymFlow

GymFlow is a JavaFX desktop application for a small gym. It will provide separate experiences for gym owners and
gym members.

This initial version contains static previews of the login, owner home, and member home screens. Authentication,
data storage, and operational features will be added in later increments.

## Requirements

- Java SE 25

## Run locally

```shell
./gradlew run
```

On Windows, use `gradlew.bat run`.

## Test

```shell
./gradlew clean check
```

## Build platform JARs

```shell
./gradlew releaseJars
```

The generated Windows x64, Linux x64, macOS x64, and macOS ARM64 JARs are placed in `release/`. Run the JAR matching
the operating system and processor architecture:

```shell
java -jar release/GymFlow-macos-arm64.jar
```


# MC Baseball

A fully playable game of baseball for Minecraft Forge 1.20.1: build a stadium, mark the
field, pick teams, and play with humans and NPCs. Now with **Live Mode**: browse today's
real MLB games from the Field Controller and follow one live on a stadium scoreboard HUD
(see [docs/LIVE_MODE.md](docs/LIVE_MODE.md)).

## Build

```
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
./gradlew runGameTestServer
```

Requires internet on first build (Forge/Minecraft downloads). Java 17 is provisioned
automatically by Gradle if it isn't installed.

## Source history

The 0.7.0 source was recovered from the released jar (Vineflower decompile + ForgeGradle's
SRG→official mappings). The rebuilt jar matches the original's 2,897 class/field/method
signatures exactly, and the original GameTests pass on the recovered code.

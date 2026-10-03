
<center>
<img src="https://github.com/Matilabo/cobblelocke/blob/main/src/main/resources/assets/cobblelocke/textures/gui/title.png" width="900">  
</center>

A Minecraft mod!

<a href="https://ko-fi.com/matilabo"><img src="https://github.com/Matilabo/cobblelocke/blob/main/assets/icons/kofi-pixel.png" alt="Ko-fi" width="64"></a>
&nbsp;
<a href="https://modrinth.com/mod/cobblelocke-cobblemon"><img src="https://github.com/Matilabo/cobblelocke/blob/main/assets/icons/modrinth-pixel.png" alt="Modrinth" width="64"></a>
&nbsp;
<a href="https://www.curseforge.com/minecraft/mc-mods/cobblelocke"><img src="https://github.com/Matilabo/cobblelocke/blob/main/assets/icons/curseforge-pixel.png" alt="CurseForge" width="64"></a>


To report an issue, check the [Issues tab](https://github.com/Matilabo/cobblelocke/issues).

# Cobblelocke

A **Cobblemon Randomlocke** mod for Minecraft Fabric: Randomizer and Nuzlocke challenge in one mod, with one settings menu, world-wide randomization tables, and a biome event lock that forces an encounter when you step into a new biome.

Requires the **Cobblemon 1.8.1** mod. Compatible with other mods such as Mega Showdown, RCT trainers, and any mod or
datapack that adds Pokémon to Minecraft like AllTheMons and Missingmons.

## Installing

Drop `cobblelocke-<version>.jar` into `mods/` alongside:

- Fabric Loader 0.17.2 or newer
- Fabric API 0.116.6+1.21.1 or newer
- Cobblemon 1.8.1
- Java 21

The mod works on both sides. Install it on the client **and** the server: the server enforces
every rule, and the client is what draws the config menu and plays the cutscene. A client without
it can still play but cannot open the menu, and the admins can configure the run from the
console instead.

## Building

From the repository root:

```bash
./gradlew build
```

The jar is built in `build/libs/cobblelocke-<version>.jar`.

Gradle downloads its own JDK 21 into `~/.gradle/jdks` and
compiles with any `java -version` locally. Cobblemon comes from its own Maven
as a compile-only dependency.

## License

Released under the Mozilla Public License 2.0. See [LICENSE](LICENSE).

<img width="2000" height="1000" alt="image(6)" src="https://i.imgur.com/5NX2JVG.png" />

---

# API

## Gradle

Add JitPack and the API to your plugin's build:

```groovy
repositories {
    mavenCentral()
    maven { url = uri('https://repo.papermc.io/repository/maven-public/') }
    maven { url = uri('https://jitpack.io') }
}

dependencies {
    compileOnly 'com.github.sylveya.ZonePractice-Pro:ZonePracticePro-Api:2.4.0'
}
```

## Maven

- Add jitpack to repositories and ZonePractice Pro to dependencies:

```xml
 <repositories>
  <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
 </repositories>
 <dependency>
     <groupId>com.github.sylveya.ZonePractice-Pro</groupId>
     <artifactId>ZonePracticePro-Api</artifactId>
     <version>2.4.0</version>
     <scope>provided</scope>
 </dependency>
 ```

- Also make sure you have added ZonePractice Pro as a dependency in your plugin.yml:

```yml
depend: [ZonePracticePro]
```

## Usage

```java
ZonePracticeApi api = ZonePracticeApi.getInstance();
api.getPlayerDivision(player, DivisionName.FULL);
api.getElo(player, "Nodebuff");
api.getLadderWins(player, "Nodebuff", WeightClass.RANKED);
api.getLadderLosses(player, "Nodebuff", WeightClass.UNRANKED);
api.getGlobalWins(player);
api.getPlayerRankedLeft(player);
api.getPlayerUnRankedLeft(player);
api.addPlayerRanked(player, 5);
api.resetPlayerUnRanked(player);
api.getExperience(player);
api.endMatch(player, "<red>Match ended by an admin");
api.getPlayerNametag(player);
```

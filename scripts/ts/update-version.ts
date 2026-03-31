const buildFilePath = "build.gradle.kts";
const versionPattern = /const val QJS4J = "(\d+)\.(\d+)\.(\d+)"/;

const fileContent = await Deno.readTextFile(buildFilePath);
const versionMatch = fileContent.match(versionPattern);

if (versionMatch === null) {
    throw new Error("Unable to find Config.Versions.QJS4J in build.gradle.kts.");
}

const majorVersion = Number.parseInt(versionMatch[1], 10);
const minorVersion = Number.parseInt(versionMatch[2], 10);
const patchVersion = Number.parseInt(versionMatch[3], 10);

if (Number.isNaN(majorVersion) || Number.isNaN(minorVersion) || Number.isNaN(patchVersion)) {
    throw new Error("Current version is invalid.");
}

const nextPatchVersion = patchVersion + 1;
const nextVersion = `${majorVersion}.${minorVersion}.${nextPatchVersion}`;
const oldVersion = `${majorVersion}.${minorVersion}.${patchVersion}`;

const updatedContent = fileContent.replace(
    versionPattern,
    `const val QJS4J = "${nextVersion}"`,
);

await Deno.writeTextFile(buildFilePath, updatedContent);

console.log(`${oldVersion} -> ${nextVersion}`);

const buildFilePath = "build.gradle.kts";
const readmePath = "README.md";
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

const readmeContent = await Deno.readTextFile(readmePath);
const updatedReadme = readmeContent.replaceAll(
    /com\.caoccao\.qjs4j:qjs4j:\d+\.\d+\.\d+/g,
    `com.caoccao.qjs4j:qjs4j:${oldVersion}`,
).replaceAll(
    /<version>\d+\.\d+\.\d+<\/version>/g,
    `<version>${oldVersion}</version>`,
);
await Deno.writeTextFile(readmePath, updatedReadme);

console.log(`${oldVersion} -> ${nextVersion}`);

import java.io.File

val sourceCodeExts = listOf("cpp", "h", "cc", "hpp", "c")
val interestingStarts = listOf("test/libcxx/", "test/std/")
val uninterestingEnds = listOf(".fail.cpp", "version.pass.cpp", ".sh.cpp", "nothing_to_do.pass.cpp")
val badParts = listOf(
        "cuchar", "uchar", // uchar.h is not present in my libc copy
        "coroutine", // we can't do anything meaningful with it yet
        "locale.facet/facet.pass.cpp" // hangs in tests (but not in the editor), to investigate
)
val sizeThreshold = 500

val filesPlaceholder = "__FILES__"
val projectPlaceholder = "__PROJECT_NAME__"
val cmakeTemplate = """
cmake_minimum_required(VERSION 3.8)
project($projectPlaceholder)

include_directories(include)
include_directories(fuzzing)
include_directories(test/support)
include_directories(test/support/test.support)
include_directories(test/support/test.workarounds)

add_executable($projectPlaceholder
$filesPlaceholder
)

target_compile_options($projectPlaceholder PUBLIC -std=c++14 -nostdinc++)
target_compile_definitions($projectPlaceholder PUBLIC
    LIBCXX_FILESYSTEM_STATIC_TEST_ROOT="\\"\\""
    LIBCXX_FILESYSTEM_DYNAMIC_TEST_ROOT="\\"\\""
    LIBCXX_FILESYSTEM_DYNAMIC_TEST_HELPER="\\"\\""
)
"""

fun useForTesting(f: File): Boolean {
    assert(f.isAbsolute)

    if (!sourceCodeExts.contains(f.extension)) {
        return false
    }

    if (!interestingStarts.any { f.path.contains(it) }) {
        return false
    }

    if (badParts.any{ f.path.contains(it) } || uninterestingEnds.any { f.name.endsWith(it) }) {
        return false
    }

    return f.readLines().none {
        (it.contains("UNSUPPORTED:") && it.contains("c++14")) ||
        ((it.contains("REQUIRES:") || it.contains("REQUIRES-ANY:")) && !it.contains("c++14")) ||
        (it.contains("XFAIL:") && (
                it.contains("availability") ||
                it.contains("c++14") ||
                it.contains("clang") ||
                it.contains("gcc") ||
                it.contains("*")
        ))
    }
}

fun splitIntoGroups(goodFiles: List<File>, root: File): List<List<File>> {
    val groups = goodFiles.groupBy { it.relativeTo(root).path.split("/").slice(0..2).joinToString(separator = "/") }
    val list = mutableListOf<MutableList<File>>()
    for (group in groups) {
        val filesInGroup = group.value
        if (list.isEmpty() || (list.last().isNotEmpty() && list.last().size + filesInGroup.size > sizeThreshold))
            list.add(mutableListOf())
        list.last().addAll(filesInGroup)
    }
    return list
}

fun writeCMakeFile(inputFolder: File, outputFolder: File, index: Int, files: List<File>) {
    val projectName = "libcxxtests$index"
    val resultFolder = File(outputFolder, projectName)
    resultFolder.deleteRecursively()
    resultFolder.mkdirs()
    inputFolder.copyRecursively(resultFolder)
    println("Copied ${inputFolder.path} to ${resultFolder.path}")
    val text = cmakeTemplate
            .replace(projectPlaceholder, projectName)
            .replace(filesPlaceholder, files.joinToString("\n", transform = {it.relativeTo(inputFolder).path}))
    val cmakeFile = File(resultFolder, "CMakeLists.txt")
    cmakeFile.writeText(text)
    println("Written CMakeLists.txt to ${cmakeFile.path}")
}

fun main(args: Array<String>) {
    val folder = File(args[0])
    val outputFolder = File(args[1])
    val goodFiles = folder.walk().filter(::useForTesting).toList()
    val groups = splitIntoGroups(goodFiles, folder)
    groups.forEachIndexed( { index, group ->
        writeCMakeFile(folder, outputFolder, index, group)
    })
}

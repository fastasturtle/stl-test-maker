import java.io.File

val sourceCodeExts = listOf("cpp", "h", "cc", "hpp", "c")
val interestingStarts = listOf("test/libcxx/", "test/std/")
val uninterestingEnds = listOf(".fail.cpp", "version.pass.cpp", ".sh.cpp", "nothing_to_do.pass.cpp")
val badParts = listOf("cuchar", "uchar", "coroutine")

val filesPlaceholder = "__FILES__"
val cmakeTemplate = """
cmake_minimum_required(VERSION 3.8)
project(llvm__libcxx__git)

include_directories(include)
include_directories(fuzzing)
include_directories(test/support)
include_directories(test/support/test.support)
include_directories(test/support/test.workarounds)

add_executable(llvm__libcxx__git
$filesPlaceholder
)

target_compile_options(llvm__libcxx__git PUBLIC -std=c++14 -nostdinc++)
target_compile_definitions(llvm__libcxx__git PUBLIC
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

fun writeCMakeFile(folder: File, goodFiles: Sequence<File>) {
    val text = cmakeTemplate.replace(filesPlaceholder, goodFiles.joinToString("\n", transform = {it.relativeTo(folder).path}))
    val cmakeFile = File(folder, "CMakeLists.txt")
    cmakeFile.writeText(text)
}

fun main(args: Array<String>) {
    val folder = File(args[0])
    val goodFiles = folder.walk().filter(::useForTesting)
    writeCMakeFile(folder, goodFiles)
}

cmake_minimum_required(VERSION 3.22.1)
project(lumen_engine_host_native LANGUAGES CXX)

include(FetchContent)

set(STOCKFISH18_COMMIT "cb3d4ee9b47d0c5aae855b12379378ea1439675c")
FetchContent_Declare(
    stockfish18
    GIT_REPOSITORY https://github.com/official-stockfish/Stockfish.git
    GIT_TAG ${STOCKFISH18_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS TRUE
)
FetchContent_MakeAvailable(stockfish18)

set(STOCKFISH_SRC "${stockfish18_SOURCE_DIR}/src")
if(NOT EXISTS "${STOCKFISH_SRC}/uci.cpp")
    message(FATAL_ERROR "Pinned Stockfish 18 source was not populated")
endif()

function(stockfish18_ensure_net filename expected_prefix)
    set(destination "${STOCKFISH_SRC}/${filename}")
    if(EXISTS "${destination}")
        file(SHA256 "${destination}" digest)
        string(SUBSTRING "${digest}" 0 12 digest_prefix)
        if(NOT digest_prefix STREQUAL expected_prefix)
            file(REMOVE "${destination}")
        endif()
    endif()

    if(NOT EXISTS "${destination}")
        set(downloaded FALSE)
        foreach(url
            "https://tests.stockfishchess.org/api/nn/${filename}"
            "https://raw.githubusercontent.com/official-stockfish/networks/master/${filename}")
            file(DOWNLOAD "${url}" "${destination}.tmp" STATUS status TLS_VERIFY ON SHOW_PROGRESS)
            list(GET status 0 status_code)
            if(status_code EQUAL 0)
                file(SHA256 "${destination}.tmp" digest)
                string(SUBSTRING "${digest}" 0 12 digest_prefix)
                if(digest_prefix STREQUAL expected_prefix)
                    file(RENAME "${destination}.tmp" "${destination}")
                    set(downloaded TRUE)
                    break()
                endif()
            endif()
            file(REMOVE "${destination}.tmp")
        endforeach()
        if(NOT downloaded)
            message(FATAL_ERROR "Could not fetch and validate Stockfish NNUE network ${filename}")
        endif()
    endif()
endfunction()

# These are the exact default network names referenced by Stockfish 18 evaluate.h.
# Validation intentionally matches upstream scripts/net.sh: the first 12 SHA-256 hex digits
# are encoded in the filename and checked after download.
stockfish18_ensure_net("nn-c288c895ea92.nnue" "c288c895ea92")
stockfish18_ensure_net("nn-37f18f62d772.nnue" "37f18f62d772")

set(STOCKFISH_SOURCES
    ${STOCKFISH_SRC}/benchmark.cpp
    ${STOCKFISH_SRC}/bitboard.cpp
    ${STOCKFISH_SRC}/evaluate.cpp
    ${STOCKFISH_SRC}/misc.cpp
    ${STOCKFISH_SRC}/movegen.cpp
    ${STOCKFISH_SRC}/movepick.cpp
    ${STOCKFISH_SRC}/position.cpp
    ${STOCKFISH_SRC}/search.cpp
    ${STOCKFISH_SRC}/thread.cpp
    ${STOCKFISH_SRC}/timeman.cpp
    ${STOCKFISH_SRC}/tt.cpp
    ${STOCKFISH_SRC}/uci.cpp
    ${STOCKFISH_SRC}/ucioption.cpp
    ${STOCKFISH_SRC}/tune.cpp
    ${STOCKFISH_SRC}/syzygy/tbprobe.cpp
    ${STOCKFISH_SRC}/nnue/nnue_accumulator.cpp
    ${STOCKFISH_SRC}/nnue/nnue_misc.cpp
    ${STOCKFISH_SRC}/nnue/network.cpp
    ${STOCKFISH_SRC}/nnue/features/half_ka_v2_hm.cpp
    ${STOCKFISH_SRC}/nnue/features/full_threats.cpp
    ${STOCKFISH_SRC}/engine.cpp
    ${STOCKFISH_SRC}/score.cpp
    ${STOCKFISH_SRC}/memory.cpp
)

add_library(lumen_stockfish18 SHARED stockfish18_jni.cpp ${STOCKFISH_SOURCES})
target_include_directories(lumen_stockfish18 PRIVATE ${STOCKFISH_SRC})
target_compile_features(lumen_stockfish18 PRIVATE cxx_std_17)
target_compile_options(lumen_stockfish18 PRIVATE
    -O3
    -funroll-loops
    -fno-exceptions
    -Wall
    -Wcast-qual
    -Wextra
    -Wshadow
    -Wmissing-prototypes
    -Wconditional-uninitialized
    "-Wa,-I${STOCKFISH_SRC}"
)
target_compile_definitions(lumen_stockfish18 PRIVATE
    NDEBUG
    IS_64BIT
    USE_PTHREADS
    GIT_SHA=cb3d4ee9
    GIT_DATE=20260131
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(lumen_stockfish18 PRIVATE USE_POPCNT USE_NEON=8 ARCH=armv8)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_definitions(lumen_stockfish18 PRIVATE USE_SSE2 ARCH=x86-64)
    target_compile_options(lumen_stockfish18 PRIVATE -msse -msse2)
else()
    message(FATAL_ERROR "M13/M14 support only arm64-v8a and x86_64, got ${ANDROID_ABI}")
endif()

target_link_options(lumen_stockfish18 PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(lumen_stockfish18 PRIVATE atomic)

# Reckless 0.9.0 is built from the exact upstream release commit. The wrapper adds only a C ABI
# entry point around the upstream initialization/UCI loop; upstream engine modules are copied
# unchanged into the generated Cargo crate.
set(RECKLESS09_COMMIT "0e92358f5acd66e5ac77b1bf558202e47c515435")
set(RECKLESS09_NETWORK "v54-5478683c.nnue")
set(RECKLESS09_NETWORK_SHA256 "5478683cb1bababde29ae8f29468a99846726548fc6a0ed54cac40ab6d38efbf")
set(RECKLESS09_RUST_TOOLCHAIN "1.88.0")

FetchContent_Declare(
    reckless09
    GIT_REPOSITORY https://github.com/codedeliveryservice/Reckless.git
    GIT_TAG ${RECKLESS09_COMMIT}
    GIT_SHALLOW FALSE
    GIT_PROGRESS TRUE
    GIT_SUBMODULES ""
)
FetchContent_MakeAvailable(reckless09)

if(NOT EXISTS "${reckless09_SOURCE_DIR}/src/uci.rs")
    message(FATAL_ERROR "Pinned Reckless 0.9.0 source was not populated")
endif()
execute_process(
    COMMAND git rev-parse HEAD
    WORKING_DIRECTORY "${reckless09_SOURCE_DIR}"
    OUTPUT_VARIABLE RECKLESS09_RESOLVED_COMMIT
    OUTPUT_STRIP_TRAILING_WHITESPACE
    RESULT_VARIABLE RECKLESS09_GIT_STATUS
)
if(NOT RECKLESS09_GIT_STATUS EQUAL 0 OR NOT RECKLESS09_RESOLVED_COMMIT STREQUAL RECKLESS09_COMMIT)
    message(FATAL_ERROR "Reckless source provenance mismatch: expected ${RECKLESS09_COMMIT}, got ${RECKLESS09_RESOLVED_COMMIT}")
endif()

set(RECKLESS09_WRAPPER_DIR "${CMAKE_CURRENT_BINARY_DIR}/reckless09-wrapper")
file(MAKE_DIRECTORY "${RECKLESS09_WRAPPER_DIR}/src" "${RECKLESS09_WRAPPER_DIR}/build" "${RECKLESS09_WRAPPER_DIR}/networks")
file(COPY "${reckless09_SOURCE_DIR}/src/" DESTINATION "${RECKLESS09_WRAPPER_DIR}/src")
file(COPY "${reckless09_SOURCE_DIR}/build/" DESTINATION "${RECKLESS09_WRAPPER_DIR}/build")
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/../rust/reckless09-wrapper/Cargo.toml"
    "${RECKLESS09_WRAPPER_DIR}/Cargo.toml"
    COPYONLY
)
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/../rust/reckless09-wrapper/src/lib.rs"
    "${RECKLESS09_WRAPPER_DIR}/src/lib.rs"
    COPYONLY
)
configure_file("${reckless09_SOURCE_DIR}/Cargo.lock" "${RECKLESS09_WRAPPER_DIR}/Cargo.lock" COPYONLY)

set(RECKLESS09_NETWORK_PATH "${RECKLESS09_WRAPPER_DIR}/networks/${RECKLESS09_NETWORK}")
if(EXISTS "${RECKLESS09_NETWORK_PATH}")
    file(SHA256 "${RECKLESS09_NETWORK_PATH}" RECKLESS09_NETWORK_ACTUAL_SHA256)
    if(NOT RECKLESS09_NETWORK_ACTUAL_SHA256 STREQUAL RECKLESS09_NETWORK_SHA256)
        file(REMOVE "${RECKLESS09_NETWORK_PATH}")
    endif()
endif()
if(NOT EXISTS "${RECKLESS09_NETWORK_PATH}")
    file(
        DOWNLOAD
        "https://github.com/codedeliveryservice/RecklessNetworks/releases/download/networks/${RECKLESS09_NETWORK}"
        "${RECKLESS09_NETWORK_PATH}.tmp"
        EXPECTED_HASH "SHA256=${RECKLESS09_NETWORK_SHA256}"
        STATUS RECKLESS09_NETWORK_STATUS
        TLS_VERIFY ON
        SHOW_PROGRESS
    )
    list(GET RECKLESS09_NETWORK_STATUS 0 RECKLESS09_NETWORK_STATUS_CODE)
    if(NOT RECKLESS09_NETWORK_STATUS_CODE EQUAL 0)
        file(REMOVE "${RECKLESS09_NETWORK_PATH}.tmp")
        message(FATAL_ERROR "Could not fetch the pinned Reckless 0.9.0 NNUE network")
    endif()
    file(RENAME "${RECKLESS09_NETWORK_PATH}.tmp" "${RECKLESS09_NETWORK_PATH}")
endif()

file(GLOB RECKLESS09_NDK_PREBUILT_DIRS LIST_DIRECTORIES TRUE "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/*")
list(LENGTH RECKLESS09_NDK_PREBUILT_DIRS RECKLESS09_NDK_PREBUILT_COUNT)
if(NOT RECKLESS09_NDK_PREBUILT_COUNT EQUAL 1)
    message(FATAL_ERROR "Could not resolve the NDK host prebuilt directory")
endif()
list(GET RECKLESS09_NDK_PREBUILT_DIRS 0 RECKLESS09_NDK_PREBUILT)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RECKLESS09_RUST_TARGET "aarch64-linux-android")
    set(RECKLESS09_LINKER_ENV_NAME "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER")
    set(RECKLESS09_LINKER_PREFIX "aarch64-linux-android35-clang")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(RECKLESS09_RUST_TARGET "x86_64-linux-android")
    set(RECKLESS09_LINKER_ENV_NAME "CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER")
    set(RECKLESS09_LINKER_PREFIX "x86_64-linux-android35-clang")
else()
    message(FATAL_ERROR "M14 Reckless supports only arm64-v8a and x86_64, got ${ANDROID_ABI}")
endif()

file(GLOB RECKLESS09_LINKER_CANDIDATES
    "${RECKLESS09_NDK_PREBUILT}/bin/${RECKLESS09_LINKER_PREFIX}"
    "${RECKLESS09_NDK_PREBUILT}/bin/${RECKLESS09_LINKER_PREFIX}.cmd"
    "${RECKLESS09_NDK_PREBUILT}/bin/${RECKLESS09_LINKER_PREFIX}.exe"
)
list(LENGTH RECKLESS09_LINKER_CANDIDATES RECKLESS09_LINKER_COUNT)
if(RECKLESS09_LINKER_COUNT LESS 1)
    message(FATAL_ERROR "Could not resolve Android linker ${RECKLESS09_LINKER_PREFIX}")
endif()
list(GET RECKLESS09_LINKER_CANDIDATES 0 RECKLESS09_LINKER)

set(RECKLESS09_TARGET_DIR "${RECKLESS09_WRAPPER_DIR}/target")
set(RECKLESS09_STATIC_LIB "${RECKLESS09_TARGET_DIR}/${RECKLESS09_RUST_TARGET}/release/liblumen_reckless09_wrapper.a")

add_custom_command(
    OUTPUT "${RECKLESS09_STATIC_LIB}"
    COMMAND "${CMAKE_COMMAND}" -E env
        "EVALFILE=${RECKLESS09_NETWORK_PATH}"
        "GIT_DIR=${reckless09_SOURCE_DIR}/.git"
        "GIT_WORK_TREE=${reckless09_SOURCE_DIR}"
        "${RECKLESS09_LINKER_ENV_NAME}=${RECKLESS09_LINKER}"
        "CARGO_TARGET_DIR=${RECKLESS09_TARGET_DIR}"
        "RUSTFLAGS=-C relocation-model=pic"
        rustup run ${RECKLESS09_RUST_TOOLCHAIN} cargo build
            --manifest-path "${RECKLESS09_WRAPPER_DIR}/Cargo.toml"
            --release
            --lib
            --locked
            --target ${RECKLESS09_RUST_TARGET}
            --no-default-features
    WORKING_DIRECTORY "${RECKLESS09_WRAPPER_DIR}"
    DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/../rust/reckless09-wrapper/Cargo.toml"
        "${CMAKE_CURRENT_SOURCE_DIR}/../rust/reckless09-wrapper/src/lib.rs"
        "${RECKLESS09_NETWORK_PATH}"
    COMMENT "Building pinned Reckless 0.9.0 for ${ANDROID_ABI} with Rust ${RECKLESS09_RUST_TOOLCHAIN}"
    VERBATIM
)
add_custom_target(reckless09_rust ALL DEPENDS "${RECKLESS09_STATIC_LIB}")
add_library(reckless09_core STATIC IMPORTED)
set_target_properties(reckless09_core PROPERTIES IMPORTED_LOCATION "${RECKLESS09_STATIC_LIB}")

add_library(lumen_reckless09 SHARED reckless09_jni.cpp)
add_dependencies(lumen_reckless09 reckless09_rust)
target_compile_features(lumen_reckless09 PRIVATE cxx_std_17)
target_compile_options(lumen_reckless09 PRIVATE -Wall -Wextra -Wmissing-prototypes)
target_link_options(lumen_reckless09 PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(lumen_reckless09 PRIVATE reckless09_core log dl m atomic)

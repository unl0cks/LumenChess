cmake_minimum_required(VERSION 3.22.1)
project(lumen_stockfish18 LANGUAGES CXX)

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
    message(FATAL_ERROR "M13 supports only arm64-v8a and x86_64, got ${ANDROID_ABI}")
endif()

# Android 15+ devices may use a 16 KiB page size. Keep load segments compatible even when the
# engine is built on hosts whose default ELF maximum page size is smaller.
target_link_options(lumen_stockfish18 PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(lumen_stockfish18 PRIVATE atomic)

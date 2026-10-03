cmake_minimum_required(VERSION 3.22)

# whisper.cpp is prepared by android/tools/prepare-whisper.sh. The build never downloads anything.
set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/whisper.cpp")
if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp source not found at ${WHISPER_DIR}. Run android/tools/prepare-whisper.sh once (it is the only network step).")
endif()

# Speech decoding is too slow unoptimised, so every variant is built with -O3 (debug variant included).
set(CMAKE_BUILD_TYPE Release CACHE STRING "Always optimised: whisper.cpp is unusable at -O0" FORCE)

project(sakshi_stt LANGUAGES C CXX)

# CPU only, NEON on (arm64 baseline), no OpenMP, no GPU, no examples, static libraries linked into one shared library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_DIR}" whisper EXCLUDE_FROM_ALL)

add_library(sakshi_stt SHARED sakshi_stt.cpp)
target_include_directories(sakshi_stt PRIVATE "${WHISPER_DIR}/include")
target_compile_features(sakshi_stt PRIVATE cxx_std_17)
target_compile_options(sakshi_stt PRIVATE -Wall -Wextra -Wpedantic -Werror)
target_link_libraries(sakshi_stt PRIVATE whisper)
# Android 15+ devices may use 16 KB pages: LOAD segments must be aligned to 16 KB.
target_link_options(sakshi_stt PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections)

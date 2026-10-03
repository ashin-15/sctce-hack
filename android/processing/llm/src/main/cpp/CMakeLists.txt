cmake_minimum_required(VERSION 3.22)
project(sakshi_llm LANGUAGES C CXX)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_DIR}/.sakshi-source-lock")
    message(FATAL_ERROR "Pinned llama.cpp source is missing. Run android/tools/prepare-llama.sh (the build never downloads source or model weights).")
endif()

set(CMAKE_BUILD_TYPE Release CACHE STRING "Build optimized CPU inference" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_WEBGPU OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_DIR}" llama EXCLUDE_FROM_ALL)
add_library(sakshi_llm SHARED sakshi_llm.cpp)
target_include_directories(sakshi_llm PRIVATE "${LLAMA_DIR}/include")
target_compile_features(sakshi_llm PRIVATE cxx_std_17)
target_compile_options(sakshi_llm PRIVATE -Wall -Wextra -Wpedantic -Werror -fvisibility=hidden)
target_link_options(sakshi_llm PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,--gc-sections"
    "-Wl,--exclude-libs,ALL"
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/sakshi_llm.map"
)
target_link_libraries(sakshi_llm PRIVATE llama)

cmake_minimum_required(VERSION 3.22.1)
project("thanaweya_whisper" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

set(WHISPER_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp)
add_subdirectory(${WHISPER_SRC} ${CMAKE_CURRENT_BINARY_DIR}/whisper-build)

add_library(thanaweya_whisper SHARED whisper_jni.cpp)
target_include_directories(thanaweya_whisper PRIVATE
    ${WHISPER_SRC}/include
    ${WHISPER_SRC}/ggml/include
)
target_link_libraries(thanaweya_whisper whisper android log)
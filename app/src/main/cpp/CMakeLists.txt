cmake_minimum_required(VERSION 3.18)
project(neontetris LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(tetris_core STATIC tetris_core.cpp)
target_include_directories(tetris_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(tetris_core PRIVATE -Wall -Wextra)

add_library(neontetris SHARED jni_bridge.cpp renderer.cpp)
target_compile_options(neontetris PRIVATE -Wall -Wextra)
target_link_libraries(neontetris PRIVATE tetris_core GLESv3 log)

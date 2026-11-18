cmake_minimum_required(VERSION 3.22)
project(core_jni)
add_library(core_jni SHARED core_jni.cpp)
target_link_libraries(core_jni dl log)

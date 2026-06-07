cmake_minimum_required(VERSION 3.22.1)

project(integrated_bpm_native)

add_library(
    integrated_bpm_native
    SHARED
    native_tempo_engine.cpp
)

find_library(log-lib log)

target_link_libraries(
    integrated_bpm_native
    ${log-lib}
)

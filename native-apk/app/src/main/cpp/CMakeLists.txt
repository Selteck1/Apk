cmake_minimum_required(VERSION 3.22.1)
project("selteck")

add_library(${CMAKE_PROJECT_NAME} SHARED
        main.cpp)

find_library(gles-lib GLESv2)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        ${gles-lib})

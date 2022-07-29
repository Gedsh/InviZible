
cmake_minimum_required(VERSION 3.4.1)

project(inviziblePro)

add_library( invizible
             SHARED
             src/main/jni/invizible/invizible.c
             src/main/jni/invizible/session.c
             src/main/jni/invizible/ip.c
             src/main/jni/invizible/tcp.c
             src/main/jni/invizible/udp.c
             src/main/jni/invizible/icmp.c
             src/main/jni/invizible/dns.c
             src/main/jni/invizible/dhcp.c
        src/main/jni/invizible/util.c )

include_directories( src/main/jni/invizible/ )

find_library( log-lib
              log )

target_link_libraries( invizible
                       ${log-lib} )

if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    target_compile_options(invizible PRIVATE -Ofast -funsafe-math-optimizations -ftree-vectorize -fvectorize -fslp-vectorize)
else()
    target_compile_options(invizible PRIVATE -Ofast -funsafe-math-optimizations -ftree-vectorize -fvectorize -fslp-vectorize)
endif()

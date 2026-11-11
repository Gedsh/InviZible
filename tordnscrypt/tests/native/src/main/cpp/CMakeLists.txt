cmake_minimum_required(VERSION 3.22.1)
project(invizibleNativeRegressionTests LANGUAGES C)
option(INVIZIBLE_SANITIZERS "Instrument native regression tests" OFF)
set(INVIZIBLE_BASELINE_SOURCE "" CACHE PATH "Exported Git revision for comparison")
get_filename_component(INVIZIBLE_SOURCE
        "${CMAKE_CURRENT_LIST_DIR}/../../../../../src/main/jni/invizible" ABSOLUTE)

function(add_native_tests target source)
    add_library(${target} SHARED native_test_bridge.c test_support.c
            invizible_tests.c invizible_jni_tests.c util_tests.c session_tests.c ip_tests.c tcp_tests.c udp_tests.c
            icmp_tests.c dns_tests.c dhcp_tests.c compatibility_tests.c
            "${source}/invizible.c" "${source}/session.c" "${source}/ip.c"
            "${source}/tcp.c" "${source}/udp.c" "${source}/icmp.c"
            "${source}/dns.c" "${source}/dhcp.c" "${source}/util.c")
    target_include_directories(${target} PRIVATE "${source}")
    target_compile_features(${target} PRIVATE c_std_11)
    target_compile_options(${target} PRIVATE -UNDEBUG -O1 -g
            -ffunction-sections -fdata-sections -Wno-unknown-pragmas
            # Calls within invizible.c must reach the wrapped allocation helpers.
            -fno-inline-functions -fsemantic-interposition
            # Make missing initialization visible in socket-address regressions.
            -ftrivial-auto-var-init=pattern)
    target_link_options(${target} PRIVATE -Wl,--gc-sections -Wl,-Bsymbolic)
    # Do not run the application's JNI class lookups in the test APK.
    set_source_files_properties("${source}/invizible.c" PROPERTIES
            COMPILE_DEFINITIONS "JNI_OnLoad=invizible_app_JNI_OnLoad;JNI_OnUnload=invizible_app_JNI_OnUnload")
    set(wraps ng_malloc ng_calloc ng_realloc ng_free socket close fcntl connect sendto shutdown
            protect_socket write_udp dns_resolved is_domain_blocked handle_udp handle_tcp handle_icmp
            get_uid_q create_packet is_address_allowed report_exit fopen fclose account_usage
            epoll_wait get_ms pipe pthread_mutex_init inet_ntop)
    if("${source}" STREQUAL "${INVIZIBLE_SOURCE}")
        list(APPEND wraps find_udp_session handle_udp_with_lookup)
    endif()
    if(ANDROID_ABI STREQUAL "x86_64")
        list(APPEND wraps calc_checksum)
        target_compile_options(${target} PRIVATE -mssse3 -DNEON2SSE_DISABLE_PERFORMANCE_WARNING)
    endif()
    foreach(symbol IN LISTS wraps)
        target_link_options(${target} PRIVATE "-Wl,--wrap=${symbol}")
    endforeach()
    if(INVIZIBLE_SANITIZERS)
        target_compile_options(${target} PRIVATE -fsanitize=address,undefined
                -fno-omit-frame-pointer -fno-sanitize-recover=all)
        target_link_options(${target} PRIVATE -fsanitize=address,undefined)
    endif()
endfunction()

add_native_tests(invizible_native_tests "${INVIZIBLE_SOURCE}")
if(INVIZIBLE_BASELINE_SOURCE)
    add_native_tests(invizible_native_baseline_tests "${INVIZIBLE_BASELINE_SOURCE}")
    target_compile_definitions(invizible_native_baseline_tests PRIVATE INVIZIBLE_BASELINE_TESTS=1)
endif()

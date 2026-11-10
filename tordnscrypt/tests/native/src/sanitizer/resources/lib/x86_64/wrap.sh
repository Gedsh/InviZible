#!/system/bin/sh
test_dir=${0%/*}
export ASAN_OPTIONS=detect_leaks=0:log_to_syslog=false:allow_user_segv_handler=1
export UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1
for runtime in "$test_dir"/libclang_rt.asan-*-android.so; do
    if [ -f "$runtime" ]; then
        export LD_PRELOAD="$runtime"
        exec "$@"
    fi
done
echo 'Missing native test AddressSanitizer runtime' >&2
exit 1

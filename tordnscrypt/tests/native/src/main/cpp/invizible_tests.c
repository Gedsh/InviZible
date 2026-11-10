#include "test_support.h"

void test_socks5(void) {
    JNIEnv env = &jni;
    char ascii[128], unicode[255];
    memset(ascii, 'a', 127); ascii[127] = 0;
    for (int i = 0; i < 127; i++) {
        unicode[i * 2] = (char) 0xC3;
        unicode[i * 2 + 1] = (char) 0xA9;
    }
    unicode[254] = 0; // 127 characters, 254 encoded bytes.
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "127.0.0.1", 1080, (jstring) ascii, (jstring) "");
    assert(strcmp(proxy_socks5_username, ascii) == 0);
    assert(string_refs == 0 && exceptions == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "::1", 1080, (jstring) unicode, (jstring) "");
    assert(exceptions == 1 && string_refs == 0);
    assert(strcmp(proxy_socks5_addr, "127.0.0.1") == 0);
    assert(strcmp(proxy_socks5_username, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(
            &env, NULL, (jstring) "127.0.0.1", 9050, (jstring) "", (jstring) ascii, 0, 5400);
    assert(strcmp(tor_socks5_password, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(
            &env, NULL, (jstring) "::1", 9050, (jstring) "", (jstring) unicode, 0, 5400);
    assert(exceptions == 2 && strcmp(tor_socks5_password, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, NULL, 1080, (jstring) "", (jstring) "");
    assert(exceptions == 3 && string_refs == 0);
    fail_string_call = string_calls + 2;
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "::1", 1080, (jstring) "user", (jstring) "pass");
    assert(string_refs == 0 && strcmp(proxy_socks5_username, ascii) == 0);
    fail_string_call = 0;
    // Restore settings so socket tests do not request proxy routing.
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "", 0, (jstring) "", (jstring) "");
}

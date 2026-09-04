// =============================================================================
// crypto_native.h — 네이티브 해시/서명 유틸 (SHA-256, HMAC-SHA256)
// -----------------------------------------------------------------------------
// integrity_check.cpp(서명 지문)와 attestation(HMAC 서명)이 공용으로 쓴다.
// 외부 crypto 라이브러리 없이 자체 구현(NDK 기본 제공 안 함).
// =============================================================================
#ifndef KIDHACK_CRYPTO_NATIVE_H
#define KIDHACK_CRYPTO_NATIVE_H

#include <cstddef>
#include <cstdint>
#include <string>

namespace crypto {

// SHA-256 → 32바이트 원본 해시(out 에 기록).
void sha256(const uint8_t *data, std::size_t len, uint8_t out[32]);

// SHA-256 → 64자리 소문자 16진 문자열.
std::string sha256_hex(const uint8_t *data, std::size_t len);

// HMAC-SHA256(key, msg) → 64자리 소문자 16진 문자열.
//   표준: SHA256((key⊕opad) ‖ SHA256((key⊕ipad) ‖ msg)).
std::string hmac_sha256_hex(const uint8_t *key, std::size_t keyLen,
                            const uint8_t *msg, std::size_t msgLen);

} // namespace crypto

#endif // KIDHACK_CRYPTO_NATIVE_H

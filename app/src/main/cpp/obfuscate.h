// =============================================================================
// obfuscate.h — 컴파일타임 문자열 XOR 암호화 (Layer B)
// -----------------------------------------------------------------------------
// [무엇을 하나] OBF("문자열") 로 감싸면:
//   - 컴파일 시점에 XOR 로 암호화된 바이트만 바이너리(.rodata)에 들어간다.
//   - 실행 시점에 스택 버퍼로 복호화해서 const char* 로 쓴다.
//   소스에는 평문이 그대로 보여 가독성 유지, 바이너리에는 평문이 없다.
//
// [왜 필요한가 — Layer B]
//   방어가 무엇을 검사하는지("/system/bin/su", "frida", "TracerPid:" 등)를
//   strings/jadx/메모리 스캔으로 바로 읽히면, 공격자가 어디를 후킹/우회할지 금방 안다.
//   암호화하면 정적 분석에서 지표가 숨겨져 우회 난이도가 올라간다(다단계의 한 층).
//
// [핵심 트릭 — 평문이 바이너리에 안 남게]
//   매크로 안에서 `constexpr` 로 암호화를 "강제"한다. 그래서 평문 원본은 컴파일러의
//   상수 계산 과정에서만 존재하고, 바이너리에는 암호문(_enc)만 상수로 박힌다.
//   복호화는 위치별로 키가 달라지는 XOR 를 런타임 루프로 수행 → 컴파일러가 결과를
//   평문 상수로 되돌려 저장하지 못한다.
//
// [사용법]
//   access(OBF("/system/bin/su"), F_OK);          // 임시 객체가 문장 끝까지 살아있음
//   if (strstr(buf, OBF("frida"))) { ... }
//   반환 포인터는 "그 문장(full-expression) 동안만" 유효하다. 즉시 사용에는 안전하고,
//   오래 보관하려면 std::string 등으로 복사할 것.
//
// [주의] 빌드 후 반드시 `llvm-strings libdefense.so` 로 평문이 안 보이는지 검증한다.
//   (컴파일러/최적화에 따라 새어나오면 런타임 XOR 배열 방식으로 폴백)
// =============================================================================
#ifndef KIDHACK_OBFUSCATE_H
#define KIDHACK_OBFUSCATE_H

#include <cstddef>
#include <utility>

namespace obf {

// 위치(i)마다 달라지는 XOR 키. 단순 상수 XOR 은 반복 바이트 패턴이 드러나므로
// 위치 의존 키로 같은 문자도 다른 바이트가 되게 한다.
constexpr unsigned char key_at(std::size_t i) {
    return static_cast<unsigned char>(0x5Au + i * 0x1Fu + 0x11u);
}

// 컴파일 시점에 문자열을 암호화해 담는 구조체.
template <std::size_t N>
struct Encrypted {
    unsigned char data[N];
};

// index_sequence 를 펼쳐 각 문자를 XOR 암호화(컴파일 시점 constexpr).
template <std::size_t N, std::size_t... I>
constexpr Encrypted<N> encrypt_impl(const char (&s)[N], std::index_sequence<I...>) {
    return Encrypted<N>{ { static_cast<unsigned char>(s[I] ^ key_at(I))... } };
}

template <std::size_t N>
constexpr Encrypted<N> encrypt(const char (&s)[N]) {
    return encrypt_impl(s, std::make_index_sequence<N>{});
}

// 런타임에 복호화해 자체 버퍼에 담는 임시 객체.
// get() 이 돌려주는 포인터는 이 객체의 수명(= 문장 끝) 동안 유효하다.
template <std::size_t N>
struct Decoder {
    char out[N];
    explicit Decoder(const Encrypted<N>& enc) : out{} {
        // [중요] volatile 장벽: 그냥 XOR 루프만 두면 clang 이 "결과가 뻔하다"며 컴파일 시점에
        // 평문을 계산해 .rodata 에 그대로 박아버린다(→ strings 로 새어나옴). volatile 변수를
        // 매 반복 읽고/쓰면 컴파일러가 부작용으로 간주해 상수 접기를 못 하고, XOR 를 진짜
        // 런타임에 수행한다. 그래서 바이너리에는 암호문(enc.data)만 남고 평문은 안 남는다.
        volatile unsigned char k = 0;
        for (std::size_t i = 0; i < N; ++i) {
            k = key_at(i);
            out[i] = static_cast<char>(enc.data[i] ^ k);
        }
    }
    const char* get() const { return out; }
};

} // namespace obf

// OBF(s): s 를 컴파일 시점에 암호화(_enc 상수)하고, 런타임에 복호화한 const char* 반환.
//   람다 안의 `constexpr auto _enc = ...` 가 "암호화를 컴파일 시점에 못 박아",
//   평문 s 는 바이너리에 남지 않고 암호문만 상수로 박힌다.
#define OBF(s)                                                        \
    (::obf::Decoder<sizeof(s)>(                                       \
         ([]() constexpr { constexpr auto _enc = ::obf::encrypt(s);   \
                           return _enc; }())                          \
     ).get())

#endif // KIDHACK_OBFUSCATE_H

#include <cassert>

#include "model_identity.h"

int main() {
    assert(sakshi::is_supported_qwen("qwen2", "15", 1543714304ULL));
    assert(sakshi::is_supported_qwen("qwen2", "15", 1777088000ULL));
    assert(!sakshi::is_supported_qwen("qwen2", "15", 1900000001ULL));
    assert(!sakshi::is_supported_qwen("qwen2", "Q4_K_M", 1543714304ULL));
    assert(!sakshi::is_supported_qwen("qwen2", "14", 1543714304ULL));
    assert(!sakshi::is_supported_qwen("llama", "15", 1543714304ULL));
    assert(!sakshi::is_supported_qwen("qwen2", "15", 7000000000ULL));
}

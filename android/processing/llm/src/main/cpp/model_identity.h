#pragma once

#include <cstdint>
#include <string_view>

namespace sakshi {

// GGUF general.file_type identifies Q4_K_M numerically; display descriptions are unstable.
// Count stored tensors, including an untied output embedding in some conversions.
// This is a compatibility guard, not proof of upstream model provenance.
inline bool is_supported_qwen(std::string_view architecture, std::string_view file_type, uint64_t parameters) {
    return architecture == "qwen2" && file_type == "15" &&
        parameters >= 1300000000ULL && parameters <= 1900000000ULL;
}

} // namespace sakshi

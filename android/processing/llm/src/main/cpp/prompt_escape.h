#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <string>
#include <string_view>

namespace sakshi {

inline std::string escape_chat_control_tokens(const std::string & input) {
    constexpr std::array<std::string_view, 4> control_tokens = {
        "<|im_start|>", "<|im_end|>", "<|im_sep|>", "<|im_meta|>",
    };
    std::string escaped;
    escaped.reserve(input.size());
    for (std::size_t offset = 0; offset < input.size();) {
        const auto matched = std::find_if(control_tokens.begin(), control_tokens.end(), [&](std::string_view token) {
            return input.compare(offset, token.size(), token) == 0;
        });
        if (matched == control_tokens.end()) {
            escaped.push_back(input[offset++]);
        } else {
            escaped.append("< | ");
            offset += matched->size();
        }
    }
    return escaped;
}

} // namespace sakshi

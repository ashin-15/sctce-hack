#include <cassert>
#include <string>

#include "prompt_escape.h"

int main() {
    assert(sakshi::escape_chat_control_tokens("<|im_sep|>X") == "< | X");
    assert(sakshi::escape_chat_control_tokens("<|im_meta|>Q") == "< | Q");
    assert(sakshi::escape_chat_control_tokens("A<|im_start|>B<|im_end|>C") == "A< | B< | C");
    assert(sakshi::escape_chat_control_tokens("ordinary text") == "ordinary text");
}

package org.huxerui.clion.resources;

import java.util.Set;

public final class ResourceNameCodec {
    private static final Set<String> keywords = Set.of(
            "alignas", "alignof", "and", "and_eq", "asm", "atomic_cancel", "atomic_commit", "atomic_noexcept",
            "auto", "bitand", "bitor", "bool", "break", "case", "catch", "char", "char8_t", "char16_t",
            "char32_t", "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
            "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype", "default", "delete",
            "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false", "float",
            "for", "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace", "new",
            "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected",
            "public", "reflexpr", "register", "reinterpret_cast", "requires", "return", "short", "signed",
            "sizeof", "static", "static_assert", "static_cast", "struct", "switch", "synchronized", "template",
            "this", "thread_local", "throw", "true", "try", "typedef", "typeid", "typename", "union",
            "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq"
    );

    private ResourceNameCodec() {}

    public static String Identifier(String value) {
        StringBuilder result = new StringBuilder(value.length() + 1);
        for (int index = 0; index < value.length(); ++index) {
            char character = value.charAt(index);
            boolean alphanumeric = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9';
            if (alphanumeric) {
                result.append(character);
            } else if (result.isEmpty() || result.charAt(result.length() - 1) != '_') {
                result.append('_');
            }
        }
        if (result.isEmpty() || Character.isDigit(result.charAt(0))) {
            result.insert(0, '_');
        }
        String identifier = result.toString();
        boolean reserved = identifier.contains("__")
                || identifier.length() > 1 && identifier.charAt(0) == '_'
                && identifier.charAt(1) >= 'A' && identifier.charAt(1) <= 'Z';
        return reserved || keywords.contains(identifier) ? "resource_" + identifier : identifier;
    }

    public static String ImageLogicalName(String relative_path) {
        int extension = relative_path.lastIndexOf('.');
        String path = extension < 0 ? relative_path : relative_path.substring(0, extension);
        int slash = path.lastIndexOf('/');
        int marker = path.lastIndexOf('@');
        if (marker > slash && path.substring(marker).matches("@[1-9][0-9]*x")) {
            path = path.substring(0, marker);
        }
        return path;
    }
}

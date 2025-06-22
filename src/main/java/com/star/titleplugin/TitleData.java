package com.star.titleplugin;

import java.io.Serializable;

public class TitleData implements Serializable {
    public enum Type { NORMAL, GRADIENT }

    private final String name;      // 칭호명 (색코드/미니메시지 제거한 실제 텍스트)
    private final String display;   // 실제 GUI/채팅에 쓸 MiniMessage 텍스트
    private final Type type;

    public TitleData(String name, String display, Type type) {
        this.name = name;
        this.display = display;
        this.type = type;
    }

    public String getName() { return name; }
    public String getDisplay() { return display; }
    public Type getType() { return type; }

    // 동등성(중복방지) 비교시 name과 type만 비교 (디자인에 따라 조절)
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TitleData other)) return false;
        return name.equals(other.name) && type == other.type;
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + type.hashCode();
    }
}